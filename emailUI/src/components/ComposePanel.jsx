import { useState } from "react";
import axios from "axios";

const API_BASE = "http://localhost:8080";

// 測試範例
const TEST_EXAMPLES = [
  {
    from: "priya.sharma@example.com",
    subject: "訂單號 4471 被收了兩次費用",
    body: "您好，我的訂單編號 4471 被重複扣款了。我發現信用卡上有兩筆 $199.99 的扣款紀錄，請協助退還重複扣款的金額，謝謝。",
  },
  {
    from: "sarah.mitchell@example.com",
    subject: "Whats wrong with you guys",
    body: "For the thrid time in a row, I received a belnder with a cracked jug. I no more want to take this stress. Give me my money back.",
  },
  {
    from: "rohan.verma@example.com",
    subject: "你們的可服務真好",
    body: "哇，這服務真是太驚人了。我訂單編號 #4502 的 BrewWell 快煮壺才用『剛好整整一週』就突然不加熱了——還真好意思說是物超所值啊。喔對了，順便提一下你們所謂的『優質』品質，同一個箱子裡的 HushMix 手持攪拌機聽起來簡直像一台水泥預拌車，完全不像你們網站上宣稱的那麼『安靜』。幫個忙把這兩個問題都給我處理好，否則下次我就直接去寫差評了，謝謝",
  },
  {
    from: "tonysk@example.com",
    subject: "ZenFlow Yoga Mat 的保固相關問題",
    body: "請問 ZenFlow 瑜珈墊的保固期是多久？我目前正在考慮購買...",
  },
];

export default function ComposePanel({ onSend, displayResult }) {
  const [from, setFrom] = useState("customer@example.com");
  const [subject, setSubject] = useState("測試支援請求");
  const [body, setBody] = useState("您好，我需要協助處理我最近的訂單。");
  const [loading, setLoading] = useState(false); // 控制按鈕 disable 與 spinner 顯示
  const [networkError, setNetworkError] = useState(null); // 完全無法連線時才顯示的錯誤橫幅

  function applyExample(example) {
    setFrom(example.from);
    setSubject(example.subject);
    setBody(example.body);
    setNetworkError(null);
  }

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
      <div className="compose-workspace">
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

        <aside className="examples-panel glass" aria-label="測試範例">
          <div className="examples-header">
            <h3>測試範例</h3>

            <span>{TEST_EXAMPLES.length}</span>
          </div>
          <div className="example-list">
            {TEST_EXAMPLES.map((example, index) => (
              <button
                key={example.from}
                type="button"
                className="example-item"
                onClick={() => applyExample(example)}
              >
                <span className="example-index">{index + 1}</span>
                <span className="example-content">
                  <span className="example-subject">{example.subject}</span>
                  <span className="example-from">{example.from}</span>
                  <span className="example-body">{example.body}</span>
                </span>
              </button>
            ))}
          </div>
        </aside>
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
