package com.example.myagentclient.service;

import com.example.myagentclient.config.InboxProperties;
import com.example.myagentclient.model.AgentResponse;
import com.example.myagentclient.model.IncomingEmail;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * 透過 SMTP（與監控收件匣相同的 Mailpit 實例）將 Agent 草擬的回覆寄回給客戶。
 * 回覆透過 {@code In-Reply-To}/{@code References} 標頭串接在原始郵件上，
 * 讓收件者的郵件客戶端將其顯示為同一串對話，而非全新郵件。
 * 這是負責寄送回覆郵件的服務，把 Agent 產生的回覆透過 SMTP 寄給客戶，並讓回覆正確串接在原始對話串上。
 */
@Slf4j
@Service
public class SupportMailSender {

    /**
     * 引用回覆的時間格式，例如："On Wed, 11 Jun 2026 at 14:03, ... wrote:"
     */
    private static final DateTimeFormatter QUOTE_DATE =
            DateTimeFormatter.ofPattern("EEE, d MMM yyyy 'at' HH:mm", Locale.ENGLISH)
                    .withZone(ZoneId.systemDefault());

    /*
      pom.xml 加了 spring-boot-starter-mail
        │
        ▼
      Spring Boot 讀取 application.properties
        spring.mail.host=localhost
        spring.mail.port=1025
            │
            ▼
      自動建立 JavaMailSender Bean
            │
            ▼
      SupportMailSender 建構子注入
    */
    private final JavaMailSender mailSender; // 它是由 spring-boot-starter-mail 自動配置的
    private final InboxProperties inbox;

    public SupportMailSender(JavaMailSender mailSender, InboxProperties inbox) {
        this.mailSender = mailSender;
        this.inbox = inbox;
    }

    /**
     * 將 Agent 的回覆郵件寄給原始郵件的寄件人。
     *
     * @return {@code true} 表示回覆已成功寄出；
     * {@code false} 表示沒有有效的收件人地址或回覆內容為空。
     */
    public boolean sendReply(IncomingEmail original, AgentResponse response) throws Exception {
        // 1. 取得收件人地址
        String recipient = original.from();

        // 2. 驗證收件人地址是否有效
        if (recipient == null || recipient.isBlank() || "(unknown)".equals(recipient)) {
            log.warn("郵件「{}」沒有有效的寄件人地址，略過回覆", original.subject());
            return false;
        }
        // 3. 驗證回覆內容是否為空
        if (response.replyBody() == null || response.replyBody().isBlank()) {
            log.warn("Agent 未產生來自 {} 的郵件回覆內容，略過回覆", recipient);
            return false;
        }

        // 4. 建立 MimeMessage（MIME 格式郵件）
        MimeMessage mime = mailSender.createMimeMessage();
        // 5. 建立 MimeMessageHelper 以便於設定郵件標頭和內容
        MimeMessageHelper helper = new MimeMessageHelper(mime, false, "UTF-8");
        // 6. 設定 From / To / Subject / Body
        helper.setFrom(inbox.address());
        helper.setTo(recipient);
        helper.setSubject(replySubject(original, response));
        helper.setText(quoteOriginal(original, response.replyBody()));

        // 若知道原始郵件的 Message-ID，將回覆串接在原始郵件上。
        String messageId = original.messageId();

        // 7. 設定 In-Reply-To / References 標頭（串接對話）
        if (messageId != null && !messageId.isBlank()) {
            String ref = messageId.startsWith("<") ? messageId : "<" + messageId + ">"; // messageId 周圍加上 < >
            mime.setHeader("In-Reply-To", ref); // 設定 In-Reply-To 標頭 <abc123@mail.com> 指向直接回覆的那封信
            mime.setHeader("References", ref); // 設定 References 標頭 <abc123@mail.com> 整串對話的完整歷史鏈
        }

        // 8. 寄送郵件
        mailSender.send(mime);
        log.info("已回覆給 {}（主旨：\"{}\"）", recipient, mime.getSubject());
        return true; // 回覆已成功寄出
    }

    /**
     * 依據原始郵件建立回覆主旨（格式為 "Re: 原主旨"）。
     * 搭配 In-Reply-To/References 標頭，讓郵件客戶端將回覆歸入同一串對話，而非顯示為新郵件。
     */
    private String replySubject(IncomingEmail original, AgentResponse response) {
        // 1. 取得原始郵件的主旨
        String base = original.subject() != null && !original.subject().isBlank()
                ? original.subject()
                : (response.replySubject() != null ? response.replySubject() : "");
        // 2. 如果原始主旨以 "Re:" 開頭，則不加 "Re:"，否則加 "Re:" 開頭
        return base.regionMatches(true, 0, "Re:", 0, 3) ? base : "Re: " + base;
    }

    /**
     * 將客戶的原始郵件以引用區塊形式附在 Agent 回覆的下方
     * （格式：「On &lt;日期&gt;, &lt;寄件人&gt; wrote: &gt; ...」），
     * 與一般郵件客戶端的行為一致，讓回覆帶有完整的對話脈絡。
     */
    private String quoteOriginal(IncomingEmail original, String replyBody) {

        // 1. 取得原始郵件的內容
        String body = original.body() != null ? original.body() : "";
        // 2. 如果原始內容空白，則回覆中不加引用區塊
        String quoted = body.isBlank()
                ? ""
                : body.stripTrailing().lines().map(line -> "> " + line).reduce((a, b) -> a + "\n" + b).orElse("");
        /*
          原始內文：
            "我的訂單在哪裡？"
            "已等了三天了"

          加上 > 後：
            "> 我的訂單在哪裡？"
            "> 已等了三天了"
        * */

        // 3. 將原始內容加上引用標籤
        return """
                %s
                
                於 %s，%s 寫道：
                %s""".formatted(
                replyBody.stripTrailing(),
                QUOTE_DATE.format(original.receivedAt()),
                original.from(),
                quoted);
        /*
          """
          %s                        ← Agent 的回覆內容

          於 %s，%s 寫道：          ← 引用來源說明行
          %s                        ← 加了 > 的原始內文
          """

          ---
          最終產生的郵件內文範例

          您好，您的訂單已於今日出貨，預計明天送達。

          於 Wed, 11 Jun 2026 at 14:03，customer@example.com 寫道：
          > 我的訂單在哪裡？
          > 已等了三天了
        */
    }
}
