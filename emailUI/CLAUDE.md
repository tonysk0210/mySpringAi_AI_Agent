# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 常用指令

```bash
npm run dev       # 啟動開發伺服器，預設 localhost:5173（若被占用自動切換至 5174）
npm run build     # 生產環境建置，輸出至 dist/
npm run lint      # 執行 ESLint
npm run preview   # 本地預覽 dist/ 建置結果
```

本專案未配置任何測試框架。

## 架構說明

單頁 React 19 + Vite 8 應用程式，無路由。唯一目的是呼叫 Spring Boot 後端的 `POST http://localhost:8080/seed-mail?from=&subject=&body=`，並在畫面上顯示回傳結果。

### 狀態管理 — `App.jsx`

所有共享狀態集中在此：

- `history[]` — `SeedResult` 物件陣列，最新的在最前面
- `selected` — 指向 `history` 的索引，決定 `ComposePanel` 顯示哪一筆結果

`handleSend(result)` 會將新結果 prepend 至 `history` 並將 `selected` 設為 0。Header 的狀態燈根據 `history[0].status` 決定（`sent` → 綠色脈衝、`failed` → 紅色、無歷史 → 灰色）。

### 組件

**`Sidebar.jsx`** — 接收 `history` 與 `selected`，點擊列時觸發 `onSelect(i)`。純展示層，無自身狀態。

**`ComposePanel.jsx`** — 包含三個區塊：

- 撰寫表單（axios `POST /seed-mail`）
- `TEST_EXAMPLES` 靜態陣列 — 更新測試案例說明（情境 / 測試目的 / 預期結果）時，直接編輯此陣列
- 結果卡片，由 `displayResult` prop（= `history[selected]`）驅動

### Axios 錯誤處理（ComposePanel）

兩種失敗路徑：

1. **後端可達但 SMTP 離線**（502）→ `err.response.data` 為 `SeedResult` JSON → 直接傳入 `onSend()`
2. **後端完全無法連線**（網路錯誤）→ `err.response` 為 null → 手動建立 fallback 物件傳入 `onSend()`，並顯示 `networkError` 橫幅

fallback 物件必須符合 `SeedResult` record 結構：`{ status, message, from, to, subject, body, error, timestamp }`。

### 樣式

- `src/index.css` — 僅存放 CSS 變數（`--bg`、`--accent`、`--sent`、`--failed` 等）與全域 reset，不寫任何組件樣式
- `src/App.css` — 所有組件樣式，使用 CSS Nesting（Vite 原生支援，無需額外 plugin）。`.glass` 為玻璃擬態共用 class。動畫：`fadeSlideIn`（結果卡片）、`slideFromRight`（案例說明欄）、`pulse-dot`（狀態燈）、`spin`（載入 spinner）
- 結果卡片使用 `key={displayResult.timestamp}` 強制 remount，以重新觸發 `fadeSlideIn`
- 案例說明欄使用 `key={selectedExample.from}` 觸發 `slideFromRight`

### 後端相依服務

- Spring Boot — `localhost:8080`，`SeedMailController` 上標註 `@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:5174"})`（無全域 WebConfig）
- Mailpit SMTP — `localhost:1025`（透過 Docker Compose 啟動）
