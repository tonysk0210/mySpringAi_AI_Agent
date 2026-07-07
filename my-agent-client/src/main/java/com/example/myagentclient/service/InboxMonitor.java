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
 * 這是整個 Agent 的核心排程服務，負責定時去 Mailpit 撈新信，並把每封信交給 EmailHandler 處理。
 * <p>
 * 定時輪詢 Mailpit 收件匣，將每封未讀郵件轉換成 {@link IncomingEmail}，
 * 並轉發給設定的 {@link EmailHandler} 處理。
 * <p>
 * 已讀／未讀狀態存在 Mailpit 伺服器上：取得郵件時會自動標為已讀，藉此避免重複處理。
 * 若 handler 回報處理失敗，則將郵件標回未讀，讓下次輪詢重新處理。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InboxMonitor {

    private final MailpitInboxClient mailpitInboxClient; // 負責打 Mailpit 收件匣 API
    private final EmailHandler handler; // 負責處理每封信的邏輯（AI 回覆等）
    private final InboxProperties props; // 讀取 application.properties 的設定值

    /**
     * 定時輪詢收件匣，撈取未讀郵件並逐一處理。
     * 每次執行完畢後等待 {@code poll-interval} 毫秒再執行下一次。
     * 每 10 秒執行一次（從 application.properties 讀取，預設 10000ms）
     * fixedDelay = 上一次執行完成後才開始計時，不會重疊執行
     */
    @Scheduled(fixedDelayString = "${my-agent-client.inbox.poll-interval:10000}")
    public void poll() {
        try {
            // 1. 取得未讀郵件清單
            List<MailpitMessageSummary> unread = mailpitInboxClient.listUnread(props.batchSize());

            // 2. 檢查是否有新郵件
            if (unread.isEmpty()) {
                log.debug("沒有新郵件");
                return;
            }

            log.info("發現 {} 封新郵件", unread.size());

            // 3. 逐一處理每封郵件
            for (MailpitMessageSummary summary : unread) {
                processOne(summary.id());
            }
        } catch (Exception e) {
            // Mailpit 可能正在啟動或暫時無法連線，記錄警告並等待下次輪詢重試，不中斷排程器。
            log.warn("收件匣輪詢失敗：{}", e.getMessage(), e);
        }
    }

    // 輔助方法

    /**
     * 處理單封郵件：取得完整內容、交給 handler 處理。
     * 若處理失敗或發生例外，將郵件標回未讀以便下次輪詢重試。
     *
     * @param id Mailpit 郵件 ID
     */
    private void processOne(String id) {
        try {
            // 1. 取得完整郵件內容（同時自動標為已讀）
            MailpitMessage message = mailpitInboxClient.getMessage(id);

            // 2. 將 MailpitMessage 轉換成 IncomingEmail
            IncomingEmail email = toIncomingEmail(message);

            // 3. 交給 handler 處理
            boolean handled = handler.handle(email); // true  → 處理成功，保持已讀 ✅

            // 4. 根據 handler 的回應決定是否標回未讀
            if (!handled) {
                // 處理失敗，標回未讀，讓下次輪詢重新處理。
                mailpitInboxClient.setRead(id, false); // false → 處理失敗，標回未讀 🔄
            }
        } catch (Exception e) {
            log.error("郵件 {} 處理失敗，標回未讀以便重試", id, e);
            try {
                mailpitInboxClient.setRead(id, false); // false → 處理失敗，標回未讀 🔄
            } catch (Exception reset) {
                log.warn("無法將郵件 {} 標回未讀：{}", id, reset.getMessage()); // 連標回未讀也失敗，記錄警告但不中斷
            }
        }
    }

    /**
     * 將 Mailpit 的 {@link MailpitMessage} 轉換成應用程式內部使用的 {@link IncomingEmail}。
     * 若寄件人或主旨為 null，則補上預設值。
     *
     * @param message Mailpit 回傳的完整郵件物件
     * @return 轉換後的 {@link IncomingEmail}
     */
    private IncomingEmail toIncomingEmail(MailpitMessage message) {
        // 1. 取得寄件人地址
        String from = message.from() != null ? message.from().address() : "(未知)";

        // 2. 取得收件人地址
        List<String> to = message.to().stream()
                .map(MailpitAddress::address)
                .toList();
        // 3. 取得郵件主旨
        String subject = message.subject() != null ? message.subject() : "";
        // 4. 取得郵件最佳內文
        String body = bestBody(message);
        // 5. 取得郵件接收時間
        Instant receivedAt = parseDate(message.date());
        // 6. 建立 IncomingEmail 物件
        return new IncomingEmail(message.messageId(), from, to, subject, body, receivedAt);
    }

    /**
     * 取得郵件的最佳內文：優先使用純文字，若純文字為空則改用 HTML，兩者皆無則回傳空字串。
     *
     * @param message Mailpit 郵件物件
     * @return 郵件內文字串
     */
    private String bestBody(MailpitMessage message) {
        // 1. 檢查純文字內文
        if (message.text() != null && !message.text().isBlank()) {
            return message.text().strip();
        }
        // 2. 檢查 HTML 內文
        return message.html() != null ? message.html().strip() : "";
    }

    /**
     * 將 Mailpit 回傳的日期字串解析成 {@link Instant}。
     * 依序嘗試 {@link OffsetDateTime} 和 {@link Instant} 格式，
     * 若兩者皆失敗則回傳當下時間 {@code Instant.now()}。
     *
     * @param date Mailpit 回傳的日期字串
     * @return 解析後的 {@link Instant}
     */
    private Instant parseDate(String date) {

        // 1. 檢查日期字串是否為空
        if (date == null || date.isBlank()) {
            return Instant.now(); // 當下時間作為預設值
        }
        try {
            // 2. 將日期字串解析成 OffsetDateTime → 標準格式（含時區）
            return OffsetDateTime.parse(date).toInstant();
        } catch (Exception e) {
            try {
                // 3. 將日期字串解析成 Instant → ISO-8601 格式
                return Instant.parse(date);
            } catch (Exception ex) {
                return Instant.now(); // 解析失敗，返回當下時間作為預設值
            }
        }
    }
}
