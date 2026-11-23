import { LogOut, Gamepad2, Star } from 'lucide-react'
import { useNavigate } from 'react-router-dom'
import Logo from '../Logo'

function readStoredUser() {
  try {
    return JSON.parse(localStorage.getItem('currentUser') || '{}')
  } catch {
    return {}
  }
}

export default function Topbar({ inGame = false, user = null, onLogout }) {
  const navigate = useNavigate()
  const storedUser = readStoredUser()
  const currentUser = user && (user.username || user.displayName) ? user : storedUser
  const displayName = currentUser.displayName || currentUser.username || 'Khách'
  const ratingPoints = currentUser.ratingPoints ?? 1000
  const avatarInitial = displayName.charAt(0).toUpperCase()

  const handleLogout = () => {
    localStorage.removeItem('accessToken')
    localStorage.removeItem('currentUser')
    sessionStorage.removeItem('activeRoomId')
    if (onLogout) {
      onLogout()
    } else {
      navigate('/login', { replace: true })
    }
  }

  return (
    <header className="topbar lobby-topbar">
      <Logo small />
      <nav>
        <button type="button" className="active" onClick={() => navigate('/')}>
          <Gamepad2 size={14} />
          Sảnh chơi
        </button>
      </nav>
      <div className="top-actions">
        <div className="wallet">
          <span>{displayName}</span>
          <strong>
            <Star size={11} style={{ marginRight: 4, verticalAlign: '-1px' }} />
            {Number(ratingPoints).toLocaleString()} ELO
          </strong>
        </div>
        <div className="avatar mini" title={displayName}>
          {avatarInitial}
        </div>
        {!inGame && (
          <button
            type="button"
            className="logout-button"
            aria-label="Đăng xuất"
            title="Đăng xuất"
            onClick={handleLogout}
          >
            <LogOut size={16} />
          </button>
        )}
      </div>
    </header>
  )
}
