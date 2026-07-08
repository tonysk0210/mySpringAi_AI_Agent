import { useState } from "react";
import Sidebar from "./components/Sidebar";
import ComposePanel from "./components/ComposePanel";
import "./App.css";

function App() {
  const [history, setHistory] = useState([]);
  const [selected, setSelected] = useState(null);

  const lastResult = history[0] ?? null;
  const statusClass = lastResult
    ? lastResult.status === "sent"
      ? "connected"
      : "error"
    : "idle";
  const statusText = {
    connected: "已連線",
    error: "傳送失敗",
    idle: "待機中",
  }[statusClass];

  function handleSend(result) {
    setHistory((prev) => [result, ...prev]);
    setSelected(0);
  }

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
