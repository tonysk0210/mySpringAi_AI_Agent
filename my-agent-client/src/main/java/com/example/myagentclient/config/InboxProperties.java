package com.example.myagentclient.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Agent 透過 Mailpit REST API 監控的收件匣設定。
 * 綁定自 {@code my-agent-client.inbox.*} 設定。
 *
 * @param baseUrl      Mailpit HTTP API/UI 的基底網址（例如 http://localhost:8025）
 * @param address      支援信箱地址；植入測試郵件時作為收件人使用
 * @param pollInterval 輪詢新郵件的間隔時間，單位為毫秒
 * @param batchSize    每次輪詢最多抓取的未讀郵件數量
 */
@ConfigurationProperties(prefix = "my-agent-client.inbox")
public record InboxProperties(
        String baseUrl,
        String address,
        long pollInterval,
        int batchSize
) {
    // ← 這是 record 的 compact constructor（緊湊建構子）
    public InboxProperties { // ← 不需要寫參數，自動繼承所有欄位
        // 這裡可以修改值
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "http://localhost:8025"; // ← 預設值
        }
        if (address == null || address.isBlank()) {
            address = "support@example.com"; // ← 預設值
        }
        if (batchSize <= 0) {
            batchSize = 50; // ← 預設值
        }
    }// 出了這個 {} 之後，欄位就被鎖定，不能再改了
}
