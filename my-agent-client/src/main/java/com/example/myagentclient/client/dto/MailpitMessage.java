package com.example.myagentclient.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Mailpit {@code /api/v1/message/{ID}} 端點回傳的完整郵件內容。
 * 注意：呼叫此端點會在伺服器端將郵件標記為已讀。
 */
public record MailpitMessage(
        @JsonProperty("ID") String id,
        @JsonProperty("MessageID") String messageId,
        @JsonProperty("From") MailpitAddress from,
        @JsonProperty("To") List<MailpitAddress> to,
        @JsonProperty("Subject") String subject,
        @JsonProperty("Date") String date,
        @JsonProperty("Text") String text,
        @JsonProperty("HTML") String html
) {
    public List<MailpitAddress> to() {
        return to != null ? to : List.of();
    }
}
