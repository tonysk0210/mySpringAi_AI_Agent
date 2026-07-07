package com.example.myagentclient.service;

import com.example.myagentclient.config.InboxProperties;
import com.example.myagentclient.model.AgentResponse;
import com.example.myagentclient.model.IncomingEmail;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * 透過 SMTP 將 Agent 草擬的回覆寄回給客戶，並設定 {@code In-Reply-To}/{@code References} 標頭，
 * 讓郵件客戶端將回覆顯示在同一對話串而非全新郵件。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SupportMailSender {

    /**
     * 引用區塊的時間格式；固定 {@code Locale.ENGLISH} 確保月份縮寫（Jan/Feb...）不受系統語言影響，
     * 例如："On Wed, 11 Jun 2026 at 14:03, sender@example.com wrote:"
     */
    private static final DateTimeFormatter QUOTE_DATE =
            DateTimeFormatter.ofPattern("EEE, d MMM yyyy 'at' HH:mm", Locale.ENGLISH)
                    .withZone(ZoneId.systemDefault());

    private final JavaMailSender javaMailSender; // 由 spring-boot-starter-mail 依 spring.mail.* 自動配置，無需手動 @Bean
    private final InboxProperties inboxProperties; // 取 address 作為回覆郵件的 From 地址

    /**
     * 將 Agent 的回覆寄給原始寄件人；收件人無效或回覆內容為空時回傳 {@code false}。
     */
    public boolean sendReply(IncomingEmail original, AgentResponse response) throws Exception {
        // 1. 回覆目標是原始郵件的寄件人（即發來客服信的客戶）
        String recipient = original.from();

        // 2. "(未知)" 是 InboxMonitor 在 from 為 null 時補上的哨兵值，遇到時無法回覆只能略過
        if (recipient == null || recipient.isBlank() || "(unknown)".equals(recipient)) {
            log.warn("郵件「{}」沒有有效的寄件人地址，略過回覆", original.subject());
            return false;
        }
        // 3. 正常情況 LLM 必定產生回覆；為空表示 Agent 處理異常，略過以免寄出空信
        if (response.replyBody() == null || response.replyBody().isBlank()) {
            log.warn("Agent 未產生來自 {} 的郵件回覆內容，略過回覆", recipient);
            return false;
        }

        // 4. 使用 MimeMessage（而非 SimpleMailMessage）因需要手動設定 In-Reply-To/References 標頭
        MimeMessage mime = javaMailSender.createMimeMessage();

        // 5. false = 非 multipart（無附件）；UTF-8 確保中文等多位元組字元正確編碼
        MimeMessageHelper helper = new MimeMessageHelper(mime, false, "UTF-8");

        // 6. From 為支援信箱；Body 由 quoteOriginal() 將 Agent 回覆與原始郵件引用組合
        helper.setFrom(inboxProperties.address());
        helper.setTo(recipient);
        helper.setSubject(replySubject(original, response));
        helper.setText(quoteOriginal(original, response.replyBody()));

        // 7. 設定對話串接標頭；messageId 可能不含 <>，統一補上符合 RFC 2822 格式:<abc123@mail.com>
        String messageId = original.messageId();
        if (messageId != null && !messageId.isBlank()) {
            String ref = messageId.startsWith("<") ? messageId : "<" + messageId + ">";
            mime.setHeader("In-Reply-To", ref); // 指向直接回覆的那封信
            mime.setHeader("References", ref);  // 完整對話歷史鏈，讓客戶端正確歸入同一串
        }

        // 8. 寄送
        javaMailSender.send(mime);
        log.info("已回覆給 {}（主旨：\"{}\"）", recipient, mime.getSubject());
        return true; // 回覆已成功寄出
    }

    /**
     * 產生回覆主旨（"Re: 原主旨"）；已有 "Re:" 前綴則不重複添加。
     */
    private String replySubject(IncomingEmail original, AgentResponse response) {
        // 1. 優先用原始主旨；若為空則降級用 Agent 建議的主旨，確保回覆信有合理主旨可顯示
        String base = original.subject() != null && !original.subject().isBlank()
                ? original.subject()
                : (response.replySubject() != null ? response.replySubject() : "");
        // 2. regionMatches(true, ...) 大小寫不敏感，"RE:"/"re:" 也不會重複添加前綴
        return base.regionMatches(true, 0, "Re:", 0, 3) ? base : "Re: " + base;
    }

    /**
     * 將 Agent 回覆與原始郵件引用（{@code > ...} 格式）組合成完整的回覆內文。
     */
    private String quoteOriginal(IncomingEmail original, String replyBody) {

        // 1. 取得原始郵件的內容 body 為 null 時補空字串，避免後續 stream 操作出現 NPE
        String body = original.body() != null ? original.body() : "";

        // 2. 每行加上 "> " 前綴（標準郵件引用格式）；空白內文時跳過，避免產生無意義的空引用區塊
        //    stripTrailing() 去除尾端空白行 → lines() 拆成每行 → map 加前綴 → reduce 重新接回單一字串
        String quoted = body.isBlank()
                ? ""
                : body.stripTrailing().lines().map(line -> "> " + line).reduce((a, b) -> a + "\n" + b).orElse("");
        /*
          原始內文：              加上 "> " 後：
            "我的訂單在哪裡？"     "> 我的訂單在哪裡？"
            "已等了三天了"         "> 已等了三天了"
        */

        // 3. 組合最終內文：Agent 回覆在上，「於 <時間>，<寄件人> 寫道：」居中，引用區塊在下
        return """
                %s

                於 %s，%s 寫道：
                %s""".formatted(
                replyBody.stripTrailing(),
                QUOTE_DATE.format(original.receivedAt()),
                original.from(),
                quoted);
        /*
          模板結構                              對應參數
          ─────────────────────────────────────────────────────
          %s                               ← replyBody（Agent 回覆，已去除尾端空白）
          （空行）
          於 %s，%s 寫道：                  ← QUOTE_DATE 格式化時間、original.from()
          %s                               ← quoted（每行加了 "> " 前綴的原始內文）
          ─────────────────────────────────────────────────────
          實際輸出範例：

          您好，您的訂單已於今日出貨，預計明天送達。

          於 Wed, 11 Jun 2026 at 14:03，customer@example.com 寫道：
          > 我的訂單在哪裡？
          > 已等了三天了
        */
    }
}
