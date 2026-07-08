# Repository Guidelines

## 專案結構與模組組織

這是用於電子郵件客服 Agent 的 Spring Boot 4.1 / Spring AI client。主要 Java 程式碼位於 `src/main/java/com/example/myagentclient`。

- `controller/`: HTTP endpoint，包含測試寄信輔助功能。
- `service/`: Agent 編排、收件匣輪詢、寄信與 handler 策略。
- `client/`: Mailpit API client 與 DTO。
- `model/`: 應用程式與 LLM 結構化輸出使用的 record。
- `advisor/`: ChatClient 日誌與 token 用量稽核 advisor。
- `config/`: 型別化的應用程式設定。
- `src/main/resources/`: `application.properties` 與 `prompts/` 內的提示模板。
- `src/test/java/`: Spring Boot 測試。
- `compose.yaml`: 本機 Mailpit 服務；`data/` 儲存 Mailpit 持久化資料。

## 建置、測試與開發指令

- `docker compose up -d`: 啟動 Mailpit，SMTP 位於 `localhost:1025`，UI/API 位於 `localhost:8025`。
- `./mvnw spring-boot:run`: 在本機執行應用程式。需要 `OPENAI_API_KEY`，且 MCP server 需位於 `http://localhost:8090/mcp`。
- `./mvnw test`: 執行 JUnit / Spring Boot 測試。
- `./mvnw clean package`: 編譯、測試並建立應用程式 JAR。
- `./mvnw clean package -DskipTests`: 明確略過測試時快速打包。

若要建立測試郵件，POST 到 `http://localhost:8080/seed-mail`，再到 `http://localhost:8025` 檢查回覆。

## 程式風格與命名慣例

遵循 Java 25 慣例並使用 4 個空白縮排。套件維持在 `com.example.myagentclient` 底下。Spring 元件依職責命名，例如 `InboxMonitor`、`SupportAgent` 或 `MailpitInboxClient`。優先使用建構子注入；DTO 類資料優先使用不可變 record。長提示詞應放在 `src/main/resources/prompts`，不要直接嵌入 Java。

## 測試準則

測試使用 JUnit 5 與 Spring Boot test 支援。測試檔放在 `src/test/java` 下對應的套件中，測試類別命名為 `*Tests` 或 `*Test`。修改 Agent 流程時，請針對解析、client 行為與 handler 邏輯新增聚焦測試。開 pull request 前執行 `./mvnw test`。

## Commit 與 Pull Request 準則

近期 commit 訊息較簡短（`update`），後續請改用更清楚的祈使句，例如 `Add inbox retry handling`。Pull request 應包含簡短摘要、測試結果、設定變更，以及郵件行為改變時的截圖或 Mailpit 證據。有相關 issue 時請一併連結。

## 安全與設定提示

不要提交真實 API key、含敏感內容的 Mailpit 資料庫，或本機 IDE 設定。透過環境變數設定 `OPENAI_API_KEY`。除非需要特定 profile 覆寫，MCP endpoint、收件信箱、輪詢間隔與 Mailpit 設定應維持在 `application.properties`。
