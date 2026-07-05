package com.example.myagentclient.controller;

import com.example.myagentclient.config.InboxProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * 僅供測試使用的輔助 Controller，透過 SMTP 將假郵件植入受監控的收件匣，
 * 讓收件匣監控器有信件可以撈取。
 * <p>
 * 這是一個測試專用的 REST Controller，讓開發者透過 HTTP POST 請求，把假郵件植入 Mailpit 收件匣，用來觸發 Agent 的處理流程。
 * <pre>
 *   curl -X POST "http://localhost:8080/seed-mail?subject=退款&amp;body=我的訂單在哪裡？"
 * </pre>
 */
@RestController
@RequiredArgsConstructor
public class SeedMailController {

    private final JavaMailSender mailSender;
    private final InboxProperties inbox;


    /**
     *
     * # 使用預設值
     * curl -X POST "http://localhost:8080/seed-mail"
     * # 自訂內容
     * curl -X POST "http://localhost:8080/seed-mail?from=tony@test.com&subject=退款申請&body=我要退款"
     */
    @PostMapping("/seed-mail")
    public ResponseEntity<SeedResult> seed(
            @RequestParam(defaultValue = "customer@example.com") String from,
            @RequestParam(defaultValue = "測試支援請求") String subject,
            @RequestParam(defaultValue = "您好，我需要協助處理我最近的訂單。") String body) {

        // 1. 建立假郵件
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(inbox.address());
        message.setSubject(subject);
        message.setText(body);

        try {
            mailSender.send(message);
        } catch (MailException e) {
            // 通常表示 Mailpit SMTP 伺服器（compose.yaml）尚未啟動。
            return ResponseEntity.status(502).body(SeedResult.failed(from, inbox.address(), subject, e)); // 失敗 │ 502 Bad Gateway │ Mailpit 未啟動，回傳錯誤訊息
        }

        return ResponseEntity.ok(SeedResult.sent(from, inbox.address(), subject, body)); // 成功 │ 200 OK          │ 郵件已植入，回傳 SeedResult
    }

    /**
     * 植入嘗試的結果——回傳植入的內容，讓呼叫端確認 Agent 即將撈取的郵件內容。
     *
     * @param status    {@code "sent"}（已寄出）或 {@code "failed"}（失敗）
     * @param message   人類可讀的結果說明
     * @param from      假郵件使用的寄件人地址
     * @param to        郵件送達的受監控信箱
     * @param subject   郵件主旨
     * @param body      郵件內文（僅成功時存在）
     * @param error     失敗詳情（僅失敗時存在）
     * @param timestamp 嘗試的時間
     */
    public record SeedResult(
            String status,
            String message,
            String from,
            String to,
            String subject,
            String body,
            String error,
            Instant timestamp
    ) {
        static SeedResult sent(String from, String to, String subject, String body) {
            return new SeedResult("sent",
                    "郵件已成功寄送至 %s，Agent 將在下次輪詢時撈取。".formatted(to),
                    from, to, subject, body, null, Instant.now());
        }

        static SeedResult failed(String from, String to, String subject, Exception e) {
            return new SeedResult("failed",
                    "無法將郵件寄送至 %s，請確認 Mailpit SMTP 伺服器是否正在運行。".formatted(to),
                    from, to, subject, null, e.getMessage(), Instant.now());
        }
    }
}
/*
  ---
  SeedResult — 內部 record

  SeedResult.sent("customer@example.com", "support@example.com", "退款", "我要退款")

  產生：
  {
    "status": "sent",
    "message": "郵件已成功寄送至 support@example.com，Agent 將在下次輪詢時撈取。",
    "from": "customer@example.com",
    "to": "support@example.com",
    "subject": "退款",
    "body": "我要退款",
    "error": null,
    "timestamp": "2026-07-06T10:00:00Z"
  }

  ---
  SeedResult.failed() — 失敗

  SeedResult.failed("customer@example.com", "support@example.com", "退款", e)

  產生：
  {
    "status": "failed",
    "message": "無法將郵件寄送至 support@example.com，請確認 Mailpit SMTP 伺服器是否正在運行。",
    "from": "customer@example.com",
    "to": "support@example.com",
    "subject": "退款",
    "body": null,
    "error": "Connection refused: localhost/127.0.0.1:1025",
    "timestamp": "2026-07-06T10:00:00Z"
  }

  有兩個靜態工廠方法：

  ┌─────────────────────┬──────┬────────────┬───────────┐
  │        方法          │ 用於 │ error 欄位  │ body 欄位 │
  ├─────────────────────┼──────┼────────────┼───────────┤
  │ SeedResult.sent()   │ 成功  │ null       │ 有值      │
  ├─────────────────────┼──────┼────────────┼───────────┤
  │ SeedResult.failed() │ 失敗  │ 有錯誤訊息  │ null      │
  └─────────────────────┴──────┴────────────┴───────────┘

  ---
* */
