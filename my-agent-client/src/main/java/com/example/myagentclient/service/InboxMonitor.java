package com.example.myagentclient.service;

import com.example.myagentclient.client.MailpitInboxClient;
import com.example.myagentclient.client.dto.MailpitAddress;
import com.example.myagentclient.client.dto.MailpitMessage;
import com.example.myagentclient.client.dto.MailpitMessageSummary;
import com.example.myagentclient.config.InboxProperties;
import com.example.myagentclient.model.IncomingEmail;
import com.example.myagentclient.service.handler.EmailHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 定時輪詢 Mailpit 收件匣，將未讀郵件轉為 {@link IncomingEmail} 後交給 {@link EmailHandler} 處理。
 * 處理成功保持已讀；失敗則標回未讀，下次輪詢自動重試。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InboxMonitor {

    private final MailpitInboxClient mailpitInboxClient; // 負責打 Mailpit 收件匣 API
    private final EmailHandler handler; // 負責處理每封信的邏輯（AI 回覆等）
    private final InboxProperties inboxProperties; // 讀取 application.properties 的設定值

    /**
     * 輪詢未讀郵件並逐一處理；fixedDelay 確保上一次執行完畢後才開始倒計時，不會重疊。 每 10 秒執行一次
     */
    @Scheduled(fixedDelayString = "${my-agent-client.inbox.poll-interval:10000}")
    public void poll() {
        try {
            // 1. 向 Mailpit 查詢未讀郵件（上限 batchSize，避免單次處理量過大）
            List<MailpitMessageSummary> unread = mailpitInboxClient.listUnread(inboxProperties.batchSize());

            // 2. 無新郵件則提前返回，跳過後續處理
            if (unread.isEmpty()) {
                log.debug("沒有新郵件");
                return;
            }

            log.info("發現 {} 封新郵件", unread.size());

            // 3. 逐一處理，每封獨立 try-catch，一封失敗不影響其他封
            for (MailpitMessageSummary summary : unread) {
                processOne(summary.id());
            }
        } catch (Exception e) {
            // Mailpit 可能正在啟動或暫時無法連線；吞掉例外讓排程器繼續，下次輪詢自動重試。
            log.warn("收件匣輪詢失敗：{}", e.getMessage(), e);
        }
    }

    // ─────────────────────────── 輔助方法 ───────────────────────────

    /**
     * 取得完整郵件並交給 handler；失敗或例外時標回未讀，讓下次輪詢重試。
     */
    private void processOne(String id) {
        try {
            // 1. 取得完整郵件內容（同時自動標為已讀，避免下次輪詢重複取出）
            MailpitMessage message = mailpitInboxClient.getMessage(id);

            // 2. 將 Mailpit 格式轉換成應用程式內部格式，供 handler 使用
            IncomingEmail email = toIncomingEmail(message);

            // 3. 交給 handler 處理；true = 成功保持已讀，false = 失敗需標回未讀重試
            boolean handled = handler.handle(email);

            // 4. handler 回報失敗 → 標回未讀，讓下次輪詢重新嘗試
            if (!handled) {
                mailpitInboxClient.setRead(id, false); // false → 標回未讀，觸發下次重試
            }
        } catch (Exception e) {
            log.error("郵件 {} 處理失敗，標回未讀以便重試", id, e);
            try {
                mailpitInboxClient.setRead(id, false); // false → 補救標回未讀，讓輪詢重試
            } catch (Exception reset) {
                log.warn("無法將郵件 {} 標回未讀：{}", id, reset.getMessage()); // 連重設也失敗，只記錄不拋出，避免中斷排程器
            }
        }
    }

    /**
     * 將 {@link MailpitMessage} 轉為內部 {@link IncomingEmail}；寄件人或主旨為 null 時補上預設值。
     */
    private IncomingEmail toIncomingEmail(MailpitMessage message) {
        // 1. 寄件人：from 可能為 null（Mailpit 不保證有寄件人），補上預設值避免 NPE
        String from = message.from() != null ? message.from().address() : "(未知)";

        // 2. 收件人：MailpitAddress → String，只取 address 欄位，捨棄 display name
        List<String> to = message.to().stream()
                .map(MailpitAddress::address)
                .toList();

        // 3. 主旨：subject 可能為 null，補空字串讓下游不需判斷 null
        String subject = message.subject() != null ? message.subject() : "";

        // 4. 內文：優先純文字，降級 HTML，詳見 bestBody()
        String body = bestBody(message);

        // 5. 接收時間：date 為 Mailpit 字串格式，交給 parseDate() 容錯解析
        Instant receivedAt = parseDate(message.date());

        // 6. 組裝並回傳，messageId 作為去重識別碼
        return new IncomingEmail(message.messageId(), from, to, subject, body, receivedAt);
    }

    /**
     * 優先取純文字內文，降級 HTML，兩者皆無則回傳空字串。
     */
    private String bestBody(MailpitMessage message) {
        // 1. 優先純文字：無 HTML 標籤干擾，AI 處理更乾淨
        if (message.text() != null && !message.text().isBlank()) {
            return message.text().strip();
        }
        // 2. 降級 HTML：純文字不存在時的備援；兩者皆無回傳空字串，讓 handler 自行決定如何處理
        return message.html() != null ? message.html().strip() : "";
    }

    /**
     * 將日期字串依序嘗試 {@link OffsetDateTime}、{@link Instant} 格式解析；兩者皆失敗則回傳 {@code Instant.now()}。
     */
    private Instant parseDate(String date) {

        // 1. Mailpit 未回傳時間時，用當下時間兜底，避免因缺少日期而中斷處理
        if (date == null || date.isBlank()) {
            return Instant.now();
        }
        try {
            // 2. 優先嘗試 OffsetDateTime（含時區偏移，例：2024-01-01T12:00:00+08:00），Mailpit 最常見格式
            return OffsetDateTime.parse(date).toInstant();
        } catch (Exception e) {
            try {
                // 3. 降級嘗試純 UTC Instant（例：2024-01-01T12:00:00Z）
                return Instant.parse(date);
            } catch (Exception ex) {
                return Instant.now(); // 兩種格式皆失敗，用當下時間兜底，不因日期解析錯誤中斷整封郵件
            }
        }
    }
}
