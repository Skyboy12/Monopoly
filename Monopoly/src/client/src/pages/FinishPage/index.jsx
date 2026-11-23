import { useLocation, useNavigate } from 'react-router-dom'
import { Crown, Building2, Coins, LogOut, Trophy } from 'lucide-react'
import Logo from '../../components/Logo'
import { PLAYER_COLORS } from '../../data/gameData'

function computeStandings(players, tiles) {
  const tilePriceByOwner = {}
  if (Array.isArray(tiles)) {
    tiles.forEach((tile) => {
      if (tile.ownerId) {
        tilePriceByOwner[tile.ownerId] = (tilePriceByOwner[tile.ownerId] || 0) + (tile.price || 0)
      }
    })
  }

  return [...players]
    .map((player, index) => {
      const cash = Number(player.money ?? 0)
      const assetValue = tilePriceByOwner[player.id] ?? 0
      const propertyCount =
        Array.isArray(player.ownedTiles)
          ? player.ownedTiles.length
          : Number(player.ownedCount ?? 0)
      const netWorth = cash + assetValue
      const isBankrupt = player.status === 'BANKRUPT'
      return {
        id: player.id,
        name: player.name || player.id,
        avatar: (player.name || player.id || '?').charAt(0).toUpperCase(),
        color: player.color || PLAYER_COLORS[index % PLAYER_COLORS.length],
        status: player.status || 'ACTIVE',
        isBankrupt,
        cash,
        assetValue,
        propertyCount,
        netWorth,
      }
    })
    .sort((a, b) => {
      if (a.isBankrupt !== b.isBankrupt) return a.isBankrupt ? 1 : -1
      if (b.netWorth !== a.netWorth) return b.netWorth - a.netWorth
      return b.cash - a.cash
    })
    .map((player, index) => ({ ...player, rank: index + 1 }))
}

export default function FinishPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const state = location.state || {}
  const rawPlayers = Array.isArray(state.players) ? state.players : []

  if (rawPlayers.length === 0) {
    return (
      <main className="finish-page victory-page">
        <div className="victory-glow" />
        <div className="victory-shell">
          <header className="victory-header">
            <Logo small />
            <div>
              <span>Trạng thái trận đấu</span>
              <strong>Không có dữ liệu ván đấu</strong>
            </div>
          </header>
          <section className="podium-section empty-finish-card">
            <Trophy size={42} className="empty-finish-icon" />
            <h1>Chưa có kết quả trận đấu</h1>
            <p className="muted">
              Hãy tham gia hoặc hoàn thành một ván đấu để xem bảng xếp hạng tổng kết.
            </p>
            <div className="finish-actions finish-actions-center">
              <button type="button" className="primary finish-primary" onClick={() => navigate('/')}>
                <LogOut size={17} />
                Về sảnh chơi
              </button>
            </div>
          </section>
        </div>
      </main>
    )
  }

  const standings = computeStandings(rawPlayers, state.tiles)
  const winner = standings[0]
  const podiumOrder = [standings[1], standings[0], standings[2]].filter(Boolean)

  return (
    <main className="finish-page victory-page">
      <div className="victory-glow" />
      <div className="victory-shell">
        <header className="victory-header">
          <Logo small />
          <div>
            <span>Ván đấu kết thúc</span>
            <strong>
              {state.roomId || 'Phòng chơi'} · {state.roomName || 'Monopoly Party'}
            </strong>
          </div>
        </header>

        <section className="podium-section">
          <div className="victory-title">
            <span>NGƯỜI CHIẾN THẮNG</span>
            <h1>
              <Crown size={24} /> {winner.name}
            </h1>
            <p>Tổng tài sản ${winner.netWorth.toLocaleString()}</p>
          </div>
          <div className="podium">
            {podiumOrder.map((player) => (
              <div className={`podium-player rank-${player.rank}`} key={player.id}>
                {player.rank === 1 && <Crown className="podium-crown" size={38} />}
                <span className="podium-rank">Hạng {player.rank}</span>
                <div
                  className="podium-avatar"
                  style={{ background: `linear-gradient(135deg,${player.color},#4c1d95)` }}
                >
                  {player.avatar}
                </div>
                <h3>{player.name}</h3>
                <strong>${player.netWorth.toLocaleString()}</strong>
                <small>
                  <Building2 size={12} />
                  {player.propertyCount} bất động sản
                </small>
              </div>
            ))}
          </div>
        </section>

        <section className="match-stats-card">
          <div className="stats-card-title">
            <div>
              <Coins size={16} />
              <span>Thống kê trận đấu</span>
            </div>
            <b>{state.turnId ? `${state.turnId} lượt` : `${standings.length} người chơi`}</b>
          </div>
          <div className="stats-table" role="table">
            <div className="stats-row stats-head" role="row">
              <span>Người chơi</span>
              <span>Trạng thái</span>
              <span>Tiền mặt</span>
              <span>Bất động sản</span>
              <span>Tổng giá trị</span>
            </div>
            {standings.map((player) => (
              <div className="stats-row" role="row" key={player.id}>
                <div className="stats-player">
                  <span style={{ background: player.color }}>{player.avatar}</span>
                  <b>{player.name}</b>
                  {player.rank === 1 && <em>Vô địch</em>}
                </div>
                <span>{player.isBankrupt ? 'Phá sản' : 'Hoạt động'}</span>
                <span>${player.cash.toLocaleString()}</span>
                <span>{player.propertyCount} ô (${player.assetValue.toLocaleString()})</span>
                <span>${player.netWorth.toLocaleString()}</span>
              </div>
            ))}
          </div>
        </section>

        <div className="finish-actions">
          <button
            type="button"
            className="primary finish-primary"
            onClick={() => navigate('/')}
          >
            <LogOut size={17} />
            Về sảnh chơi
          </button>
        </div>
      </div>
    </main>
  )
}
