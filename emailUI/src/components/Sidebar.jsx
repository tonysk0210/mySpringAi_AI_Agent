function getInitials(email) {
  if (!email) return '?'
  return email.split('@')[0].slice(0, 2).toUpperCase()
}

function formatRelativeTime(timestamp) {
  if (!timestamp) return ''
  const diff = Math.floor((Date.now() - new Date(timestamp)) / 1000)
  if (diff < 60) return '剛剛'
  if (diff < 3600) return `${Math.floor(diff / 60)} 分鐘前`
  if (diff < 86400) return `${Math.floor(diff / 3600)} 小時前`
  return new Date(timestamp).toLocaleDateString('zh-TW')
}

export default function Sidebar({ history, selected, onSelect }) {
  return (
    <aside className="sidebar">
      <div className="sidebar-header">
        <h3>寄件記錄</h3>
        {history.length > 0 && (
          <span className="badge">{history.length}</span>
        )}
      </div>

      {history.length === 0 ? (
        <div className="sidebar-empty">
          <div className="empty-icon">✉</div>
          <p>尚無郵件</p>
          <span>撰寫第一封種子信吧</span>
        </div>
      ) : (
        <ul className="email-list">
          {history.map((item, i) => (
            <li
              key={`${item.timestamp}-${i}`}
              className={`email-item ${selected === i ? 'active' : ''} ${item.status}`}
              onClick={() => onSelect(i)}
            >
              <div className={`avatar avatar-${item.status}`}>
                {getInitials(item.from)}
              </div>
              <div className="email-meta">
                <div className="email-from">{item.from || '未知寄件人'}</div>
                <div className="email-subject">{item.subject || '(無主旨)'}</div>
              </div>
              <div className="email-time">{formatRelativeTime(item.timestamp)}</div>
            </li>
          ))}
        </ul>
      )}
    </aside>
  )
}
