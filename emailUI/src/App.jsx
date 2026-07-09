import { useState } from "react";
import Sidebar from "./components/Sidebar";
import ComposePanel from "./components/ComposePanel";
import "./App.css";

function App() {
  const [history, setHistory] = useState([]); // 所有寄信結果的陣列
  const [selected, setSelected] = useState(null); // 目前選到哪一筆歷史紀錄的 index。初始值是 null，代表還沒有選任何紀錄

  // 2. render 時，從 history 取得最新一筆寄信結果
  const lastResult = history[0] ?? null;

  // 3. render 時，根據最新結果決定 statusClass
  const statusClass = lastResult
    ? lastResult.status === "sent"
      ? "connected"
      : "error"
    : "idle";

  // 4. render 時，根據 statusClass 決定顯示文字
  const statusText = {
    connected: "已連線",
    error: "傳送失敗",
    idle: "待機中",
  }[statusClass];

  // 1. 寄信完成後，更新 history state
  function handleSend(result) {
    setHistory((prev) => [result, ...prev]); // 將新的寄信結果加到歷史紀錄的最前面
    setSelected(0); // 將選取的 index 設為 0，代表選到最新的寄信結果
  }

  // 根據 selected 的值，決定 ComposePanel 要顯示哪一筆歷史紀錄的結果
  const displayResult = selected !== null ? history[selected] : null;

  return (
    <div className="email-app">
      {/* header */}
      <header className="app-header">
        <div className="app-logo">
          <span className="logo-icon">🐋</span>
          <span className="logo-title">Email Agent Tester</span>
        </div>
        <div className={`status-indicator ${statusClass}`}>
          <span className="status-dot" />
          <span className="status-text">{statusText}</span>
        </div>
      </header>

      <div className="app-body">
        <Sidebar history={history} selected={selected} onSelect={setSelected} />
        <ComposePanel onSend={handleSend} displayResult={displayResult} />
      </div>
    </div>
  );
}

export default App;
