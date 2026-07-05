# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 常用指令

```bash
# 啟動應用程式
./mvnw spring-boot:run

# 編譯（不執行測試）
./mvnw compile -DskipTests

# 完整建置
./mvnw package -DskipTests

# 執行測試
./mvnw test

# 執行單一測試類別
./mvnw test -Dtest=ClassName

# H2 Console（應用程式啟動後）
# URL: http://localhost:8080/h2-console
# JDBC URL: jdbc:h2:file:./h2db/mcpserverdb;AUTO_SERVER=true
# Username: sa / Password: (空白)
```

## 專案用途

這是一個 **Spring AI MCP Server**，為 AI Agent 提供結構化工具，用以自動化顧客支援電郵回覆。Agent 的工作流程固定：先使用唯讀查詢工具理解情境，再使用行動工具執行決策。

## 架構概覽

```
com.example.mcpserver
├── entity/          # JPA 實體 + Enums（所有業務 enum 集中於 Enums.java）
├── repo/            # Spring Data JPA Repository（含自訂查詢方法）
├── dto/SupportDtos  # 全為 Java record，MCP tool 的唯一回傳型別
└── tool/
    ├── SupportQueryTools   # 8 個唯讀 MCP 工具（@Transactional readOnly）
    └── SupportActionTools  # 2 個寫入 MCP 工具（issue_refund、log_support_ticket）
```

### MCP 工具設計分層

**查詢層（SupportQueryTools）**：`lookup_customer_by_email` → `get_customer_orders` / `get_order_by_number` → `search_products` / `get_product_by_sku` → `detect_duplicate_charges` → `check_warranty` → `get_customer_ticket_history`

**行動層（SupportActionTools）**：`issue_refund`、`log_support_ticket`

### 關鍵資料模型設計

- **payments 表允許同一 order_id 有多筆 CAPTURED 記錄**，這是 `detect_duplicate_charges` 能運作的前提，不是設計缺陷。
- **SupportTicket 歷史**讓 Agent 能識別重複性故障（如 Sarah 攪拌機第三次損壞觸發善意補償）。
- **`specifications` 欄位為 JSON 字串**（`Product.specifications` 型別為 `String`，非 JPA JSON 型別），存放各類商品屬性（電壓、尺寸、材質等）。
- **CustomerOrder**（而非 `Order`）是類別名稱，因 `ORDER` 是 SQL 保留字。

### DTO 設計規則

`SupportDtos.java` 內所有 DTO 均為 **Java record**，設計為平面、可序列化結構。MCP tool 方法只能回傳這些 record，絕不回傳 JPA 實體（避免 Hibernate Proxy 序列化問題）。

## 資料庫與初始化

- 使用 **H2 檔案型資料庫**（`./h2db/mcpserverdb`），重啟後資料持久保存。
- `spring.sql.init.mode=always`：每次啟動都執行 `sql/schema.sql` 和 `sql/data.sql`。
- `schema.sql` 使用 `CREATE TABLE IF NOT EXISTS`，`data.sql` 使用 `MERGE INTO … KEY(id)`，確保冪等性。
- 每個 MERGE 區塊後有 `ALTER TABLE … RESTART WITH 100`，防止 AUTO_INCREMENT 與 seed 資料 id 衝突。
- `spring.jpa.hibernate.ddl-auto=none`，Schema 完全由 `schema.sql` 管理，JPA 不介入。

## 新增 MCP 工具的規則

1. **查詢工具**加至 `SupportQueryTools`（標記 `@Transactional(readOnly = true)`）。
2. **寫入工具**加至 `SupportActionTools`（標記 `@Transactional`）。
3. 在 `SupportDtos.java` 新增對應的 **Java record** 作為回傳型別。
4. `@McpTool(name=…)` 的 name 使用 snake_case；description 使用繁體中文。
5. `@McpToolParam` 的 description 使用繁體中文；可選參數加 `required = false`。

## SQL 相容性注意事項（H2）

修改 `sql/schema.sql` 或 `sql/data.sql` 時：
- **禁用** MySQL 專屬語法：`ENGINE=InnoDB`、`CHARSET`、`COLLATE`、`ENUM(...)`、`JSON_OBJECT('k',v)`、`TIMESTAMP(date, timestr)`、`CURDATE() - INTERVAL n DAY`
- 時間計算改用：`DATEADD('SECOND', s, CAST(DATEADD('DAY', -n, CURRENT_DATE) AS TIMESTAMP))`
- JSON 欄位的資料用純字串字面值插入：`'{"key":"value"}'`
- 索引在 `CREATE TABLE` 外用 `CREATE INDEX IF NOT EXISTS` 單獨建立
