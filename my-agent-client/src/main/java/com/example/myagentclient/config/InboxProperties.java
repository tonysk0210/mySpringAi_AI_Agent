package com.example.myagentclient.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Agent 監控 Mailpit 收件匣所需的設定，綁定自 {@code my-agent-client.inbox.*}。
 *
 * @param baseUrl      Mailpit HTTP API/UI 基底網址
 * @param address      Agent 監控的支援信箱地址
 * @param pollInterval 收件匣輪詢間隔，單位為毫秒
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
        if (pollInterval <= 0) {
            pollInterval = 10_000L; // ← 預設值
        }
        if (batchSize <= 0) {
            batchSize = 50; // ← 預設值
        }
    } // 出了這個 {} 之後，欄位就被鎖定，不能再改了
}
