import { Bell, LogOut, Trophy, BookOpen, Gamepad2 } from 'lucide-react'
import Logo from '../Logo'

export default function Topbar({ inGame = false, onLogout }) {
  return (
    <header className="topbar lobby-topbar">
      <Logo small />
      <nav>
        <button className="active"><Gamepad2 size={14}/>Sảnh chơi</button>
        <button><Trophy size={14}/>Bảng xếp hạng</button>
        <button><BookOpen size={14}/>Hướng dẫn</button>
      </nav>
      <div className="top-actions">
        <div className="wallet"><span>Số dư</span><strong>$1,360</strong></div>
        <button className="bell" aria-label="Thông báo"><Bell size={17}/><i /></button>
        <div className="avatar mini">H</div>
        {!inGame && <button className="logout-button" aria-label="Đăng xuất" onClick={onLogout}><LogOut size={16}/></button>}
      </div>
    </header>
  )
}
