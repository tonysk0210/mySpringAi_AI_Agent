import { useState } from "react";
import axios from "axios";

const API_BASE = "http://localhost:8080";

// 測試範例
const TEST_EXAMPLES = [
  {
    from: "priya.sharma@example.com",
    subject: "訂單號 4471 被收了兩次費用",
    body: "您好，我的訂單編號 4471 被重複扣款了。我發現信用卡上有兩筆 $199.99 的扣款紀錄，請協助退還重複扣款的金額，謝謝。",
    context: "客戶指出同一筆訂單被信用卡重複扣款，並明確提供訂單編號與金額。",
    purpose:
      "測試 Agent 是否能辨識帳務與退款類客服請求，正確擷取訂單編號 4471 與重複扣款金額。",
    expectedResult:
      "預期應歸類為退款或帳務問題，回覆需確認會協助查核並處理重複扣款退款。",
  },
  {
    from: "sarah.mitchell@example.com",
    subject: "What's wrong with you guys",
    body: "For the third time in a row, I received a blender with a cracked jug. I no more want to take this stress. Give me my money back.",
    context: "客戶使用英文抱怨連續三次收到破損果汁機，語氣強烈且要求退款。",
    purpose:
      "測試 Agent 是否能處理拼字錯誤、負面情緒與明確退款要求，不只依賴主旨判斷。",
    expectedResult:
      "預期應辨識為高不滿度的商品破損退款案例，回覆需安撫客戶並啟動退款或升級處理。",
  },
  {
    from: "rohan.verma@example.com",
    subject: "你們的服務可真好",
    body: "哇，這服務真是太驚人了。我訂單編號 #4502 的 BrewWell 快煮壺才用『剛好整整一週』就突然不加熱了——還真好意思說是物超所值啊。喔對了，順便提一下你們所謂的『優質』品質，同一個箱子裡的 HushMix 手持攪拌機聽起來簡直像一台水泥預拌車，完全不像你們網站上宣稱的那麼『安靜』。幫個忙把這兩個問題都給我處理好，否則下次我就直接去寫差評了，謝謝",
    context:
      "客戶用反諷語氣描述同一訂單內兩個商品問題：快煮壺不加熱、手持攪拌機噪音過大。",
    purpose:
      "測試 Agent 是否能理解諷刺語氣、多商品問題與差評威脅，避免把主旨誤判為正面稱讚。",
    expectedResult:
      "預期應辨識為品質投訴與升級風險案例，回覆需分別處理 BrewWell 與 HushMix 的問題。",
  },
  {
    from: "tonysk@example.com",
    subject: "ZenFlow Yoga Mat 的保固相關問題",
    body: "請問 ZenFlow 瑜珈墊的保固期是多久？我目前正在考慮購買...",
    context: "潛在客戶在購買前詢問 ZenFlow 瑜珈墊的保固資訊。",
    purpose:
      "測試 Agent 是否能辨識售前知識型問題，而不是誤判為既有訂單或退款案件。",
    expectedResult:
      "預期應回覆保固相關資訊，必要時引導客戶查看產品頁或客服政策。",
  },
];

export default function ComposePanel({ onSend, displayResult }) {
  const [from, setFrom] = useState("customer@example.com");
  const [subject, setSubject] = useState("測試支援請求");
  const [body, setBody] = useState("您好，我需要協助處理我最近的訂單。");
  const [loading, setLoading] = useState(false); // 控制按鈕 disable 與 spinner 顯示
  const [networkError, setNetworkError] = useState(null); // 完全無法連線時才顯示的錯誤橫幅
  const [selectedExample, setSelectedExample] = useState(null);

  function applyExample(example) {
    setFrom(example.from);
    setSubject(example.subject);
    setBody(example.body);
    setNetworkError(null);
    setSelectedExample(example);
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

        {/* 添加測試範例 */}
        <aside className="examples-panel glass" aria-label="測試範例">
          {/* 左欄：範例清單 */}
          <div className="examples-list-col">
            <div className="examples-header">
              <h3>測試範例</h3>
              <span>{TEST_EXAMPLES.length}</span>
            </div>
            <div className="example-list">
              {TEST_EXAMPLES.map((example, index) => (
                <button
                  key={example.from}
                  type="button"
                  className={`example-item ${
                    selectedExample?.from === example.from ? "active" : ""
                  }`}
                  onClick={() => applyExample(example)}
                  aria-pressed={selectedExample?.from === example.from}
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
          </div>

          {/* 右欄：案例細節 */}
          <div className="examples-detail-col">
            {selectedExample ? (
              <div key={selectedExample.from} className="example-detail">
                <div className="detail-title">
                  <span className="detail-label">案例說明</span>
                  <h4>{selectedExample.subject}</h4>
                  <p className="detail-sender">{selectedExample.from}</p>
                </div>
                <div className="detail-sections">
                  <div className="detail-section section-context">
                    <span className="detail-chip chip-context">情境</span>
                    <p>{selectedExample.context}</p>
                  </div>
                  <div className="detail-section section-purpose">
                    <span className="detail-chip chip-purpose">測試目的</span>
                    <p>{selectedExample.purpose}</p>
                  </div>
                  <div className="detail-section section-expected">
                    <span className="detail-chip chip-expected">預期結果</span>
                    <p>{selectedExample.expectedResult}</p>
                  </div>
                </div>
              </div>
            ) : (
              <div className="example-detail-empty">
                <div className="detail-empty-icon">←</div>
                <p>
                  點選左側範例
                  <br />
                  查看測試說明
                </p>
              </div>
            )}
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
