import { useState } from "react";
import axios from "axios";

const API_BASE = "http://localhost:8080";

// 測試範例
const TEST_EXAMPLES = [
  {
    from: "priya.sharma@example.com",
    subject: "訂單號 4471 被收了兩次費用",
    body: "您好，我的訂單編號 4471 被重複扣款了。我發現信用卡上有兩筆 $199.99 的扣款紀錄，請協助退還重複扣款的金額，謝謝。",
    context:
      "寄件人 priya.sharma@example.com 為系統既有顧客（CUSTOMERS.ID = 3）。其所提及的訂單 4471 確實存在於 ORDERS 表（ORDERS.ID = 2），且 PAYMENTS 表中可查到兩筆 ORDER_ID = 2 的付款紀錄，足以佐證重複扣款屬實。",
    purpose:
      "驗證 Agent 面對明確提供訂單號的帳務投訴時，是否能主動呼叫 detect_duplicate_charges_by_order_number 工具，取得客觀的重複扣款證據，而非單憑客戶描述直接退款；並在確認 duplicateDetected: true 後，以正確的退款類型 DUPLICATE_CHARGE 與金額 $199.99 呼叫 issue_refund，最終透過 log_support_ticket 將完整處理脈絡寫入 SUPPORT_TICKETS。",
    expectedResult:
      "issue_refund 成功執行後，PAYMENTS 表中對應的付款紀錄狀態應被沖銷由 CAPTURED 更新為 REFUNDED；REFUNDS 表應新增一筆屬於 Priya（PAYMENT.ORDER_ID = 2）的退款紀錄，金額為 $199.99；最後 SUPPORT_TICKETS 表同步新增一筆本次互動的處理記錄。",
  },
  {
    from: "sarah.mitchell@example.com",
    subject: "What's wrong with you guys",
    body: "For the third time in a row, I received a blender with a cracked jug. I no more want to take this stress. Give me my money back.",
    context:
      "寄件人 sarah.mitchell@example.com 為系統既有顧客（CUSTOMERS.ID = 1），且已有三筆訂單紀錄（ORDERS.ID = 1、4、5），均為同款果汁機。前兩次因商品破損申請退貨，PAYMENTS 表中 ORDER_ID = 4 與 5 的付款紀錄狀態皆已更新為 REFUNDED；REFUNDS 與 SUPPORT_TICKETS 亦留有對應的歷史處理紀錄。本次來信為第三度收到破損商品。",
    purpose:
      "驗證 Agent 面對情緒激動、語氣強硬且夾雜非正式用語的投訴信時，是否仍能準確識別退款訴求；並測試其能否主動呼叫 check_warranty_by_order_number_and_sku 工具查詢商品保固狀態，在確認保固有效後以 REFUND_TYPE: WARRANTY 發起退款，而非要求客戶補充資訊或直接拒絕；最後驗證是否依據 CUSTOMERS.PREFERRED_LANGUAGE = EN，自動以英文撰寫回覆郵件。",
    expectedResult:
      "Agent 透過 check_warranty_by_order_number_and_sku 確認商品仍在保固期內後，對 ORDERS.ID = 1 發起退款：PAYMENTS 表中 ORDER_ID = 1 的付款紀錄狀態由 CAPTURED 沖銷為 REFUNDED；REFUNDS 表同步新增一筆退款紀錄，REFUND_TYPE 為 WARRANTY；SUPPORT_TICKETS 表 INSERT 一筆本次處理的完整記錄；最後因 CUSTOMERS.PREFERRED_LANGUAGE = EN，Agent 應以英文撰寫回覆郵件。",
  },
  {
    from: "rohan.verma@example.com",
    subject: "你們的服務可真好",
    body: "哇，這服務真是太驚人了。我訂單編號 #4502 的 HushMix 手持攪拌機聽起來簡直像一台水泥預拌車，——還真好意思說是物超所值啊。完全不像你們網站上宣稱的那麼『安靜』。幫個忙把這兩個問題都給我處理好，否則下次我就直接去寫差評了，謝謝",
    context:
      "寄件人 rohan.verma@example.com 為系統既有顧客（CUSTOMERS.ID = 4），來信以反諷語氣投訴訂單 4502（ORDERS.ID = 3）中兩件商品的品質問題：BrewWell 快煮壺使用僅一週即停止加熱，HushMix 手持攪拌機噪音遠超商品描述。信末附帶差評威脅，屬於需優先處理的升級風險案例。",
    purpose:
      "驗證 Agent 是否能穿透反諷語氣，識別 HushMix 噪音投訴而非將主旨誤判為正面稱讚；測試其是否依序呼叫 check_warranty_by_order_number_and_sku 與 detect_duplicate_charges_by_order_number，並在問題未確認為硬體故障前，以多元選項取代直接退款；最後驗證 log_support_ticket 是否記錄升級風險，並依 CUSTOMERS.PREFERRED_LANGUAGE 選擇回覆語言。",
    expectedResult:
      "Agent 識別反諷語氣後，正確鎖定 HushMix 手持攪拌機（SKU: HMX-50）的噪音投訴，確認商品仍在 12 個月保固期內（到期日 2027-07-04），並確認無重複扣款。由於問題尚未確認為硬體故障，Agent 未直接發起退款，而是提供遠端排查、退回檢測、退款申請等選項供客戶選擇；最後寫入 SUPPORT_TICKETS，並以中文或英文（ CUSTOMERS.PREFERRED_LANGUAGE ）撰寫回覆郵件。",
  },
  {
    from: "tonysk@example.com",
    subject: "ZenFlow Yoga Mat 的保固相關問題",
    body: "請問 ZenFlow 瑜珈墊的保固期是多久？我目前正在考慮購買...",
    context:
      "寄件人 tonysk@example.com 非系統既有顧客，ORDERS 表中無任何訂單紀錄。此為購買前的諮詢信，詢問 ZenFlow 瑜珈墊的保固條款，意在評估是否值得購入。",
    purpose:
      "驗證 Agent 是否能正確辨識售前諮詢與售後客訴的差異：面對 ORDERS 表中無訂單紀錄的潛在顧客，不應呼叫 check_warranty_by_order_number_and_sku、issue_refund 或 log_support_ticket 等售後工具，而應直接以產品保固政策知識回覆，必要時引導至官方產品頁面或銷售團隊。",
    expectedResult:
      "Agent 識別來信為售前知識型問題，不查詢 CUSTOMERS 或 ORDERS 表，亦不觸發任何退款流程；直接提供 ZenFlow 瑜珈墊的保固期資訊，並視情況引導客戶至產品頁面或進一步諮詢管道。",
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
