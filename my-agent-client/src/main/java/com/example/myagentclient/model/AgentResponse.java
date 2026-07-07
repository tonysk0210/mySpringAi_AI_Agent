package com.example.myagentclient.model;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * LLM 處理郵件後的結構化回應；{@code replyBody} 寄給客戶，{@code operatorSummary} 留給真人查閱。
 * {@code @JsonPropertyDescription} 會轉成 JSON Schema 傳給模型，修改時請保持說明清晰。
 */
@JsonClassDescription("處理支援郵件的結果：包含寄給客戶的回覆，以及內部摘要備註。")
public record AgentResponse(

        @JsonPropertyDescription("回覆郵件的主旨，例如：「Re: 訂單 4471 重複扣款」。")
        String replySubject,

        @JsonPropertyDescription("完整且可直接寄出的客戶回覆，以客戶的語言和語氣撰寫。純文字格式，包含適當的問候語和署名。不得有任何佔位符或待填寫的 TODO。")
        String replyBody,

        @JsonPropertyDescription("給真人客服人員的簡短內部摘要：客戶是誰、他們的需求、你發現了什麼，以及你採取了什麼行動（包含退款 ID 和支援服務單 ID）。")
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