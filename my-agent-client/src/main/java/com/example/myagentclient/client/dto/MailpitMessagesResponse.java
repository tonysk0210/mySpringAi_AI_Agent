package com.example.myagentclient.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Mailpit {@code /api/v1/messages} 和 {@code /api/v1/search} 端點回傳的回應封裝。
 */
// @JsonProperty 告訴 Jackson（JSON 轉換套件）：JSON 裡的 "total" 對應到 Java 的 total 欄位。這裡名稱一樣所以不影響，但如果名稱不同（如 base-url → baseUrl）就很重要。
public record MailpitMessagesResponse(
        @JsonProperty("total") int total,
        @JsonProperty("unread") int unread,
        @JsonProperty("count") int count,
        @JsonProperty("messages") List<MailpitMessageSummary> messages
) {
    //  Compact Constructor 的防禦：當 API 沒有回傳 messages 欄位時，回傳空列表而不是 null，避免呼叫端出現 NullPointerException。
    public List<MailpitMessageSummary> messages() {
        return messages != null ? messages : List.of();
    }
}


/*
  Mailpit API 實際回傳的 JSON 長這樣

  {
    "total": 100,
    "unread": 5,
    "count": 50,
    "messages": [
      { "ID": "abc123", ... },
      { "ID": "def456", ... }
    ]
  }
*/