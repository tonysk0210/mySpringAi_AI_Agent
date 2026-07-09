# CLAUDE.md

此檔案提供 Claude Code (claude.ai/code) 在此專案中操作時所需的指引。

## 專案概覽

本專案是一個多模組 Maven 專案，實作自主式 AI 電子郵件客服代理，由三個元件組成：

1. **mcp-server** (port 8090) — Spring Boot MCP（模型上下文協議）伺服器，將業務資料以工具形式暴露給 AI 代理
2. **my-agent-client** (port 8080) — Spring Boot AI 代理，定期輪詢郵件信箱，透過 Spring AI 與 OpenAI 自主處理客服信件
3. **emailUI** (port 5173) — React 19 + Vite 前端，用於注入測試信件並查看代理回應結果

**技術堆疊：** Java 25、Spring Boot 4.1.0、Spring AI 2.0.0、H2 檔案型資料庫、Mailpit（Docker 假 SMTP）、React 19、Vite、Axios

## 建置與執行指令

### MCP Server
```bash
cd mcp-server
./mvnw spring-boot:run          # 執行（port 8090）
./mvnw test                     # 執行測試
./mvnw package -DskipTests      # 建置 JAR
```

### Agent Client
需設定環境變數 `OPENAI_API_KEY`。
```bash
cd my-agent-client
docker compose up -d            # 啟動 Mailpit（SMTP :1025，UI :8025）
./mvnw spring-boot:run          # 執行代理（port 8080）

# 注入測試信件
curl -X POST "http://localhost:8080/seed-mail" \
  -G --data-urlencode 'from=customer@example.com' \
  --data-urlencode 'subject=Order Issue' \
  --data-urlencode 'body=I want a refund for order #ORD-001'
```

### 前端
```bash
cd emailUI
npm install
npm run dev      # 開發伺服器，含 HMR（port 5173）
npm run build    # 生產環境建置
npm run lint     # ESLint 檢查
```

## 系統架構

### 資料流程
1. 客戶信件送達 `support@example.com`（Mailpit SMTP）
2. `InboxMonitor` 每 10 秒輪詢 Mailpit REST API 取得未讀信件
3. `SupportAgent` 將信件內容連同系統提示詞與 MCP 工具送交 OpenAI LLM
4. LLM 自主決定呼叫哪些 MCP 工具查詢資料並執行動作，無硬編碼流程
5. `AgentEmailHandler` 發送回覆；`LoggingEmailHandler` 記錄互動日誌

### MCP 工具（mcp-server）
- **查詢工具**（`SupportQueryTools.java`，唯讀交易）：`lookup_customer`、`get_orders`、`get_order_details`、`get_payment_info`、`detect_duplicate_charges`、`check_warranty`、`get_ticket_history`、`list_all_products`
- **動作工具**（`SupportActionTools.java`）：`issue_refund`、`log_support_ticket`

### Agent Client 關鍵類別
- `SupportAgent.java` — ChatClient 設定、結構化輸出（`AgentResponse` record → JSON Schema）、MCP 工具綁定
- `InboxMonitor.java` — `@Scheduled` 輪詢、透過已處理訊息 ID 進行去重
- `prompts/support-agent-system.st` — 繁體中文系統提示詞

### 資料庫
H2 檔案型資料庫，路徑 `./h2db/`。Schema 與種子資料位於 `mcp-server/src/main/resources/sql/`。資料表：CUSTOMERS、PRODUCTS、ORDERS、ORDER_ITEMS、PAYMENTS、REFUNDS、SUPPORT_TICKETS。Schema 使用 `CREATE TABLE IF NOT EXISTS`；種子資料使用 `MERGE INTO` 確保重啟安全。

H2 主控台：`http://localhost:8080/h2-console`

## 重要設定

| 服務 | URL / 設定值 |
|---|---|
| MCP Server | `http://localhost:8090/mcp` |
| Agent Client | `http://localhost:8080` |
| Mailpit UI | `http://localhost:8025` |
| Mailpit SMTP | `localhost:1025` |
| 客服信箱 | `support@example.com` |
| 輪詢間隔 | 10 秒 |

兩個 Spring Boot 模組的 `application.properties` 均位於 `src/main/resources/`，代理端的 MCP 端點與輪詢間隔皆可在此調整。

## 各模組文件

每個模組均有自己的 `CLAUDE.md` 與 `AGENTS.md`，記載模組專屬規範：
- `mcp-server/CLAUDE.md` — MCP 工具設計規則、SQL 相容性注意事項
- `my-agent-client/CLAUDE.md` — 代理工作流程、Spring AI 設定細節
- `emailUI/CLAUDE.md` — React 元件結構、Axios 錯誤處理模式
