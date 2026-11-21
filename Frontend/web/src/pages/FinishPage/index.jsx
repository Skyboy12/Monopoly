import { useNavigate } from 'react-router-dom'
import { Crown, Building2, Coins, RotateCcw, LogOut, PackageOpen } from 'lucide-react'
import Logo from '../../components/Logo'
import { finishPlayersSeed as players } from '../../data/gameData'

export default function FinishPage() {
  const navigate = useNavigate()
  const winner = players.find((player) => player.rank === 1)
  const topThree = [players[1], winner, players[2]]

  return (
    <main className="finish-page victory-page">
      <div className="victory-glow" />
      <div className="victory-shell">
        <header className="victory-header"><Logo /><div><span>Ván đấu kết thúc</span><strong>MNP-2408 · Khu phố Hạ Long</strong></div></header>
        <section className="podium-section">
          <div className="victory-title"><span>NGƯỜI CHIẾN THẮNG</span><h1><Crown size={24}/> Hoàng Vũ</h1><p>Nhà tỷ phú số một khu phố!</p></div>
          <div className="podium">
            {topThree.map((player) => (
              <div className={`podium-player rank-${player.rank}`} key={player.id}>
                {player.rank === 1 && <Crown className="podium-crown" size={38}/>}<span className="podium-rank">Hạng {player.rank}</span>
                <div className="podium-avatar" style={{background:`linear-gradient(135deg,${player.color},#4c1d95)`}}>{player.avatar}</div>
                <h3>{player.name}</h3><strong>${player.cash.toLocaleString()}</strong><small><Building2 size={12}/>{player.properties} bất động sản</small>
              </div>
            ))}
          </div>
        </section>

        <section className="match-stats-card">
          <div className="stats-card-title"><div><Coins size={16}/><span>Thống kê trận đấu</span></div><b>12 vòng</b></div>
          <div className="stats-table" role="table">
            <div className="stats-row stats-head" role="row"><span>Người chơi</span><span>Vòng đi</span><span>Tiền mặt</span><span>Tài sản</span><span>Vật phẩm</span></div>
            {players.map((player) => (
              <div className="stats-row" role="row" key={player.id}><div className="stats-player"><span style={{background:player.color}}>{player.avatar}</span><b>{player.name}</b>{player.rank===1&&<em>Vô địch</em>}</div><span>{player.rounds}</span><span>${player.cash.toLocaleString()}</span><span>${player.assets.toLocaleString()}</span><span><PackageOpen size={13}/>{player.itemsUsed}</span></div>
            ))}
          </div>
        </section>
        <div className="finish-actions"><button className="secondary finish-secondary" onClick={() => navigate('/')}><LogOut size={17}/>Về sảnh chơi</button><button className="primary finish-primary" onClick={() => navigate('/game')}><RotateCcw size={17}/>Chơi lại ván mới</button></div>
      </div>
    </main>
  )
}
