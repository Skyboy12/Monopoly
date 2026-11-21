import { useEffect, useState } from 'react'
import './App.css'

const tileNames = [
  'START','Hải Hòa','Cộng đồng','Sơn Tùng','Thuế','Ga Hà Nội','Nha Trang','May mắn','Đà Nẵng','Huế','Tạm giam','Hà Giang','Điện','Phú Quốc','Mộc Châu','Ga Sài Gòn','Tây Nguyên','Cộng đồng','Đà Lạt','Hội An','Đỗ xe','Bình Dương','May mắn','Cần Thơ','Vũng Tàu','Ga Đà Nẵng','Biên Hòa','Long An','Nước','Cà Mau','Vào tù','Hạ Long','Sapa','Cộng đồng','Bãi Cháy','Ga Huế','May mắn','TP HCM','Thuế','Cần Giờ'
]

const tileColors = ['#f5c94a','#ef6b75','#63b7ff','#ef6b75','#f5f3e8','#6ca0d8','#63b7ff','#f5f3e8','#ef9a3a','#ef9a3a','#f5c94a','#63c6a5','#b5dcf2','#63c6a5','#63c6a5','#6ca0d8','#9a83d9','#f5f3e8','#9a83d9','#9a83d9','#f5c94a','#ef9a3a','#f5f3e8','#ef9a3a','#ef9a3a','#6ca0d8','#f0c44d','#f0c44d','#b5dcf2','#f0c44d','#ef6b75','#52b788','#52b788','#f5f3e8','#52b788','#6ca0d8','#f5f3e8','#d94b55','#f5f3e8','#d94b55']

const playersSeed = [
  { id: 1, name: 'Hoàng Vũ', avatar: 'H', color: '#ff5d73', money: 1380, position: 7, bot: false },
  { id: 2, name: 'Minh An', avatar: 'M', color: '#5a7cff', money: 1120, position: 12, bot: false },
  { id: 3, name: 'Lan Nhi', avatar: 'L', color: '#38c58a', money: 960, position: 4, bot: true },
]

function Logo({ small = false }) {
  return <div className={`brand ${small ? 'brand-small' : ''}`}><span className="brand-dice">◆</span><span>MONOPOLY</span><b>PARTY</b></div>
}

function AuthScreen({ onEnter }) {
  const [tab, setTab] = useState('login')
  const [name, setName] = useState('hoangvu')
  const [password, setPassword] = useState('monopoly')
  return <main className="auth-page">
    <div className="auth-decoration deco-a">★</div><div className="auth-decoration deco-b">●</div>
    <section className="auth-card">
      <Logo />
      <h1>{tab === 'login' ? 'Chào mừng trở lại!' : 'Tạo tài khoản'}</h1>
      <p className="muted">Sẵn sàng đốt cháy vòng quay may mắn?</p>
      <div className="auth-tabs"><button className={tab === 'login' ? 'active' : ''} onClick={() => setTab('login')}>Đăng nhập</button><button className={tab === 'register' ? 'active' : ''} onClick={() => setTab('register')}>Đăng ký</button></div>
      {tab === 'register' && <label>Tên hiển thị<input value={name} onChange={e => setName(e.target.value)} /></label>}
      <label>{tab === 'register' ? 'Tên đăng nhập' : 'Tên đăng nhập hoặc email'}<input value={name} onChange={e => setName(e.target.value)} /></label>
      <label>Mật khẩu<div className="password-wrap"><input type="password" value={password} onChange={e => setPassword(e.target.value)} /><span>◉</span></div></label>
      {tab === 'login' && <div className="auth-options"><label className="check"><input type="checkbox" defaultChecked /> Ghi nhớ đăng nhập</label><button className="link">Quên mật khẩu?</button></div>}
      <button className="primary wide" onClick={onEnter}>{tab === 'login' ? 'Vào chơi ngay' : 'Tạo tài khoản'} <span>→</span></button>
      <div className="auth-divider"><span>hoặc</span></div>
      <button className="google" onClick={onEnter}><b>G</b> Tiếp tục với Google</button>
    </section>
  </main>
}

