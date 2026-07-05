package com.example.myagentclient.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Mailpit 列表／搜尋端點回傳的郵件摘要。
 * 取得完整郵件只需要 {@code ID}，其餘欄位主要用於日誌記錄。
 */
public record MailpitMessageSummary(
        @JsonProperty("ID") String id,
        @JsonProperty("MessageID") String messageId,
        @JsonProperty("Read") boolean read,
        @JsonProperty("From") MailpitAddress from,
        @JsonProperty("Subject") String subject,
        @JsonProperty("Created") String created
) {
}
