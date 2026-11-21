import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Dices, MapPin, Volume2, LogOut, Sparkles, Timer, Users, ScrollText, Home, Zap, Landmark, ArrowRightCircle, Backpack, Magnet, ShieldCheck, Rocket, Trophy, BookOpen, Gamepad2 } from 'lucide-react'
import Board, { tileData } from '../../components/Board'
import { playersSeed, powerUpsSeed } from '../../data/gameData'

export default function GameRoomPage() {
  const navigate = useNavigate()
  const [players, setPlayers] = useState(playersSeed)
  const [dice, setDice] = useState([3, 5])
  const [rolled, setRolled] = useState(false)
  const [turnTime, setTurnTime] = useState(42)
  const [log, setLog] = useState('Hoàng Vũ đang lựa chọn hành động')
  const [turnIndex, setTurnIndex] = useState(0)
  const [rolling, setRolling] = useState(false)
  const [toast, setToast] = useState('')

  useEffect(() => {
    const timer = setInterval(() => setTurnTime((time) => time > 0 ? time - 1 : 60), 1000)
    return () => clearInterval(timer)
  }, [])

  const roll = () => {
    if (rolled || rolling) return
    setRolling(true)
    window.setTimeout(() => {
      const first = Math.floor(Math.random() * 6) + 1
      const second = Math.floor(Math.random() * 6) + 1
      setDice([first, second])
      setRolled(true)
      setRolling(false)
      setPlayers((current) => current.map((player, index) => index === turnIndex
        ? { ...player, position: (player.position + first + second) % 40 }
        : player))
      setLog(`${players[turnIndex].name} gieo được ${first + second} (${first}+${second})`)
    }, 550)
  }

  const endTurn = () => {
    const next = (turnIndex + 1) % players.length
    setTurnIndex(next)
    setRolled(false)
    setTurnTime(49)
    setLog(`Đến lượt ${players[next].name} gieo xúc xắc!`)
  }

  const showToast = (message) => {
    setToast(message)
    window.setTimeout(() => setToast(''), 3200)
  }

  const current = players[turnIndex]
  const currentTile = tileData[current.position]

  return (
    <main className="party-game">
      <header className="party-topbar">
        <div className="party-brand"><div className="party-logo"><Dices size={20}/></div><div><div>MONOPOLY <b>PARTY</b></div><small>Tabletop Edition</small></div></div>
        <nav className="party-nav"><button className="active"><Gamepad2 size={14}/>Sảnh chơi</button><button><Trophy size={14}/>Bảng xếp hạng</button><button><BookOpen size={14}/>Hướng dẫn</button></nav>
        <div className="party-room-tools"><div className="room-pill"><b><i/>MNP-2408</b><span>•</span><span><MapPin size={13}/>Khu phố Hạ Long</span><em>32ms</em></div><button className="icon-button"><Volume2 size={16}/></button><button className="leave-button" onClick={() => navigate('/')}><LogOut size={15}/><span>Rời phòng</span></button></div>
      </header>

      <div className="party-layout">
        <aside className="left-panel">
          <section className="glass-card turn-card">
            <div className="card-title"><span><Sparkles size={14}/>Lượt hiện tại</span><b><Timer size={14}/>{turnTime}s</b></div>
            <div className="active-player"><div className="active-avatar" style={{background:`linear-gradient(135deg,${current.color},#7c3aed)`}}>{current.avatar}</div><div><h3>{current.name}<em>BẠN</em></h3><p><i/>Đang chờ gieo xúc xắc</p></div></div>
          </section>

          <section className="glass-card players-card"><div className="card-title"><span><Users size={14}/>Người chơi (3/4)</span><b>Vòng 4</b></div>
            <div className="players-list">{players.map((player,index)=><div className={`player-row ${index===turnIndex?'is-current':''}`} key={player.id}><div className="player-info"><div className="player-avatar" style={{background:player.color}}>{player.avatar}</div><div><h4>{player.name}{index===turnIndex&&<i/>}</h4><p><Home size={10}/>{tileData.filter(t=>t.owner===player.id).length} đất <b>•</b>{tileData[player.position]?.name}</p></div></div><strong>${player.cash.toLocaleString()}</strong></div>)}</div>
          </section>

          <section className="glass-card log-card"><div className="card-title"><span><ScrollText size={14}/>Nhật ký ván đấu</span></div><div className="game-logs"><p>Sự kiện Bão may mắn x2 đã kích hoạt!</p><p>Lan Nhi vừa đi qua Phú Quốc.</p><p>Minh An đã mua Nha Trang ($180).</p><p>{log}</p></div></section>
        </aside>

        <section className="board-stage">
          <Board players={players} currentPosition={current.position} onRoll={roll} dice={dice} rolled={rolled} rolling={rolling} onFinish={() => navigate('/finish')}/>
          <div className="status-pill"><div><i/><span>{log} • Đang ở ô <b>{currentTile?.name}</b></span></div><b>Bão may mắn x2 tiền thưởng</b></div>
        </section>

        <aside className="right-panel">
          <section className="glass-card action-card-panel"><div className="card-title"><span><Zap size={14}/>Hành động</span><b className="phase-badge">{rolled?'Action Phase':'Awaiting Roll'}</b></div>
            <button className="side-action roll-action" onClick={roll} disabled={rolled||rolling}><div><Dices size={20}/></div><span><strong>Gieo xúc xắc</strong><small>{rolling?'Đang tung...':'Nhấn để tung cặp xúc xắc'}</small></span></button>
            <button className={`side-action buy-action ${rolled?'enabled':''}`} disabled={!rolled}><div><Landmark size={20}/></div><span><strong>Mua bất động sản</strong><small>{rolled?'Có thể mua tại ô hiện tại':'Chưa thể mua ở ô này'}</small></span></button>
            {rolled&&<button className="end-turn" onClick={endTurn}><ArrowRightCircle size={16}/>Kết thúc lượt</button>}
          </section>

          <section className="glass-card inventory-card"><div className="card-title"><span><Backpack size={14}/>Vật phẩm</span><b>{powerUpsSeed.reduce((total, item) => total + item.count, 0)} / 6</b></div><div className="inventory-grid">
            {powerUpsSeed.map((item) => {
              const Icon = item.id === 'magnet' ? Magnet : item.id === 'shield' ? ShieldCheck : Rocket
              const message = item.id === 'magnet' ? 'Đá đổi: Kéo đối thủ gần nhất về ô hiện tại!' : item.id === 'shield' ? 'Bảo vệ: Miễn nhiễm tiền thuê trong 1 lượt!' : 'Tên lửa: Tiến thẳng thêm 5 ô!'
              return <button className={`power-${item.tone}`} onClick={() => showToast(message)} key={item.id}><i>{item.count}</i><Icon size={20}/><span>{item.name}</span></button>
            })}
          </div></section>
        </aside>
      </div>
      {toast&&<div className="game-toast">{toast}</div>}
    </main>
  )
}
