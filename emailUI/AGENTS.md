# Repository Guidelines

## 專案結構與模組組織

此 repository 是用於測試 email support agent 的 Vite React 前端。應用程式碼位於 `src/`，其中 `src/main.jsx` 負責啟動 React，`src/App.jsx` 負責協調頁面狀態。可重用 UI 元件放在 `src/components/`，目前包含送出種子信的 `ComposePanel.jsx` 與顯示寄送歷史的 `Sidebar.jsx`。樣式分為 `src/index.css` 的全域樣式與 `src/App.css` 的應用程式版面。靜態瀏覽器資源位於 `public/`，正式建置輸出會產生在 `dist/`。

## 建置、測試與開發指令

- `npm install`：依 `package-lock.json` 安裝 dependencies。
- `npm run dev`：啟動 Vite 開發伺服器並支援 hot reload。
- `npm run build`：建立 production bundle，輸出到 `dist/`。
- `npm run preview`：在本機預覽已建置的 bundle。
- `npm run lint`：對整個專案執行 ESLint。

此 UI 會將測試郵件 POST 到 `http://localhost:8080/seed-mail`，因此測試 end-to-end 行為時需先在本機啟動 Spring Boot backend。

## 程式風格與命名慣例

使用 JavaScript ES modules 與 React function components。元件檔案與匯出元件使用 PascalCase，例如 `ComposePanel.jsx`；區域函式、state 變數與事件處理器使用 camelCase，例如 `handleSubmit`。當格式化邏輯變多時，優先用小型 helper function 維持 JSX 可讀性。現有 JSX 較多的檔案使用兩個空白縮排，多數 source files 使用分號；編輯時請遵循周邊檔案風格。

## 測試準則

目前尚未設定自動化測試框架。提交變更前，請執行 `npm run lint` 與 `npm run build`。UI 行為需手動驗證 `npm run dev`、表單驗證、測試範例選取、backend 成功回應，以及 backend 不可用時的 network-error 處理。若未來新增測試，建議將 React component tests 放在 `src/` 旁邊，命名如 `ComposePanel.test.jsx`。

## Commit 與 Pull Request 準則

近期 Git history 使用較短的 `update` 訊息，對 review 來說資訊不足。請改用祈使句且有範圍的 commit message，例如 `Add seed email example panel` 或 `Fix backend connection error state`。Pull request 應包含精簡摘要、驗證步驟、可用時連結相關 issue；若有可見 UI 變更，請附上截圖或錄影。

## Agent 專用注意事項

搜尋 repository 時優先使用 `rg`。變更範圍應保持聚焦；除非明確要求，避免編輯產生出的 `dist/` 內容。使用者可見文字請保留繁體中文，除非該功能本身就是要調整語言行為。
