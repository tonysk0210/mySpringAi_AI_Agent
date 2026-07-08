# CLAUDE.md

此檔案提供 Claude Code（claude.ai/code）在此專案中工作時的指引。

## 專案概述

**my-agent-client** 是一個展示 Agentic AI 模式的 Spring Boot 應用程式：一個自主的電子郵件客服 Agent，會定期輪詢信箱、透過 LLM 呼叫 MCP 工具處理每封郵件，並自動發送回覆——完全不寫死任何業務流程。LLM 自行決定要呼叫哪些工具。

## 建置與啟動

```bash
# 啟動 Mailpit（需先執行）
docker compose up -d

# 啟動應用程式（需設定 OPENAI_API_KEY 環境變數）
./mvnw spring-boot:run

# 打包 JAR
./mvnw clean package -DskipTests
```

**測試 Agent**，注入一封模擬來信：
```bash
curl -X POST "http://localhost:8080/seed-mail" \
  -G \
  --data-urlencode 'from=customer@example.com' \
  --data-urlencode 'subject=訂單狀態查詢' \
  --data-urlencode 'body=我的訂單到哪裡了？'
```

接著到 **http://localhost:8025** 的 Mailpit Web UI 查看 Agent 的回覆。

## 必要環境

- `OPENAI_API_KEY` — OpenAI API 金鑰
- MCP Server 須執行於 `http://localhost:8090/mcp`（提供商業工具：查詢客戶、訂單狀態、退款等）
- Docker 用於啟動 Mailpit（SMTP：`localhost:1025`，API/UI：`localhost:8025`）

## 架構

**技術堆疊：** Spring Boot 4.1.0 · Spring AI 2.0.0 · Java 25 · Maven

**郵件處理流程：**
```
@Scheduled（每 10 秒 fixedDelay）
  → InboxMonitor 輪詢 Mailpit，抓取 support@example.com 的未讀郵件
  → EmailHandler.handle(IncomingEmail)
    → SupportAgent.resolve(email)           ← ChatClient + MCP 工具
      → LLM 自主決定呼叫哪些工具
      → 回傳 AgentResponse（replySubject、replyBody、operatorSummary）
    → SupportMailSender 透過 SMTP 發送回覆（含 RFC 2822 郵件串接標頭）
    → 成功：郵件保持已讀 | 失敗：標回未讀，下次輪詢重試
```

**`SupportAgent`** 是核心元件：在建構時組裝 `ChatClient`，載入系統提示（`classpath:/prompts/support-agent-system.st`，繁體中文）、透過 `ToolCallbackProvider` 掛載所有 MCP 工具，並附加兩個 Advisor（`TokenUsageAuditAdvisor`、`PrettyLoggerAdvisor`）。`resolve()` 只需呼叫一次 `ChatClient.prompt().call().entity(AgentResponse.class)`，Spring AI 會自動執行工具呼叫迴圈。

**Handler 策略：** `AgentEmailHandler`（標註 `@Primary`）執行 LLM 處理。若要切換為純記錄模式，將 `@Primary` 移至 `LoggingEmailHandler`。

**結構化輸出：** `AgentResponse` 是 record，Spring AI 會自動推導其 JSON Schema 供 LLM 填寫。

## 重要設定（`application.properties`）

- `spring.ai.mcp.client.streamable-http.connections.mcp-server.url=http://localhost:8090`
- `my-agent-client.inbox.address=support@example.com` — 監控的信箱地址
- `my-agent-client.inbox.poll-interval=10000` — 輪詢間隔（毫秒）
- `logging.level.com.example.myagentclient.advisor.PrettyLoggerAdvisor=DEBUG` — 開啟完整的 LLM prompt/response 日誌
