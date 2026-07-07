package com.example.myagentclient.service;

import com.example.myagentclient.advisor.PrettyLoggerAdvisor;
import com.example.myagentclient.advisor.TokenUsageAuditAdvisor;
import com.example.myagentclient.config.InboxProperties;
import com.example.myagentclient.model.AgentResponse;
import com.example.myagentclient.model.IncomingEmail;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

/**
 * 封裝 {@link ChatClient} 與 MCP 工具，將郵件交給 LLM 端對端自主處理。
 * 不手動協調工具呼叫順序——Spring AI 自動執行工具呼叫迴圈，我們只需提供系統提示與郵件內容。
 */
@Service
public class SupportAgent {

    private final ChatClient chatClient;

    public SupportAgent(ChatClient.Builder chatClientBuilder,
                        ToolCallbackProvider mcpTools,  // spring-ai-starter-mcp-client 自動配置，包含 MCP 伺服器暴露的所有工具（查客戶、查訂單、退款等）
                        InboxProperties inboxProperties,          // 取 address 注入系統提示，讓 LLM 知道它代表哪個支援信箱
                        @Value("classpath:/prompts/support-agent-system.st") Resource systemPrompt) { // 定義 LLM 角色、處理流程與輸出格式的完整指令

        this.chatClient = chatClientBuilder
                // 1. 載入系統提示；{support_address} 是提示中的佔位符，在此注入實際信箱地址
                .defaultSystem(sys -> sys.text(systemPrompt)
                        .param("support_address", inboxProperties.address()))

                // 2. 掛載所有 MCP 工具；Spring AI 自動執行工具呼叫迴圈，LLM 可視需要呼叫任意次
                .defaultTools(mcpTools)

                // 3. TokenUsageAuditAdvisor 記錄每次呼叫的 token 用量；PrettyLoggerAdvisor 格式化印出 prompt/response
                .defaultAdvisors(new TokenUsageAuditAdvisor(), new PrettyLoggerAdvisor())
                .build();
    }

    /**
     * 將郵件交給 LLM 自主處理，回傳含客戶回覆與內部摘要的 {@link AgentResponse}。
     */
    public AgentResponse resolve(IncomingEmail email) {

        return chatClient.prompt()
                // 1. 將客戶來信的各欄位填入 user message 模板，作為 LLM 本次需要處理的輸入內容
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
                        .param("to", String.join(", ", email.to())) // List<String> → 逗號分隔字串
                        .param("receivedAt", email.receivedAt())
                        .param("subject", email.subject())
                        .param("body", email.body()))
                // 2. 觸發 LLM 呼叫（含工具呼叫迴圈），並依 AgentResponse 的 JSON Schema 將回應解析成 Java 物件
                .call()
                .entity(AgentResponse.class);
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