package com.example.myagentclient.service;

import com.example.myagentclient.client.MailpitClient;
import com.example.myagentclient.client.dto.MailpitAddress;
import com.example.myagentclient.client.dto.MailpitMessage;
import com.example.myagentclient.client.dto.MailpitMessageSummary;
import com.example.myagentclient.config.InboxProperties;
import com.example.myagentclient.model.IncomingEmail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 定時輪詢 Mailpit 收件匣，將每封未讀郵件轉換成 {@link IncomingEmail}，
 * 並轉發給設定的 {@link EmailHandler} 處理。
 * <p>
 * 已讀／未讀狀態存在 Mailpit 伺服器上：取得郵件時會自動標為已讀，藉此避免重複處理。
 * 若 handler 回報處理失敗，則將郵件標回未讀，讓下次輪詢重新處理。
 */
@Service
public class InboxMonitor {

    private static final Logger log = LoggerFactory.getLogger(InboxMonitor.class);

    private final MailpitClient mailpit;
    private final EmailHandler handler;
    private final InboxProperties props;

    public InboxMonitor(MailpitClient mailpit, EmailHandler handler, InboxProperties props) {
        this.mailpit = mailpit;
        this.handler = handler;
        this.props = props;
    }

    @Scheduled(fixedDelayString = "${support-agent.inbox.poll-interval:10000}")
    public void poll() {
        try {
            List<MailpitMessageSummary> unread = mailpit.listUnread(props.batchSize());
            if (unread.isEmpty()) {
                log.debug("沒有新郵件");
                return;
            }
            log.info("發現 {} 封新郵件", unread.size());
            for (MailpitMessageSummary summary : unread) {
                processOne(summary.id());
            }
        } catch (Exception e) {
            // Mailpit 可能正在啟動或暫時無法連線，記錄警告並等待下次輪詢重試，不中斷排程器。
            log.warn("收件匣輪詢失敗：{}", e.getMessage(), e);
        }
    }

    private void processOne(String id) {
        try {
            // 取得完整郵件內容，同時 Mailpit 會自動將其標為已讀。
            MailpitMessage message = mailpit.getMessage(id);
            IncomingEmail email = toIncomingEmail(message);
            boolean handled = handler.handle(email);
            if (!handled) {
                // 處理失敗，標回未讀，讓下次輪詢重新處理。
                mailpit.setRead(id, false);
            }
        } catch (Exception e) {
            log.error("郵件 {} 處理失敗，標回未讀以便重試", id, e);
            try {
                mailpit.setRead(id, false);
            } catch (Exception reset) {
                log.warn("無法將郵件 {} 標回未讀：{}", id, reset.getMessage());
            }
        }
    }

    private IncomingEmail toIncomingEmail(MailpitMessage message) {
        String from = message.from() != null ? message.from().address() : "(未知)";
        List<String> to = message.to().stream()
                .map(MailpitAddress::address)
                .toList();
        String subject = message.subject() != null ? message.subject() : "";
        String body = bestBody(message);
        Instant receivedAt = parseDate(message.date());
        return new IncomingEmail(message.messageId(), from, to, subject, body, receivedAt);
    }

    /** 優先使用純文字內容；若只有 HTML 則退而使用 HTML。 */
    private String bestBody(MailpitMessage message) {
        if (message.text() != null && !message.text().isBlank()) {
            return message.text().strip();
        }
        return message.html() != null ? message.html().strip() : "";
    }

    private Instant parseDate(String date) {
        if (date == null || date.isBlank()) {
            return Instant.now();
        }
        try {
            return OffsetDateTime.parse(date).toInstant();
        } catch (Exception e) {
            try {
                return Instant.parse(date);
            } catch (Exception ex) {
                return Instant.now();
            }
        }
    }
}
