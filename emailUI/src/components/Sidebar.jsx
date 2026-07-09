// 用來取得寄件人 email 的前兩個字母，並轉成大寫；如果沒有 email，就顯示 ?。
function getInitials(email) {
  if (!email) return "?";
  return email.split("@")[0].slice(0, 2).toUpperCase();
}

// 用來格式化寄信時間，顯示相對時間，例如「剛剛」、「5 分鐘前」、「3 小時前」；如果超過一天，就顯示完整日期。
function formatRelativeTime(timestamp) {
  if (!timestamp) return "";
  const diff = Math.floor((Date.now() - new Date(timestamp)) / 1000);
  if (diff < 60) return "剛剛";
  if (diff < 3600) return `${Math.floor(diff / 60)} 分鐘前`;
  if (diff < 86400) return `${Math.floor(diff / 3600)} 小時前`;
  return new Date(timestamp).toLocaleDateString("zh-TW");
}

export default function Sidebar({ history, selected, onSelect }) {
  return (
    <aside className="sidebar">
      <div className="sidebar-header">
        <h3>寄件記錄</h3>
        {/* 1. 顯示歷史紀錄數量 */}
        {history.length > 0 && <span className="badge">{history.length}</span>}
      </div>

      {/* 2. 顯示歷史寄出紀錄 */}
      {history.length === 0 ? (
        <div className="sidebar-empty">
          <div className="empty-icon">✉</div>
          <p>尚無寄出郵件</p>
          <span>撰寫第一封種子信吧</span>
        </div>
      ) : (
        // 2-2. 顯示歷史紀錄列表，點擊後會呼叫 onSelect，並傳入該筆紀錄的 index
        <ul className="email-list">
          {history.map((item, i) => (
            <li
              key={`${item.timestamp}-${i}`} // 這裡用 timestamp 加上 index i 組合，避免 key 重複
              className={`email-item ${selected === i ? "active" : ""} ${item.status}`} // 根據 item.status 加上不同的 class，方便 CSS 樣式化
              /**
               *  - email-item：基本樣式
               *  - active：目前被選中的樣式
               *  - sent：寄送成功的樣式
               *  - failed：寄送失敗的樣式
               */
              onClick={() => onSelect(i)}
            >
              {/* 2-3. 顯示寄件人縮寫、寄件人、主旨、寄信時間 */}
              <div className={`avatar avatar-${item.status}`}>
                {/* email 的前兩個字母 */}
                {getInitials(item.from)}
              </div>
              <div className="email-meta">
                <div className="email-from">{item.from || "未知寄件人"}</div>
                <div className="email-subject">
                  {item.subject || "(無主旨)"}
                </div>
              </div>
              <div className="email-time">
                {/* 顯示相對時間 */}
                {formatRelativeTime(item.timestamp)}
              </div>
            </li>
          ))}
        </ul>
      )}
    </aside>
  );
}
