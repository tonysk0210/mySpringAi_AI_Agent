package com.example.myagentclient.model;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * LLM 處理單封支援郵件後回傳的結構化結果。
 * 區分兩種受眾：{@code replySubject}/{@code replyBody} 是實際寄給客戶的郵件，
 * 而 {@code operatorSummary} 是給查看日誌的真人的內部備註。
 * <p>
 * Jackson 的描述會以 JSON Schema 的形式提供給模型作為回應格式，請保持說明清晰易懂。
 */
@JsonClassDescription("處理支援郵件的結果：包含寄給客戶的回覆，以及內部摘要備註。")
public record AgentResponse(

        @JsonPropertyDescription("回覆郵件的主旨，例如：「Re: 訂單 4471 重複扣款」。")
        String replySubject,

        @JsonPropertyDescription("完整且可直接寄出的客戶回覆，以客戶的語言和語氣撰寫。"
                + "純文字格式，包含適當的問候語和署名。不得有任何佔位符或待填寫的 TODO。")
        String replyBody,

        @JsonPropertyDescription("給真人客服人員的簡短內部摘要：客戶是誰、他們的需求、你發現了什麼，"
                + "以及你採取了什麼行動（包含退款 ID 和支援服務單 ID）。")
        String operatorSummary
) {
}
/*
  ---
  關鍵機制：@JsonClassDescription 和 @JsonPropertyDescription

  這兩個註解不是給人看的，而是給 LLM 看的：

  Spring AI 把 AgentResponse 轉成 JSON Schema
        │
        ▼
  {
    "description": "處理支援郵件的結果...",
    "properties": {
      "replySubject": {
        "description": "回覆郵件的主旨..."
      },
      "replyBody": {
        "description": "完整且可直接寄出的客戶回覆..."
      },
      "operatorSummary": {
        "description": "給真人客服人員的簡短內部摘要..."
      }
    }
  }
        │
        ▼
  LLM 讀到這個 Schema，知道每個欄位要填什麼
        │
        ▼
  LLM 回傳符合格式的 JSON
        │
        ▼
  .entity(AgentResponse.class) 自動解析成 Java 物件

  ---
* */