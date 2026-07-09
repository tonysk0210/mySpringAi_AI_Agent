package com.example.myagentclient.controller;

import com.example.myagentclient.config.InboxProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * 測試用 Controller：透過 SMTP 將假郵件植入 Mailpit，觸發 Agent 的處理流程。
 * <pre>curl -X POST "http://localhost:8080/seed-mail?subject=退款&amp;body=我的訂單在哪裡？"</pre>
 */
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:5174"})
@RestController
@RequiredArgsConstructor
public class SeedMailController {

    private final JavaMailSender mailSender;
    private final InboxProperties inbox;


    /**
     * 植入測試郵件；所有參數皆有預設值，可直接 POST 不帶任何參數。
     */
    @PostMapping("/seed-mail")
    public ResponseEntity<SeedResult> seed(
            @RequestParam(defaultValue = "customer@example.com") String from,
            @RequestParam(defaultValue = "測試支援請求") String subject,
            @RequestParam(defaultValue = "您好，我需要協助處理我最近的訂單。") String body) {

        // 1. 使用 SimpleMailMessage（不需要 MimeMessage），只是模擬一封普通客戶來信，不需自訂標頭
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(inbox.address()); // 植入受監控的支援收件匣 support@example.com，InboxMonitor 下次輪詢時會撈到
        message.setSubject(subject);
        message.setText(body);

        try {
            mailSender.send(message);
        } catch (MailException e) {
            // Mailpit SMTP（compose.yaml）尚未啟動；502 = 上游服務不可用
            return ResponseEntity.status(502).body(SeedResult.failed(from, inbox.address(), subject, e));
        }

        // 2. 回傳植入結果
        return ResponseEntity.ok(SeedResult.sent(from, inbox.address(), subject, body));
    }

    /**
     * 植入嘗試的結果；{@code body} 僅成功時有值，{@code error} 僅失敗時有值。emailUI 的 history useState 結構
     * SeedResult - static nested record
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
        // 成功：body 填入郵件內文，error 為 null
        static SeedResult sent(String from, String to, String subject, String body) {
            return new SeedResult("sent",
                    "郵件已成功寄送至 %s，Agent 將在下次輪詢時撈取。".formatted(to),
                    from, to, subject, body, null, Instant.now());
        }

        // 失敗：error 填入例外訊息，body 為 null（郵件未寄出，無內文可回報）
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
