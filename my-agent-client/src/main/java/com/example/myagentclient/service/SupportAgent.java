package com.example.myagentclient.service;

import com.example.myagentclient.config.InboxProperties;
import com.example.myagentclient.model.AgentResponse;
import com.example.myagentclient.model.IncomingEmail;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

/**
 * Agent 的核心大腦。封裝了一個與 MCP 伺服器工具整合的 Spring AI {@link ChatClient}，
 * 讓 LLM 自主完成整個處理流程：讀取郵件、呼叫所需的 MCP 工具來識別客戶並收集資訊、
 * 決定行動方案、執行行動，並記錄工單。
 * <p>
 * 這裡不手動協調個別工具呼叫——{@code spring-ai-starter-mcp-client} 自動配置提供的
 * {@code ToolCallbackProvider} 會交給 {@code ChatClient}，由 Spring AI 自動執行工具呼叫迴圈。
 * 我們只需要提供模型的指令（系統提示）以及要處理的郵件內容。
 */
@Service
public class SupportAgent {

    private final ChatClient chatClient;

    public SupportAgent(ChatClient.Builder chatClientBuilder,
                        ToolCallbackProvider mcpTools, // MCP 伺服器提供的工具集
                        InboxProperties inbox, // 信箱設定（address 等）
                        @Value("classpath:/prompts/support-agent-system.st") Resource systemPrompt) { // 告訴 LLM：你是誰、你的工作是什麼、你應該怎麼處理郵件
        this.chatClient = chatClientBuilder
                // 1. 提供模型操作信箱的完整指令（系統提示），並注入所代表的支援信箱地址。
                .defaultSystem(sys -> sys.text(systemPrompt)
                        .param("support_address", inbox.address())) // 把信箱地址注入到系統提示裡
                // 2. 公開 MCP 伺服器提供的所有工具。Spring AI 自動在迴圈中執行這些工具，讓 LLM 可以視需要執行任意多個步驟。
                .defaultTools(mcpTools)
                .build();
    }

    /**
     * 將單封郵件交給 LLM，讓它端對端自主完成整個處理流程。
     *
     * @return Agent 的結構化處理結果：包含寄給客戶的回覆內容，
     * 以及 Agent 對郵件內容理解與處理行動的內部摘要。
     */
    public AgentResponse resolve(IncomingEmail email) {
        return chatClient.prompt()
                // 1. 把郵件內容組成 user message 給 LLM
                .user(u -> u.text("""
                                支援收件匣收到一封新郵件，請處理。
                                
                                寄件人     : {from}
                                收件人     : {to}
                                收到時間   : {receivedAt}
                                主旨       : {subject}
                                
                                內文：
                                {body}
                                """)
                        .param("from", email.from())
                        .param("to", String.join(", ", email.to()))
                        .param("receivedAt", email.receivedAt())
                        .param("subject", email.subject())
                        .param("body", email.body()))
                .call()
                .entity(AgentResponse.class); // 2. 把回應自動轉成 Java entity 物件
    }
}
/*
  ---
  整體流程

  resolve(email)
        │
        ▼
  組合 user message（郵件內容）
        │
        ▼
  ChatClient 送給 LLM
    ├── 系統提示（你是支援 Agent，信箱是 support@example.com）
    ├── User 訊息（這封郵件的內容）
    └── MCP 工具（查訂單、查客戶資料...）
        │
        ▼
  LLM 自主決定：
    ├── 呼叫 MCP 工具查資料
    ├── 決定回覆內容
    └── 產生結構化回應
        │
        ▼
  .entity(AgentResponse.class)  自動解析成 Java 物件
        │
        ▼
  回傳 AgentResponse（回覆內容 + 內部摘要）

  ---
  關鍵設計：LLM 自主驅動

  這裡完全不寫「先查訂單、再查客戶、再回覆」這種流程，而是：

  把郵件丟給 LLM
      + 系統提示（告訴它怎麼工作）
      + MCP 工具（給它能用的工具）
           │
           ▼
  LLM 自己決定要呼叫哪些工具、呼叫幾次、怎麼回覆

  這就是 Agentic AI 的核心概念：讓 LLM 自主規劃並執行，而不是人工寫死流程。
* */