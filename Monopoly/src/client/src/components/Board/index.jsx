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
  onRoll,
  roomStatus = 'WAITING',
  phase = 'AWAITING_ROLL',
  statusHeadline = 'Đang đồng bộ trạng thái...',
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

            return (
              <div
                className={`board-tile ${corner ? 'corner-tile' : ''} ${tile.frozen ? 'tile-frozen' : ''} ${isActiveHere ? 'tile-active' : ''}`}
                style={{ gridRow: position.row, gridColumn: position.col }}
                key={tile.index}
                title={`#${tile.index} ${tile.name}${tile.price > 0 ? ` ($${tile.price})` : ''}`}
              >
                {isStreet && (
                  <div
                    className={`tile-color tile-color-${
                      position.row === 11
                        ? 'bottom'
                        : position.row === 1
                          ? 'top'
                          : position.col === 1
                            ? 'left'
                            : 'right'
                    }`}
                    style={{ background: tile.color }}
                  />
                )}
                {ownerColor && (
                  <span className="tile-owner" style={{ background: ownerColor }} />
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
                <small>{roomStatus === 'PLAYING' ? phase : roomStatus}</small>
                <strong>{statusHeadline}</strong>
              </div>
            </div>
            <div className="dice-zone">
              <div className={`dice-cube ${rolling ? 'dice-rolling' : ''}`}>
                <span>{d1}</span>
              </div>
              <div className={`dice-cube ${rolling ? 'dice-rolling' : ''}`}>
                <span>{d2}</span>
              </div>
              <div className="dice-total">{totalText}</div>
            </div>
            <div className="board-action">
              <button
                type="button"
                className="roll-main"
                onClick={onRoll}
                disabled={!canRoll || rolling}
              >
                <Dices size={20} />
                {rolling ? 'Đang tung...' : canRoll ? 'Gieo xúc xắc' : 'Chờ lượt'}
              </button>
              <small>
                {canRoll ? (
                  <>
                    Nhấn nút hoặc phím <kbd>Space</kbd> để tung
                  </>
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
