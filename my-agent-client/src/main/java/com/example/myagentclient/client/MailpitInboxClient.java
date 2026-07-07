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
 * Mailpit 收件匣 REST API 的薄層封裝。將 Agent 所需的幾個讀取呼叫封裝起來，
 * 讓其他程式碼不需要直接處理原始 HTTP 或 Mailpit 網址。
 * 這是一個封裝 Mailpit 收件匣相關 API 的客戶端類別，Agent 透過它來查詢、讀取、標記郵件，不需要在其他地方直接寫 HTTP 請求。
 *
 * @see <a href="https://mailpit.axllent.org/docs/api-v1/">Mailpit API v1</a>
 */
@Component
public class MailpitInboxClient {

    private final RestClient restClient;
    private final String inboxAddress;

    public MailpitInboxClient(RestClient.Builder builder, InboxProperties props) {
        this.restClient = builder.baseUrl(props.baseUrl()).build(); // Spring 的 HTTP 客戶端，設定好 base URL（http://localhost:8025）
        this.inboxAddress = props.address(); // 支援信箱地址（support@example.com）
    }

    /**
     * 列出寄給支援信箱（support@example.com）的未讀郵件（最新的排在最前面）。
     * <p>
     * 過濾條件：收件人是支援信箱、且寄件人不是支援信箱。
     * 這樣可以排除 Agent 自己寄出的回覆——因為 Mailpit 會攔截所有經過的郵件（包含 Agent 的回覆），
     * 若不排除，Agent 會把自己的回覆也當成新信件處理，導致無限迴圈。
     */
    public List<MailpitMessageSummary> listUnread(int limit) {
        // 1. 組合查詢條件，找出未讀且寄送給支援信箱的郵件，排除自己寄出的信
        String query = "is:unread to:%s !from:%s".formatted(inboxAddress, inboxAddress); // is:unread to:support@example.com !from:support@example.com

        // 2. 呼叫 Mailpit API 來查詢郵件，並取得回應 (打 HTTP GET 請求)
        //  GET http://localhost:8025/api/v1/search
        //      ?query=is:unread to:support@example.com !from:support@example.com
        //      &limit=50
        MailpitMessagesResponse response = restClient.get()
                .uri(uri -> uri.path("/api/v1/search") // 組合完整網址加上參數 - 查詢未讀郵件列表
                        .queryParam("query", query) // 查詢條件
                        .queryParam("limit", limit) // 限制數量
                        .build())
                .retrieve() // 發送請求，等待回應
                .body(MailpitMessagesResponse.class); // 把回傳的 JSON 自動轉成 MailpitMessagesResponse 物件

        // 3. 回應可能為 null，因此需要檢查並處理
        return response != null ? response.messages() : List.of();
    }

    /**
     * 這是一個依郵件 ID 取得完整郵件內容的方法，會向 Mailpit 打一個 HTTP GET 請求，並把回傳的 JSON 自動轉成 MailpitMessage 物件。副作用：Mailpit 會將該郵件標記為已讀。
     */
    public MailpitMessage getMessage(String id) {
        return restClient.get()
                .uri("/api/v1/message/{id}", id)
                .retrieve()
                .body(MailpitMessage.class);
    }

    /**
     * 這是一個手動設定郵件已讀／未讀狀態的方法，主要用於當郵件處理失敗時，把它標回未讀，讓下次輪詢能重新處理。
     */
    public void setRead(String id, boolean read) {
        restClient.put() // 使用 HTTP PUT 方法（更新資料用）
                .uri("/api/v1/messages")
                .contentType(MediaType.APPLICATION_JSON) // 告訴伺服器傳的是 JSON 格式
                .body(new ReadUpdate(List.of(id), read))
                .retrieve()
                .toBodilessEntity();
    }
    /*
      實際送出的請求
      PUT http://localhost:8025/api/v1/messages
      Content-Type: application/json

      {
        "IDs": ["abc123"],
        "Read": false
      }
     */

    /**
     * {@code PUT /api/v1/messages} 的請求本體。
     */
    private record ReadUpdate(
            @JsonProperty("IDs") List<String> ids,
            @JsonProperty("Read") boolean read
    ) {
    }
}