function Lobby({ onJoin, onCreate }) {
  const rooms = [
    { id: 'MNP-2408', host: 'LanNhi', map: 'Khu phố Hạ Long', players: 3, mode: 'Party', status: 'Đang chờ' },
    { id: 'MNP-1182', host: 'Tuấn Anh', map: 'Cổ tích', players: 2, mode: 'Classic', status: 'Đang chờ' },
    { id: 'MNP-7731', host: 'Minh Khang', map: 'Đường đua', players: 4, mode: 'Party', status: 'Đang chơi' },
  ]
  return <main className="lobby app-shell">
    <Topbar />
    <div className="lobby-layout">
      <aside className="profile-card">
        <div className="profile-cover"><Logo small /></div>
        <div className="profile-main"><div className="avatar large">H</div><div><h2>Hoàng Vũ</h2><p>Lv. 12 · Top 18%</p></div></div>
        <div className="stats"><div><span>ELO</span><b>1,248</b></div><div><span>Thắng</span><b>42</b></div><div><span>Chuỗi</span><b>5</b></div></div>
      </aside>
      <section className="rooms-section">
        <div className="page-heading"><div><p className="eyebrow">Sảnh chính</p><h1>Chọn phòng chơi</h1></div><button className="primary" onClick={onCreate}>＋ Tạo phòng</button></div>
        <div className="room-toolbar"><div className="tabs"><button className="active">Tất cả</button><button>Party</button><button>Classic</button></div><button className="filter">≡ Bộ lọc</button></div>
        <div className="room-list">
          {rooms.map((room, i) => <article className="room-row" key={room.id}>
            <div className={`room-art art-${i}`}><span>{room.map[0]}</span></div>
            <div className="room-info"><div><h3>{room.id}</h3><span className={room.status === 'Đang chơi' ? 'playing' : ''}>{room.status}</span></div><p>{room.map} · Chủ phòng: {room.host}</p></div>
            <div className="mode-badge">{room.mode}</div>
            <div className="seat-count"><b>{room.players}</b>/4 <span>♟</span></div>
            <button className="join-btn" disabled={room.players === 4 || room.status === 'Đang chơi'} onClick={() => onJoin(room)}>{room.status === 'Đang chơi' ? 'Xem' : 'Tham gia'}</button>
          </article>)}
        </div>
      </section>
    </div>
  </main>
}

function Topbar({ inGame = false }) {
  return <header className="topbar"><Logo small /><nav><button className="active">Sảnh chơi</button><button>Bảng xếp hạng</button><button>Hướng dẫn</button></nav><div className="top-actions"><button className="bell">♢<i /></button><div className="avatar mini">H</div>{!inGame && <button className="menu">•••</button>}</div></header>
}

function positionToGrid(index) {
  if (index <= 10) return { c: 11 - index, r: 11 }
  if (index <= 20) return { c: 1, r: 21 - index }
  if (index <= 30) return { c: index - 19, r: 1 }
  return { c: 11, r: 31 - index }
}

function Board({ players, currentPosition, onRoll, dice, rolled, onFinish }) {
  return <div className="board">
    {tileNames.map((name, i) => {
      const pos = positionToGrid(i)
      return <div className={`tile ${i === currentPosition ? 'current-tile' : ''}`} style={{gridColumn: pos.c, gridRow: pos.r, '--tile-color': tileColors[i]}} key={name + i}>
        {i === 0 && <span className="start-mark">GO</span>}
        {i === 10 && <span className="jail-mark">🚔</span>}
        {i === 20 && <span className="park-mark">P</span>}
        {i === 30 && <span className="jail-mark">🚓</span>}
        {(i === 5 || i === 15 || i === 25 || i === 35) && <span className="rail-mark">🚂</span>}
        <div className="tile-name">{name}</div>
        {i === currentPosition && <div className="tile-pawns">{players.filter(p => p.position === i).map(p => <span style={{background:p.color}} key={p.id}>{p.avatar}</span>)}</div>}
      </div>
    })}
    <div className="board-center">
      <div className="center-logo"><Logo small /></div>
      <div className="event-card"><span>⚡</span><div><small>SỰ KIỆN ĐANG BẬT</small><b>Bão may mắn</b></div><strong>×2</strong></div>
      <div className="dice-area"><div className="dice dice-a" data-value={dice[0] || 3}>{dice[0] || 3}</div><div className="dice dice-b" data-value={dice[1] || 5}>{dice[1] || 5}</div></div>
      <button className="roll-button" onClick={onRoll}>{rolled ? 'Tiếp tục' : 'Gieo xúc xắc'}</button>
      <button className="finish-demo" onClick={onFinish}>Xem kết quả</button>
    </div>
  </div>
}

