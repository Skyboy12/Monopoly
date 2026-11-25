import { useEffect } from 'react'
import { Dices, Snowflake, Zap } from 'lucide-react'
import Logo from '../Logo'
import { BOARD_TILE_META, CORNER_INDICES } from '../../data/gameData'

function positionToGrid(index) {
  if (index === 0) return { row: 11, col: 11 }
  if (index < 10) return { row: 11, col: 11 - index }
  if (index === 10) return { row: 11, col: 1 }
  if (index < 20) return { row: 11 - (index - 10), col: 1 }
  if (index === 20) return { row: 1, col: 1 }
  if (index < 30) return { row: 1, col: 1 + (index - 20) }
  if (index === 30) return { row: 1, col: 11 }
  return { row: 1 + (index - 30), col: 11 }
}

export default function Board({
  tiles = [],
  players = [],
  currentPlayerId = '',
  dice = null,
  canRoll = false,
  rolling = false,
  isInJail = false,
  turnsInJail = 0,
  onRoll,
  isMyTurn = false,
  canBuy = false,
  buyPrice = 0,
  onBuy,
  canUpgrade = false,
  upgradeCost = 50,
  onUpgrade,
  canPayBail = false,
  onPayBail,
  canEndTurn = false,
  onEndTurn,
  botControl = false,
  roomStatus = 'WAITING',
  phase = 'AWAITING_ROLL',
  statusHeadline = 'Đang đồng bộ trạng thái...',
  activeEvents = [],
  rentMultiplier = 1,
}) {
  useEffect(() => {
    const handleKeyDown = (event) => {
      if (event.code !== 'Space') return
      const tag = event.target?.tagName
      if (tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT' || event.target?.isContentEditable) {
        return
      }
      if (canRoll && !rolling) {
        event.preventDefault()
        onRoll?.()
      }
    }
    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [canRoll, rolling, onRoll])

  const mergedTiles = BOARD_TILE_META.map((meta) => {
    const serverTile = tiles.find((t) => t.index === meta.index)
    return {
      ...meta,
      ...(serverTile || {}),
      ownerId: serverTile?.ownerId || null,
      frozen: Boolean(serverTile?.frozen),
    }
  })

  const playerColorMap = new Map(players.map((player) => [player.id, player.color]))
  const d1 = dice?.dice1 ?? '-'
  const d2 = dice?.dice2 ?? '-'
  const totalText = dice
    ? `Tổng: ${dice.total}${dice.dice1 === dice.dice2 ? ' • Gieo đôi!' : ''}`
    : roomStatus === 'WAITING'
      ? 'Đang chờ bắt đầu ván'
      : 'Chờ gieo xúc xắc'

  return (
    <div className="board-frame">
      <div className="board-surface">
        <div className="monopoly-grid">
          {mergedTiles.map((tile) => {
            const position = positionToGrid(tile.index)
            const playersHere = players.filter(
              (player) => player.status !== 'BANKRUPT' && player.position === tile.index,
            )
            const corner = CORNER_INDICES.has(tile.index)
            const ownerColor = tile.ownerId ? playerColorMap.get(tile.ownerId) : null
            const isStreet = tile.type === 'STREET' && tile.color
            const isActiveHere = playersHere.some((player) => player.id === currentPlayerId)
            const edgeSide =
              position.row === 11
                ? 'bottom'
                : position.row === 1
                  ? 'top'
                  : position.col === 1
                    ? 'left'
                    : 'right'
            const edgeClass = corner ? '' : `tile-edge-${edgeSide}`
            const hasPawns = playersHere.length > 0
            const isChance = tile.type === 'CHANCE'
            const isChest = tile.type === 'COMMUNITY_CHEST'
            const houseCount = Number(tile.houseCount ?? 0)
            const hasHotel = Boolean(tile.hasHotel)

            return (
              <div
                className={`board-tile ${corner ? 'corner-tile' : ''} ${edgeClass} ${hasPawns ? 'has-pawns' : ''} ${tile.frozen ? 'tile-frozen' : ''} ${isActiveHere ? 'tile-active' : ''} ${isChance ? 'tile-chance' : ''} ${isChest ? 'tile-chest' : ''}`}
                style={{ gridRow: position.row, gridColumn: position.col }}
                key={tile.index}
                title={`#${tile.index} ${tile.name}${tile.price > 0 ? ` ($${tile.price})` : ''}${hasHotel ? ' • Khách sạn' : houseCount > 0 ? ` • ${houseCount} nhà` : ''}`}
              >
                {isStreet && (
                  <div
                    className={`tile-color tile-color-${edgeSide}`}
                    style={{ background: tile.color }}
                  />
                )}
                {ownerColor && (
                  <span className="tile-owner" style={{ background: ownerColor }} />
                )}
                {hasHotel ? (
                  <span className="tile-building tile-hotel" title="Khách sạn">
                    KS
                  </span>
                ) : (
                  houseCount > 0 && (
                    <span className="tile-building tile-houses" title={`${houseCount} nhà`}>
                      {Array.from({ length: Math.min(4, houseCount) }).map((_, i) => (
                        <i key={i} />
                      ))}
                    </span>
                  )
                )}
                {tile.frozen && (
                  <span className="tile-frozen-badge" title="Ô đang đóng băng">
                    <Snowflake size={9} />
                  </span>
                )}
                <span className="tile-name">{tile.name}</span>
                {tile.sub && <small className="tile-sub">{tile.sub}</small>}
                {tile.price > 0 && <b className="tile-price">${tile.price}</b>}
                {playersHere.length > 0 && (
                  <div className="tile-pawns">
                    {playersHere.map((player) => (
                      <span style={{ background: player.color }} key={player.id} title={player.name}>
                        {player.avatar}
                      </span>
                    ))}
                  </div>
                )}
              </div>
            )
          })}

          <div className="board-center">
            <div className="center-brand">
              <Logo small />
            </div>
            <div className="event-banner">
              <div className="event-icon">
                <Zap size={17} fill="currentColor" />
              </div>
              <div>
                <small>
                  {activeEvents.length > 0
                    ? activeEvents.map((ev) => ev.name || ev.eventName || ev.id).join(' • ')
                    : roomStatus === 'PLAYING'
                      ? phase
                      : roomStatus}
                </small>
                <strong>{statusHeadline}</strong>
              </div>
              {rentMultiplier > 1 && (
                <span className="event-x">x{rentMultiplier} TIỀN THUÊ</span>
              )}
            </div>
            <div className="dice-zone">
              <div className="dice-cubes">
                <div className={`dice-cube ${rolling ? 'dice-rolling' : ''}`}>
                  <span>{d1}</span>
                </div>
                <div className={`dice-cube ${rolling ? 'dice-rolling' : ''}`}>
                  <span>{d2}</span>
                </div>
              </div>
              <div className="dice-total">{totalText}</div>
            </div>
            <div className="board-action">
              {isMyTurn && phase === 'AWAITING_DECISION' ? (
                <div className="board-quick-actions">
                  {canBuy && (
                    <button type="button" className="quick-btn quick-buy" onClick={onBuy}>
                      Mua đất (${buyPrice})
                    </button>
                  )}
                  {canUpgrade && (
                    <button type="button" className="quick-btn quick-upgrade" onClick={onUpgrade}>
                      Nâng cấp (${upgradeCost})
                    </button>
                  )}
                  {canEndTurn && (
                    <button type="button" className="quick-btn quick-end" onClick={onEndTurn}>
                      Kết thúc lượt
                    </button>
                  )}
                </div>
              ) : (
                <>
                  <button
                    type="button"
                    className="roll-main"
                    onClick={onRoll}
                    disabled={!canRoll || rolling}
                  >
                    <Dices size={20} />
                    {rolling
                      ? 'Đang tung...'
                      : canRoll
                        ? isInJail
                          ? 'Thử gieo đôi ra tù'
                          : 'Gieo xúc xắc'
                        : 'Chờ lượt'}
                  </button>
                  {isMyTurn && isInJail && canRoll && (canPayBail || canEndTurn) && (
                    <div className="board-quick-actions">
                      {canPayBail && (
                        <button type="button" className="quick-btn quick-bail" onClick={onPayBail}>
                          Bảo lãnh $50
                        </button>
                      )}
                      {canEndTurn && (
                        <button type="button" className="quick-btn quick-end" onClick={onEndTurn}>
                          Bỏ lượt
                        </button>
                      )}
                    </div>
                  )}
                </>
              )}
              <small>
                {botControl ? (
                  'Bot đang tiếp quản lượt...'
                ) : isMyTurn && phase === 'AWAITING_DECISION' ? (
                  'Chọn hành động nhanh bên trên hoặc ở bảng bên'
                ) : canRoll ? (
                  isInJail ? (
                    <>
                      Lượt tù {turnsInJail}/3 • Nhấn nút hoặc <kbd>Space</kbd> để thử gieo đôi
                    </>
                  ) : (
                    <>
                      Nhấn nút hoặc phím <kbd>Space</kbd> để tung
                    </>
                  )
                ) : (
                  'Điều khiển mở khóa khi tới lượt gieo xúc xắc của bạn'
                )}
              </small>
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}
