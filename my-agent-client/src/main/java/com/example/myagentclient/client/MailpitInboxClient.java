package com.example.myagentclient.client;

import com.example.myagentclient.client.dto.MailpitMessage;
import com.example.myagentclient.client.dto.MailpitMessageSummary;
import com.example.myagentclient.client.dto.MailpitMessagesResponse;
import com.example.myagentclient.config.InboxProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * Mailpit 收件匣 REST API 的薄層封裝，集中處理查詢、讀取、標記郵件等 HTTP 呼叫。
 *
 * @see <a href="https://mailpit.axllent.org/docs/api-v1/">Mailpit API v1</a>
 */
@Component
public class MailpitInboxClient {

    private final RestClient restClient;
    private final String inboxAddress;

    public MailpitInboxClient(RestClient.Builder builder, InboxProperties inboxProperties) {
        this.restClient = builder.baseUrl(inboxProperties.baseUrl()).build(); // Spring 的 HTTP 客戶端，設定好 base URL（http://localhost:8025）
        this.inboxAddress = inboxProperties.address(); // 支援信箱地址（support@example.com）
    }

    /**
     * 列出寄給支援信箱（support@example.com）的未讀郵件（最新的排在最前面）。
     * <p>
     * 排除寄件人也是支援信箱的郵件，避免 Agent 把自己的回覆再次當成新信處理。
     */
    public List<MailpitMessageSummary> listUnread(int limit) {
        // 1. 組合查詢條件，找出寄送給 support@example.com 且未讀的郵件，並排除自己寄出的信
        String query = "is:unread to:%s !from:%s".formatted(inboxAddress, inboxAddress); // is:unread to:support@example.com !from:support@example.com

        // 2. 透過 Mailpit API 來查詢郵件，並取得回應 (打 HTTP GET 請求)
        //  GET http://localhost:8025/api/v1/search
        //      ?query=is:unread to:support@example.com !from:support@example.com
        //      &limit=50
        MailpitMessagesResponse response = restClient.get()
                .uri(uri -> uri.path("/api/v1/search")
                        .queryParam("query", query)
                        .queryParam("limit", limit)
                        .build())
                .retrieve()
                .body(MailpitMessagesResponse.class); // 解析 JSON 回應為 MailpitMessagesResponse 物件

        // 3. 回傳郵件清單
        return response != null ? response.messages() : List.of();
    }

    /**
     * 依郵件 ID 取得完整郵件內容。Mailpit 會在讀取後將該郵件標記為已讀。
     */
    public MailpitMessage getMessage(String id) {
        return restClient.get()
                .uri("/api/v1/message/{id}", id)
                .retrieve()
                .body(MailpitMessage.class); // 解析 JSON 回應為 MailpitMessage 物件
    }

    /**
     * 設定郵件已讀／未讀狀態；處理失敗時會標回未讀，讓下次輪詢重新處理。
     */
    /*
      送出的請求會長這樣

      PUT http://localhost:8025/api/v1/messages
      Content-Type: application/json

      {
        "IDs": ["abc123"],
        "Read": false
      }
    */
    public void setRead(String id, boolean read) {
        restClient.put()
                .uri("/api/v1/messages")
                .contentType(MediaType.APPLICATION_JSON) // 告訴伺服器傳的是 JSON 格式
                .body(new ReadUpdate(List.of(id), read))
                .retrieve()
                .toBodilessEntity();
    }

    /**
     * {@code PUT /api/v1/messages} 的請求本體。
     */
    private record ReadUpdate(
            @JsonProperty("IDs") List<String> ids,
            @JsonProperty("Read") boolean read
    ) {
    }
}