function GameRoom({ onLeave, onFinish }) {
  const [players, setPlayers] = useState(playersSeed)
  const [dice, setDice] = useState([3, 5])
  const [rolled, setRolled] = useState(false)
  const [turnTime, setTurnTime] = useState(42)
  const [log, setLog] = useState('Hoàng Vũ đang lựa chọn hành động')

  useEffect(() => { const timer = setInterval(() => setTurnTime(t => t > 0 ? t - 1 : 60), 1000); return () => clearInterval(timer) }, [])

  const roll = () => {
    if (!rolled) {
      const d1 = Math.floor(Math.random()*6)+1, d2 = Math.floor(Math.random()*6)+1
      setDice([d1,d2]); setRolled(true)
      setPlayers(ps => ps.map((p,i) => i === 0 ? {...p, position: (p.position+d1+d2)%40} : p))
      setLog(`Hoàng Vũ gieo được ${d1 + d2} (${d1}+${d2})`)
    } else { setRolled(false); setTurnTime(60); setLog('Đến lượt Minh An') }
  }

  const current = players[0]
  return <main className="game app-shell">
    <Topbar inGame />
    <div className="game-layout">
      <aside className="player-panel">
        <div className="panel-heading"><span>Lượt hiện tại</span><div className="turn-time"><b>{turnTime}</b><small>giây</small></div></div>
        <div className="current-player"><div className="avatar large" style={{background: current.color}}>{current.avatar}</div><div><h3>{current.name}</h3><p><span className="online-dot"/> Đang hoạt động</p></div></div>
        <div className="player-cards">
          {players.map((p,i) => <div className={`player-card ${i === 0 ? 'active' : ''}`} key={p.id}><div className="avatar" style={{background:p.color}}>{p.avatar}</div><div className="player-meta"><b>{p.name}</b><span>📍 Ô {p.position} · {p.bot ? 'BOT' : 'Online'}</span></div><strong>${p.money}</strong></div>)}
        </div>
        <button className="leave-btn" onClick={onLeave}>← Rời phòng</button>
      </aside>
      <section className="game-main">
        <div className="game-heading"><div><p className="eyebrow">MNP-2408 · PARTY MODE</p><h1>Khu phố Hạ Long</h1></div><div className="connection"><span/> Máy chủ ổn định</div></div>
        <Board players={players} currentPosition={current.position} onRoll={roll} dice={dice} rolled={rolled} onFinish={onFinish} />
        <div className="game-log"><span>●</span>{log}</div>
      </section>
      <aside className="action-panel">
        <div className="action-title"><span>Hành động</span><span className="phase">AWAITING_ROLL</span></div>
        <button className="action-card roll-card" onClick={roll}><span className="action-icon">🎲</span><div><b>{rolled ? 'Kết thúc lượt' : 'Gieo xúc xắc'}</b><small>Nhấn để tung xúc xắc</small></div></button>
        <button className="action-card" disabled><span className="action-icon">🏠</span><div><b>Mua bất động sản</b><small>Chưa thể mua ở ô này</small></div></button>
        <div className="inventory"><div className="inventory-heading"><b>Vật phẩm</b><span>3/6</span></div><div className="items"><button><span>🧲</span><small>Đổi chỗ</small><b>2</b></button><button><span>🎭</span><small>Bẻ khóa</small><b>1</b></button><button><span>🚀</span><small>Siêu tốc</small><b>1</b></button></div></div>
      </aside>
    </div>
  </main>
}

function FinishScreen({ onAgain, onLobby }) {
  return <main className="finish-page"><div className="confetti c1">◆</div><div className="confetti c2">●</div><div className="finish-card"><Logo /><p className="winner-label">NGƯỜI CHIẾN THẮNG</p><div className="winner-crown">♛</div><div className="avatar champion">H</div><h1>Hoàng Vũ</h1><p className="finish-sub">Nhà tỷ phú số một khu phố!</p><div className="ranking"><div><span>2</span><div className="avatar rank-two">M</div><b>Minh An</b><small>$1,120</small></div><div className="first"><span>1</span><div className="avatar rank-one">H</div><b>Hoàng Vũ</b><small>$1,380</small></div><div><span>3</span><div className="avatar rank-three">L</div><b>Lan Nhi</b><small>$960</small></div></div><div className="finish-actions"><button className="secondary" onClick={onLobby}>Về sảnh</button><button className="primary" onClick={onAgain}>Chơi lại</button></div></div></main>
}

function App() {
  const [screen, setScreen] = useState('auth')
  const [createModal, setCreateModal] = useState(false)
  const enterLobby = () => setScreen('lobby')
  const createRoom = () => setCreateModal(true)
  return <>
    {screen === 'auth' && <AuthScreen onEnter={enterLobby} />}
    {screen === 'lobby' && <Lobby onJoin={() => setScreen('room')} onCreate={createRoom} />}
    {screen === 'room' && <GameRoom onLeave={() => setScreen('lobby')} onFinish={() => setScreen('finish')} />}
    {screen === 'finish' && <FinishScreen onAgain={() => setScreen('room')} onLobby={() => setScreen('lobby')} />}
    {createModal && <div className="modal-backdrop" onClick={() => setCreateModal(false)}><div className="create-modal" onClick={e => e.stopPropagation()}><button className="modal-close" onClick={() => setCreateModal(false)}>×</button><p className="eyebrow">Tạo phòng mới</p><h2>Thiết lập ván đấu</h2><label>Tên phòng<input defaultValue="Party của Hoàng Vũ" /></label><div className="modal-grid"><label>Chế độ chơi<select defaultValue="Party"><option>Party</option><option>Classic</option></select></label><label>Số người<select defaultValue="4"><option>2</option><option>3</option><option>4</option></select></label></div><button className="primary wide" onClick={() => {setCreateModal(false); setScreen('room')}}>Tạo & vào phòng</button></div></div>}
  </>
}

export default App
