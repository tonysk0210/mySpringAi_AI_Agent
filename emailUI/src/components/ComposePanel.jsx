import { useState } from "react";
import axios from "axios";

const API_BASE = "http://localhost:8080";

export default function ComposePanel({ onSend, displayResult }) {
  const [from, setFrom] = useState("customer@example.com");
  const [subject, setSubject] = useState("測試支援請求");
  const [body, setBody] = useState("您好，我需要協助處理我最近的訂單。");
  const [loading, setLoading] = useState(false); // 控制按鈕 disable 與 spinner 顯示
  const [networkError, setNetworkError] = useState(null); // 完全無法連線時才顯示的錯誤橫幅

  // 提交表單時，先防止預設行為，然後設定 loading 為 true，並清空 networkError
  async function handleSubmit(e) {
    e.preventDefault();
    setLoading(true);
    setNetworkError(null);

    const params = new URLSearchParams({ from, subject, body }); // 將表單資料轉換成 URLSearchParams 格式

    try {
      // 1. 發送 POST 請求到後端
      const { data } = await axios.post(`${API_BASE}/seed-mail?${params}`);
      // 2. 將後端回傳的資料傳遞給父組件函式 handleSend(data);
      /**
       * 把這次後端回傳的寄信結果加到 history 最前面
       * 把目前選取項目設成 0，代表畫面顯示最新一筆結果
       */
      onSend(data); // 這裡的 data 是 axios response 裡的 response.data，也就是後端 /seed-mail 回傳的 JSON 內容。
    } catch (err) {
      if (err.response?.data) {
        onSend(err.response.data);
      } else {
        const fallback = {
          status: "failed",
          message: "無法連線至後端伺服器，請確認 Spring Boot 是否正在運行。",
          from,
          to: null,
          subject,
          body: null,
          error: err.message,
          timestamp: new Date().toISOString(),
        };
        // 3. 如果是網路連線錯誤，就顯示錯誤訊息
        onSend(fallback);
        setNetworkError(fallback.message);
      }
    } finally {
      setLoading(false);
    }
  }

  return (
    <main className="compose-panel">
      <div className="compose-card glass">
        <div className="compose-header">
          <h2>撰寫種子信件</h2>
          <p>填寫下方欄位後送出，Agent 將在下次輪詢時處理此郵件。</p>
        </div>

        {/* 表單開始 */}
        <form className="compose-form" onSubmit={handleSubmit}>
          <div className="field-group">
            <label htmlFor="from">寄件人</label>
            <input
              id="from"
              type="email"
              value={from}
              onChange={(e) => setFrom(e.target.value)}
              placeholder="customer@example.com"
              required
            />
          </div>
          <div className="field-group">
            <label htmlFor="subject">主旨</label>
            <input
              id="subject"
              type="text"
              value={subject}
              onChange={(e) => setSubject(e.target.value)}
              placeholder="測試支援請求"
              required
            />
          </div>
          <div className="field-group">
            <label htmlFor="body">內容</label>
            <textarea
              id="body"
              value={body}
              onChange={(e) => setBody(e.target.value)}
              placeholder="您好，我需要協助處理我最近的訂單。"
              rows={5}
              required
            />
          </div>
          <div className="form-footer">
            {/*  */}
            {networkError && (
              <div className="network-error">{networkError}</div>
            )}
            <button type="submit" className="send-btn" disabled={loading}>
              {loading ? (
                <>
                  <span className="spinner" />
                  傳送中...
                </>
              ) : (
                <>
                  <span>✈</span>
                  送出種子信
                </>
              )}
            </button>
          </div>
        </form>
      </div>

      {displayResult && (
        <div
          key={displayResult.timestamp}
          className={`result-card glass result-${displayResult.status}`}
        >
          <div className="result-header">
            <div className={`result-icon result-icon-${displayResult.status}`}>
              {displayResult.status === "sent" ? "✓" : "✕"}
            </div>
            <div>
              <div
                className={`result-status-badge badge-${displayResult.status}`}
              >
                {displayResult.status === "sent" ? "已送出" : "傳送失敗"}
              </div>
              <p className="result-message">{displayResult.message}</p>{" "}
              {/* 郵件已成功寄送至 support@example.com，Agent 將在下次輪詢時撈取。 */}
            </div>
          </div>

          <div className="result-fields">
            {displayResult.to && (
              <div className="result-field">
                <span className="field-label">收件人</span>
                <span className="field-value">{displayResult.to}</span>
              </div>
            )}
            {displayResult.from && (
              <div className="result-field">
                <span className="field-label">寄件人</span>
                <span className="field-value">{displayResult.from}</span>
              </div>
            )}
            {displayResult.subject && (
              <div className="result-field">
                <span className="field-label">主旨</span>
                <span className="field-value">{displayResult.subject}</span>
              </div>
            )}
            {displayResult.body && (
              <div className="result-field">
                <span className="field-label">內容</span>
                <span className="field-value body-value">
                  {displayResult.body}
                </span>
              </div>
            )}
            {displayResult.error && (
              <div className="result-field">
                <span className="field-label error-label">錯誤</span>
                <span className="field-value error-value">
                  {displayResult.error}
                </span>
              </div>
            )}
            {displayResult.timestamp && (
              <div className="result-field">
                <span className="field-label">時間</span>
                <span className="field-value">
                  {new Date(displayResult.timestamp).toLocaleString("zh-TW")}
                </span>
              </div>
            )}
          </div>
        </div>
      )}
    </main>
  );
}
