# mySpringAi AI Agent — 自主式 AI 客服代理系統

一個以 **Spring Boot + Spring AI + OpenAI** 建構的自主式電子郵件客服代理系統。系統自動監控客服信箱，透過 LLM 分析來信意圖、查詢業務資料庫、執行退款等動作，最後自動回覆客戶——全程無人工介入。

---

## 目錄

- [快速開始](#快速開始)
- [Demo 流程](#demo-流程)
- [測試情境](#測試情境)
- [系統架構](#系統架構)
- [技術堆疊](#技術堆疊)
- [模組說明](#模組說明)
  - [mcp-server](#mcp-server)
  - [my-agent-client](#my-agent-client)
  - [emailUI](#emailui)
- [資料模型](#資料模型)
- [MCP 工具清單](#mcp-工具清單)
- [API 端點](#api-端點)
- [設計原則](#設計原則)

---

## 快速開始

### 前置條件

- Java 25+
- Maven（或使用 `./mvnw`）
- Docker Desktop（Mailpit）
- Node.js 20+
- `OPENAI_API_KEY` 環境變數

### 啟動步驟

**1. 啟動 MCP Server**
```bash
cd mcp-server
./mvnw spring-boot:run
# 驗證：http://localhost:8090/
```

**2. 啟動 Agent Client**
```bash
# 確認已設定環境變數
export OPENAI_API_KEY=sk-...   # Linux/macOS
$env:OPENAI_API_KEY="sk-..."   # PowerShell

cd my-agent-client
./mvnw spring-boot:run
# 驗證：http://localhost:8080
```

**3. 啟動 Mailpit**

啟動 Agent Client 時會自動啟動 Mailpit（透過 Docker Compose），請先確認 Docker Desktop 已開啟。
```bash
# 驗證：http://localhost:8025
```

**4. 啟動前端**
```bash
cd emailUI
npm install
npm run dev
# 開啟：http://localhost:5173
```

---

## Demo 流程

以下為一個完整的 Demo Cycle，展示從客戶發信到 AI 自動回覆的端對端流程。

### 步驟 1 — 確認 H2 資料庫成功建立

開啟 **http://localhost:8090/h2-console** 以確認 mcp-server 的資料庫已正確初始化。

登入資訊：
- **JDBC URL：** `jdbc:h2:file:./h2db/mcpserverdb`
- **Username：** `sa`
- **Password：** 無

連線後可查詢 CUSTOMERS、PRODUCTS、ORDERS 等資料表，確認種子資料已成功載入。

### 步驟 2 — 前端模擬客戶發信

開啟 **http://localhost:5173 **（emailUI 前端介面）。

選擇預設測試情境或自行填寫表單，點擊送出後，系統會將信件透過 `POST /seed-mail` 注入 Mailpit SMTP，模擬客戶寄信至 `support@example.com` 的完整流程。

### 步驟 3 — Mailpit 模擬客服信箱收件

開啟 **http://localhost:8025 **（Mailpit Web UI）。

Mailpit 作為虛擬客服信箱，攔截所有發往 `support@example.com` 的信件。可在此檢視原始信件內容、寄件人、主旨等資訊。

`InboxMonitor` 每 10 秒透過 Mailpit HTTP API 輪詢未讀信件，發現新信後立即交由 Agent Client 處理，並將信件標記為已讀，避免重複處理。

### 步驟 4 — 觀察 Agent Client Console 的 LLM 處理過程

在 **my-agent-client** 的 Console 中，可透過 `PrettyLoggerAdvisor` 輸出的格式化日誌，逐步觀察 LLM 的完整決策過程：

- **SYSTEM**：系統提示詞（代理角色與規則）
- **USER**：傳入的客戶信件內容
- **TOOL_CALL**：LLM 決定呼叫的 MCP 工具與參數（如 `lookup_customer_by_email`、`issue_refund` 等）
- **TOOL_RESPONSE**：MCP Server 回傳的查詢或操作結果
- **ASSISTANT**：LLM 最終生成的 `AgentResponse`（客戶回覆 + 內部摘要）

`TokenUsageAuditAdvisor` 同時記錄每次 LLM 呼叫的 input / output token 用量。

### 步驟 5 — Mailpit 攔截 AI 自動回覆信件

回到 **http://localhost:8025 **，可在 Mailpit 收件匣中看到 Agent Client 透過 SMTP 寄出的自動回覆信件，包含：

- 正確的 `In-Reply-To` 與 `References` 標頭（確保郵件串接）
- LLM 生成的回覆正文（使用客戶偵測到的語言）
- 原始信件以 `> ` 引用格式附於信末

至此完成一個完整的 **Demo Cycle**：客戶發信 → 信件進入信箱 → LLM 自主分析並操作工具 → 自動回覆客戶。

---

## 測試情境

### 情境 1：重複扣款退款

**寄件人：** priya.sharma@example.com
**情境：** 訂單 #4471 被扣款兩次（各 $199.99）
**預期流程：**
1. `lookup_customer_by_email` → 識別 Priya
2. `get_customer_orders_by_order_number(4471)` → 發現兩筆 CAPTURED
3. `detect_duplicate_charges_by_order_number(4471)` → 確認重複扣款 $199.99
4. `issue_refund(4471, 199.99, DUPLICATE_CHARGE, ...)` → 退款成功
5. `log_support_ticket(intent=BILLING_ISSUE, sentiment=NEGATIVE)`

### 情境 2：保固內退款

**寄件人：** sarah.mitchell@example.com
**情境：** AeroBlend 300 故障，申請退款
**預期流程：**
1. `lookup_customer_by_email` → 識別 Sarah
2. `get_customer_orders_by_email` → 取得訂單紀錄
3. `check_warranty_by_order_number_and_sku` → 確認產品仍在保固期內
4. `issue_refund(..., WARRANTY, ...)` → 執行保固退款
5. `log_support_ticket(intent=WARRANTY_CLAIM)`

### 情境 3：多語言諷刺投訴（請求進一步佐證）

**寄件人：** rohan.verma@example.com
**情境：** 中英混雜、充滿諷刺語氣的雜音投訴
**預期流程：**
1. 穿透諷刺語氣，識別為保固申訴
2. `check_warranty_by_order_number_and_sku` → 確認在保固期內
3. 因尚未確認實際故障狀況，**不直接核發退款**，改為請客戶提供進一步佐證（如影片或照片）
4. `log_support_ticket(intent=WARRANTY_CLAIM, sentiment=NEGATIVE, detectedLanguage="en+zh")`

### 情境 4：售前產品諮詢

**寄件人：** tonysk@example.com（系統中無此客戶）
**情境：** 詢問瑜伽墊保固期限
**預期流程：**
1. `lookup_customer_by_email` → 未找到客戶（售前場景）
2. `search_products_by_name_or_sku("yoga mat")` → 找到產品
3. 回覆產品保固資訊

---

## 系統架構

```
┌─────────────────────────────────────────────────────────────────┐
│                         emailUI (port 5173)                     │
│              React 19 + Vite 測試介面                            │
│         注入測試信件 / 查看代理回應結果                         	  │
└──────────────────────────────┬──────────────────────────────────┘
                               │ POST /seed-mail
                               ▼
┌─────────────────────────────────────────────────────────────────┐
│               my-agent-client (port 8080)                       │
│                                                                 │
│  InboxMonitor (每 10 秒輪詢)                                	  │
│       │                                                         │
│       ▼                                                         │
│  SupportAgent ──── OpenAI LLM ──── MCP Tool Loop                │
│       │                                 │                       │
│       ▼                                 │                       │
│  SupportMailSender (SMTP 回覆)     	  │                       │
└─────────────────────────────────────────┼───────────────────────┘
                                          │ MCP Streamable HTTP
                                          ▼
┌─────────────────────────────────────────────────────────────────┐
│                  mcp-server (port 8090)                         │
│                                                                 │
│  QueryTools (8 個唯讀工具)  +  ActionTools (2 個寫入工具)     	  │
│                                                                 │
│              H2 File-based Database (./h2db/)                   │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│               Mailpit Docker Container                          │
│    SMTP :1025 (接收測試信件)  +  HTTP API :8025 (UI / 輪詢)   	  │
└─────────────────────────────────────────────────────────────────┘
```

### 端對端處理流程

1. 客戶信件送達 `support@example.com`（Mailpit SMTP port 1025）
2. `InboxMonitor` 每 10 秒透過 Mailpit HTTP API 取得未讀信件
3. `SupportAgent` 將信件內容連同系統提示詞送交 OpenAI LLM
4. LLM **自主決定**要呼叫哪些 MCP 工具（無硬編碼流程）
5. MCP 工具查詢資料庫或執行退款等寫入操作
6. LLM 生成 `AgentResponse`（客戶回覆 + 內部摘要）
7. `SupportMailSender` 透過 SMTP 發送回覆，含正確的 `In-Reply-To` 標頭
8. `log_support_ticket` 工具永遠在最後被呼叫，記錄完整互動

---

## 技術堆疊

| 層級 | 技術 |
|------|------|
| 語言 | Java 25 |
| 後端框架 | Spring Boot 4.1.0 |
| AI 整合 | Spring AI 2.0.0 + OpenAI API |
| MCP 協議 | Model Context Protocol (Streamable HTTP) |
| 資料庫 | H2 File-based（`./h2db/mcpserverdb`） |
| ORM | Spring Data JPA / Hibernate |
| 郵件測試 | Mailpit（Docker） |
| 前端 | React 19.2.7 + Vite 8.1.1 |
| HTTP 客戶端 | Axios 1.18.1 |
| 建置工具 | Maven Wrapper（mvnw） |

---

## 模組說明

### mcp-server

**職責：** 以 MCP 工具形式暴露業務資料，供 LLM 代理呼叫。

**端口：** `8090`
**MCP 端點：** `POST http://localhost:8090/mcp`（Streamable HTTP）

#### 套件結構

```
mcp-server/src/main/java/com/example/mcpserver/
├── tool/
│   ├── SupportQueryTools.java    # 8 個唯讀查詢工具
│   └── SupportActionTools.java   # 2 個寫入動作工具
├── entity/                       # JPA 實體（Customer, Product, CustomerOrder…）
├── repo/                         # Spring Data JPA Repository
└── dto/SupportDtos.java          # 工具回傳值（Java records）
```

#### 重要設定（application.properties）

```properties
spring.datasource.url=jdbc:h2:file:./h2db/mcpserverdb
spring.sql.init.mode=always          # 每次啟動執行 schema.sql + data.sql
spring.jpa.hibernate.ddl-auto=none   # 由 SQL 腳本完全管理 schema
spring.jpa.open-in-view=false
spring.ai.mcp.server.protocol=streamable
spring.ai.mcp.server.streamable-http.mcp-endpoint=/mcp
```

---

### my-agent-client

**職責：** 監控郵件信箱，協調 LLM 與 MCP 工具自主處理客服信件，發送回覆。

**端口：** `8080`
**環境變數：** 需設定 `OPENAI_API_KEY`

#### 套件結構

```
my-agent-client/src/main/java/com/example/myagentclient/
├── service/
│   ├── SupportAgent.java              # ChatClient 設定、MCP 綁定、結構化輸出
│   ├── InboxMonitor.java              # @Scheduled 輪詢、去重處理
│   ├── SupportMailSender.java         # SMTP 回覆（含 In-Reply-To 標頭）
│   └── handler/
│       ├── AgentEmailHandler.java     # @Primary：呼叫 LLM → 發送回覆
│       └── LoggingEmailHandler.java   # 備用：純日誌記錄
├── advisor/
│   ├── TokenUsageAuditAdvisor.java    # 記錄每次 LLM token 用量
│   └── PrettyLoggerAdvisor.java       # ASCII box 格式美化日誌
└── controller/
    └── SeedMailController.java        # POST /seed-mail 注入測試信件
```

#### 核心類別說明

**`SupportAgent`**
- 使用 `ChatClient.Builder` 掛載系統提示詞（`support-agent-system.st`）
- 透過 `ToolCallbackProvider` 綁定所有 MCP 工具
- 呼叫 `.entity(AgentResponse.class)` 取得結構化 JSON 輸出
- Spring AI 負責驅動多輪工具呼叫迴圈

**`InboxMonitor`**
- `@Scheduled(fixedDelayString = "${my-agent-client.inbox.poll-interval:10000}")`
- 透過 Mailpit HTTP API 取得未讀信件，轉換為 `IncomingEmail` record
- `EmailHandler.handle()` 回傳 `true` → 保持已讀；回傳 `false` → 標回未讀重試

**`SupportMailSender`**
- 設定 RFC 2822 `In-Reply-To` 與 `References` 標頭確保會話串接
- 原始信件以 `> ` 引用格式附於回覆末尾

#### 資料模型

```java
record IncomingEmail(
    String messageId, String from, List<String> to,
    String subject, String body, Instant receivedAt
)

record AgentResponse(
    @JsonPropertyDescription("...") String replySubject,
    @JsonPropertyDescription("...") String replyBody,
    @JsonPropertyDescription("...") String operatorSummary
)
```

#### 系統提示詞

位於 `src/main/resources/prompts/support-agent-system.st`，繁體中文撰寫，定義代理的 6 步驟處理工作流程：

1. 以 email 查詢客戶身份
2. 識別意圖與情緒
3. 透過唯讀工具蒐集事實
4. 僅在資料支持的情況下執行退款
5. 確認 SKU 後再記錄工單
6. 永遠以 `log_support_ticket` 結束

#### 重要設定（application.properties）

```properties
spring.ai.openai.api-key=${OPENAI_API_KEY}
spring.ai.mcp.client.streamable-http.connections.mcp-server.url=http://localhost:8090
spring.ai.mcp.client.streamable-http.connections.mcp-server.endpoint=/mcp
spring.mail.host=localhost
spring.mail.port=1025
my-agent-client.inbox.base-url=http://localhost:8025
my-agent-client.inbox.address=support@example.com
my-agent-client.inbox.poll-interval=10000
my-agent-client.inbox.batch-size=50
```

#### Docker Compose（Mailpit）

```yaml
# my-agent-client/compose.yaml
services:
  mailpit:
    image: axllent/mailpit:latest
    ports:
      - "1025:1025"   # SMTP
      - "8025:8025"   # HTTP UI / API
    volumes:
      - ./data:/data  # 持久化郵件
    environment:
      MP_SMTP_AUTH_ACCEPT_ANY: 1
      MP_SMTP_AUTH_ALLOW_INSECURE: 1
      MP_MAX_MESSAGES: 5000
```

---

### emailUI

**職責：** React 測試介面，提供預設測試情境、注入信件、查看代理回應。

**端口：** `5173`（Vite dev server）

#### 元件結構

```
emailUI/src/
├── App.jsx              # 狀態管理（history, selected）
├── components/
│   ├── ComposePanel.jsx # 撰寫表單 + 預設情境選擇 + 結果卡片
│   └── Sidebar.jsx      # 寄件歷史列表 + 頭像 + 相對時間
├── App.css              # 所有元件樣式（CSS Nesting）
└── index.css            # CSS 變數（--bg, --accent, --sent, --failed）
```

**ComposePanel** 內建 4 個測試情境（`TEST_EXAMPLES` 陣列），點選可自動填入表單：

| 情境 | 寄件人 | 測試目的 |
|------|--------|---------|
| 重複扣款 | priya.sharma@example.com | 偵測同一訂單兩筆 CAPTURED 付款 |
| 保固內退款 | sarah.mitchell@example.com | 確認在保固期內並執行退款 |
| 多語言諷刺投訴 | rohan.verma@example.com | 穿透諷刺語氣、多語言偵測 |
| 售前保固詢問 | tonysk@example.com | 查詢產品規格，無需建立工單 |

---

## 資料模型

### 資料庫 Schema

```sql
CUSTOMERS       (id, full_name, email*, phone, preferred_language, loyalty_tier, created_at)
PRODUCTS        (id, sku*, name, description, category, price, currency,
                 specifications JSON, warranty_months, stock_quantity, created_at)
ORDERS          (id, order_number*, customer_id→, order_date, status,
                 shipping_address, total_amount, currency, created_at)
ORDER_ITEMS     (id, order_id→CASCADE, product_id→, quantity, unit_price)
PAYMENTS        (id, order_id→, amount, currency, payment_method,
                 transaction_ref*, status, charged_at)
                 ※ 同一 order_id 可有多筆 CAPTURED（用於重複扣款偵測）
REFUNDS         (id, order_id→, payment_id→, amount, currency,
                 reason, refund_type, status, created_at)
SUPPORT_TICKETS (id, customer_id→, order_id→, product_id→, channel,
                 subject, raw_message, detected_language, intent,
                 sentiment, status, resolution, created_at, resolved_at)
```

*= UNIQUE 約束；→ = FOREIGN KEY

### 列舉值

| 類型 | 值 |
|------|----|
| LoyaltyTier | STANDARD, SILVER, GOLD, PLATINUM |
| OrderStatus | PENDING, PAID, SHIPPED, DELIVERED, CANCELLED, RETURNED |
| PaymentStatus | AUTHORIZED, CAPTURED, FAILED, REFUNDED, PARTIALLY_REFUNDED |
| RefundType | GOODWILL, DUPLICATE_CHARGE, WARRANTY, RETURN, OTHER |
| RefundStatus | REQUESTED, APPROVED, PROCESSED, REJECTED |
| Intent | REFUND_REQUEST, PRESALES_QUESTION, BILLING_ISSUE, WARRANTY_CLAIM, COMPLAINT, GENERAL, OTHER |
| Sentiment | POSITIVE, NEUTRAL, NEGATIVE, ANGRY |
| TicketStatus | OPEN, RESOLVED, ESCALATED |

### 預載種子資料

| 客戶 | Email | 訂單 | 測試場景 |
|------|-------|------|---------|
| Sarah Mitchell | sarah.mitchell@example.com | 4198, 3801, 4007（均為 AeroBlend 300） | 觸發保固內退款 |
| Priya Sharma | priya.sharma@example.com | 4471（ChefPro 刀具組） | 同訂單有兩筆 CAPTURED 付款 |
| Rohan Verma | rohan.verma@example.com | 4502（手持攪拌器） | 諷刺語氣、中英混雜 |
| James Cooper | james.cooper@example.com | 無 | — |

---

## MCP 工具清單

### 查詢工具（唯讀，`@Transactional(readOnly = true)`）

| 工具名稱 | 輸入參數 | 回傳型別 | 用途 |
|---------|---------|---------|------|
| `lookup_customer_by_email` | `email: String` | `CustomerInfo` | 識別寄件人；永遠第一個呼叫 |
| `get_customer_orders_by_email` | `email: String` | `List<OrderDetails>` | 取得所有訂單與付款紀錄（最新在前） |
| `get_customer_orders_by_order_number` | `orderNumber: String` | `OrderDetails` | 依訂單號查詢單一訂單 |
| `search_products_by_name_or_sku` | `query: String` | `List<ProductInfo>` | 依名稱或 SKU 模糊搜尋產品 |
| `get_product_by_sku` | `sku: String` | `ProductInfo` | 取得完整產品規格（含 JSON specifications） |
| `detect_duplicate_charges_by_order_number` | `orderNumber: String` | `DuplicateChargeResult` | 偵測同訂單多筆 CAPTURED 付款 |
| `check_warranty_by_order_number_and_sku` | `orderNumber: String`, `sku: String?` | `WarrantyStatus` | 確認產品是否在保固期內 |
| `get_customer_ticket_history_by_email` | `email: String` | `TicketHistory` | 查詢歷史工單，識別重複故障模式 |

### 動作工具（寫入，`@Transactional`）

| 工具名稱 | 輸入參數 | 回傳型別 | 資料庫操作 |
|---------|---------|---------|-----------|
| `issue_refund` | `orderNumber`, `amount: BigDecimal`, `refundType`, `reason` | `RefundResult` | INSERT REFUNDS；UPDATE PAYMENTS → REFUNDED |
| `log_support_ticket` | `customerEmail`, `rawMessage`, `intent`, `sentiment`, `subject`, `detectedLanguage`, `resolution`, `orderNumber?`, `sku?` | `TicketLogResult` | INSERT SUPPORT_TICKETS（status=RESOLVED） |

---

## API 端點

### my-agent-client（port 8080）

| 方法 | 路徑 | 說明 |
|------|------|------|
| `POST` | `/seed-mail` | 注入測試信件至 Mailpit |

**Query 參數：** `from`, `subject`, `body`（均可選，含預設值）

**回應範例：**
```json
{
  "status": "sent",
  "message": "Test email injected successfully",
  "from": "customer@example.com",
  "to": "support@example.com",
  "subject": "Order Issue",
  "body": "I want a refund for order #4471",
  "error": null,
  "timestamp": "2026-07-10T10:30:00Z"
}
```

**CORS：** 允許 `localhost:5173` 與 `localhost:5174`（Vite dev ports）

### Mailpit（port 8025）

| 方法 | 路徑 | 說明 |
|------|------|------|
| `GET` | `/api/v1/messages` | 列出所有信件 |
| `GET` | `/api/v1/message/{id}` | 取得單一信件完整內容 |
| `PUT` | `/api/v1/message/{id}/read` | 標記為已讀 |
| `GET` | `/` | Web UI |

### H2 Console（port 8080）

`http://localhost:8090/h2-console`
- JDBC URL: `jdbc:h2:file:./h2db/mcpserverdb`
- Username: `sa`（預設）
- Password: 無

---

## 設計原則

### LLM 自主性
無硬編碼工作流程。LLM 根據系統提示詞中的規則，**自主決定**呼叫哪些工具、呼叫幾次、以什麼順序。Spring AI 的多輪工具呼叫迴圈使這成為可能。

### 讀寫分離
查詢工具標記 `@Transactional(readOnly = true)`，動作工具標記 `@Transactional`。DTO（Java records）在事務內建立，防止 Hibernate Proxy 洩漏到工具回傳值。

### 容錯機制
- `EmailHandler.handle()` 回傳 `false` → 信件標回未讀，下次輪詢重試
- 工具拋出例外 → Spring AI 捕捉並回傳錯誤給 LLM
- `issue_refund` 在無 CAPTURED 付款時拋出明確例外，防止誤退款

### 可追溯性
- 每次互動永遠以 `log_support_ticket` 結尾，記錄完整原始信件、決策摘要
- `operatorSummary` 捕捉 LLM 決策理由（內部用）
- `TokenUsageAuditAdvisor` 記錄每次 LLM 呼叫的 token 用量

### 資料庫冪等初始化
`schema.sql` 使用 `CREATE TABLE IF NOT EXISTS`；`data.sql` 使用 `MERGE INTO`，確保每次重啟不會重複建表或重複插入資料。
