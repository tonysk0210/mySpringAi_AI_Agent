# Repository Guidelines

## 專案結構與模組組織

此儲存庫包含三個專案：

- `mcp-server/`：Spring Boot MCP server，包含 JPA entity、repository、MCP tools、H2 schema 與 seed data。Java 程式碼位於 `src/main/java/com/example/mcpserver`；測試位於 `src/test/java`。
- `my-agent-client/`：Spring Boot / Spring AI client，負責監控 Mailpit、呼叫 MCP tools，並寄出客服回覆。Java 程式碼位於 `src/main/java/com/example/myagentclient`；prompt template 位於 `src/main/resources/prompts`。
- `emailUI/`：Vite React email interface。原始碼位於 `src/`，可重用 UI 元件位於 `src/components`。

`target/`、`dist/`、本機 H2 資料夾、Mailpit data 與 IDE 檔案屬於產物或本機狀態，不是原始碼。

## 建置、測試與開發指令

請在對應專案目錄下執行指令：

- `.\mvnw.cmd test`：執行 Spring Boot/JUnit 測試。
- `.\mvnw.cmd spring-boot:run`：在本機啟動 Spring Boot module。
- `.\mvnw.cmd clean package`：編譯、測試並封裝 JAR。
- `docker compose up -d`：當 module 提供 `compose.yaml` 時，啟動本機基礎服務。
- `npm run dev`：在 `emailUI` 啟動 Vite dev server。
- `npm run build`：建立 production frontend build。
- `npm run lint`：對 React app 執行 ESLint。

## 程式風格與命名慣例

Java 程式碼使用 4 個空白縮排、package 小寫、class 使用 `PascalCase`，member 使用 `camelCase`。Spring component 請依角色分組：`controller`、`service`、`client`、`repo`、`entity`、`tool`、`config`、`advisor`。優先使用 constructor injection；DTO 類資料優先使用不可變 record。

React component 檔名使用 `PascalCase`，例如 `ComposePanel.jsx`；hook 與 helper 使用 `camelCase`。

## 測試準則

Spring 測試使用 JUnit 5 與 Spring Boot test support。測試請放在 `src/test/java` 的對應 package 下，命名如 `SupportAgentTests` 或 `McpServerApplicationTests`。修改 repository、MCP tool contract、prompt parsing、mail handling 或 configuration binding 時，請新增聚焦測試。

Frontend 變更應通過 `npm run lint` 與 `npm run build`。

## Commit 與 Pull Request 準則

近期 commit history 使用較短訊息，例如 `update`；後續請優先使用更清楚的祈使句，例如 `fix: handle missing ticket email` 或 `docs: refresh MCP setup notes`。

Pull request 應包含變更摘要、測試結果、相關 issue 連結，以及 UI 或 MCP 行為變更的截圖或 sample request。

## 安全與設定提示

不要提交 API key、mailbox 內容、本機 H2 檔案或特定機器的 IDE 設定。請使用 `OPENAI_API_KEY` 等環境變數；修改 `application.properties` 或 `compose.yaml` 時，請記錄需要的本機服務與 port。
