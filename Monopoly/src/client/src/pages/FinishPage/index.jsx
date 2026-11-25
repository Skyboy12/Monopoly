import { useEffect, useState } from 'react'
import { useLocation, useNavigate, useSearchParams } from 'react-router-dom'
import { Crown, Building2, Coins, LogOut, Trophy } from 'lucide-react'
import Logo from '../../components/Logo'
import { PLAYER_COLORS } from '../../data/gameData'
import api, { parseGamePayload } from '../../plugins/api'
import { formatMoney } from '../../utils/money'

function computeStandings(players, tiles) {
  const tilePriceByOwner = {}
  if (Array.isArray(tiles)) {
    tiles.forEach((tile) => {
      if (tile.ownerId) {
        const buildingCost = tile.buildingCost || 50000
        const buildingValue = tile.hasHotel
          ? 5 * buildingCost
          : (tile.houseCount || 0) * buildingCost
        tilePriceByOwner[tile.ownerId] =
          (tilePriceByOwner[tile.ownerId] || 0) + (tile.price || 0) + buildingValue
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

function readStoredMatch(roomIdFromUrl) {
  try {
    const raw = sessionStorage.getItem('lastFinishedMatch')
    if (!raw) return null
    const parsed = JSON.parse(raw)
    if (!parsed || !Array.isArray(parsed.players) || parsed.players.length === 0) return null
    if (roomIdFromUrl && parsed.roomId && parsed.roomId !== roomIdFromUrl) return null
    return parsed
  } catch {
    return null
  }
}

export default function FinishPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const [searchParams] = useSearchParams()
  const roomIdFromUrl = searchParams.get('roomId') || ''

  const [matchState, setMatchState] = useState(() => {
    if (Array.isArray(location.state?.players) && location.state.players.length > 0) {
      return location.state
    }
    return readStoredMatch(roomIdFromUrl) || {}
  })
  const [loadingMatch, setLoadingMatch] = useState(
    () =>
      !(Array.isArray(location.state?.players) && location.state.players.length > 0) &&
      !readStoredMatch(roomIdFromUrl) &&
      Boolean(roomIdFromUrl),
  )

  useEffect(() => {
    if (Array.isArray(matchState.players) && matchState.players.length > 0) return undefined
    if (!roomIdFromUrl) return undefined
    let cancelled = false
    api
      .get(`/api/v1/rooms/${encodeURIComponent(roomIdFromUrl)}`)
      .then((res) => {
        if (cancelled) return
        const payload = parseGamePayload(res.data)
        if (payload && Array.isArray(payload.players) && payload.players.length > 0) {
          const next = {
            roomId: payload.roomId || roomIdFromUrl,
            roomName: payload.roomName,
            players: payload.players,
            tiles: payload.tiles,
            turnId: payload.turnId,
          }
          setMatchState(next)
          try {
            sessionStorage.setItem('lastFinishedMatch', JSON.stringify(next))
          } catch {
            // Ignore storage errors
          }
        }
      })
      .catch(() => {
        // Keep empty state fallback
      })
      .finally(() => {
        if (!cancelled) setLoadingMatch(false)
      })
    return () => {
      cancelled = true
    }
  }, [roomIdFromUrl, matchState.players])

  const state = matchState
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
              <strong>{loadingMatch ? 'Đang tải kết quả...' : 'Không có dữ liệu ván đấu'}</strong>
            </div>
          </header>
          <section className="podium-section empty-finish-card">
            <Trophy size={42} className="empty-finish-icon" />
            <h1>{loadingMatch ? 'Đang tải bảng tổng kết...' : 'Chưa có kết quả trận đấu'}</h1>
            <p className="muted">
              {loadingMatch
                ? 'Vui lòng chờ trong giây lát để đồng bộ dữ liệu trận đấu.'
                : 'Hãy tham gia hoặc hoàn thành một ván đấu để xem bảng xếp hạng tổng kết.'}
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
            <p>Tổng tài sản {formatMoney(winner.netWorth)}</p>
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
                <strong>{formatMoney(player.netWorth)}</strong>
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
                <span>{formatMoney(player.cash)}</span>
                <span>{player.propertyCount} ô ({formatMoney(player.assetValue)})</span>
                <span>{formatMoney(player.netWorth)}</span>
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
