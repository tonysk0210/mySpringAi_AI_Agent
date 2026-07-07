# Repository Guidelines（儲存庫指南）

## 專案結構與模組組織
本專案是 Maven 架構的 Java 25 Spring Boot MCP Server。主要程式碼位於 `src/main/java/com/example/mcpserver`。

- `tool/`：對外提供 MCP tools 的 Spring service，使用 `@McpTool` 標註。
- `repo/`：Spring Data JPA repository。
- `entity/`：JPA entity 與 enum。
- `dto/`：tool 回傳用的 DTO。
- `src/main/resources/application.properties`：server、MCP、logging、H2 與 SQL 初始化設定。
- `src/main/resources/sql/schema.sql`、`data.sql`：資料庫 schema 與種子資料。
- `src/test/java`：JUnit/Spring Boot 測試。
- `h2db/` 與 `target/` 是執行或建置產物，不應當作原始碼修改。

## 建置、測試與本機開發指令
請優先使用 Maven wrapper，確保不同環境使用一致的 Maven 行為。

- `.\mvnw.cmd spring-boot:run`：本機啟動 MCP Server，預設 port 為 `8090`。
- `.\mvnw.cmd test`：執行 JUnit/Spring Boot 測試。
- `.\mvnw.cmd clean package`：清理、編譯、測試並輸出 artifact 到 `target/`。
- `.\mvnw.cmd clean`：移除建置輸出。

在 Unix-like shell 中，請改用 `./mvnw`。

## 程式風格與命名慣例
遵循既有 Java 慣例：4 空格縮排、package 小寫、class 使用 `PascalCase`、method 與 field 使用 `camelCase`。`@McpTool(name = "...")` 請使用描述清楚的 `snake_case` 動作名稱，例如 `lookup_customer_by_email`。

Spring service 優先使用 Lombok `@RequiredArgsConstructor` 做 constructor injection。唯讀查詢 tool 建議標註 `@Transactional(readOnly = true)`；會改變狀態的操作應集中在 action-oriented service。

## 測試規範
測試使用 JUnit 5 與 Spring Boot test support。測試類別放在 `src/test/java` 對應 package 下，類別名稱以 `Tests` 結尾，例如 `McpServerApplicationTests`。

修改 repository query、transaction 行為、MCP tool 輸入輸出、SQL schema 或 seed data 時，請補上聚焦測試。提交 PR 前至少執行 `.\mvnw.cmd test`。

## Commit 與 Pull Request 指南
近期 commit 多為簡短祈使句，部分使用 Conventional Commit prefix，例如 `refactor: ...`、`update`。建議使用能說明意圖的訊息，例如 `fix: handle duplicate refund requests` 或 `docs: update MCP tool instructions`。

PR 應包含變更摘要、測試結果、相關 issue 連結，以及設定或資料庫變更說明。若行為需透過 client 驗證，可附上 sample MCP call 或截圖。

## 安全與設定注意事項
不要提交 secrets。H2 設定為 `spring.datasource.url=jdbc:h2:file:./h2db/mcpserverdb`，請將 `h2db/` 視為本機執行狀態。schema 與 seed data 請維護在 `src/main/resources/sql`，確保新環境可重現初始化結果。
