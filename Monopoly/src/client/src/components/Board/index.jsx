import { useEffect, useRef, useState } from 'react'
import { Dices, Flame, Handshake, Shield, Snowflake, Sparkles, Zap } from 'lucide-react'
import Logo from '../Logo'
import BoardItemBar from '../BoardItemBar'
import TileInspector from '../TileInspector'
import { BOARD_TILE_META, CORNER_INDICES } from '../../data/gameData'
import { BAIL_AMOUNT, formatCompactMoney, formatMoney } from '../../utils/money'
import './board.css'

const HOP_INTERVAL_MS = 110
const TRAIL_DURATION_MS = 320
const TELEPORT_DURATION_MS = 480

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

function prefersReducedMotion() {
  if (typeof window === 'undefined' || !window.matchMedia) return false
  return window.matchMedia('(prefers-reduced-motion: reduce)').matches
}

export default function Board({
  tiles = [],
  players = [],
  currentPlayerId = '',
  myPlayerId = '',
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
  upgradeCost = 50_000,
  onUpgrade,
  upgradableTileIndices = [],
  onUpgradeTile,
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
  itemControls = null,
  itemFx = null,
  highlightedPlayerId = null,
  sellableTileIndices = [],
  forcedDiceValue = null,
  lastRollerName = '',
  actionBanner = null,
}) {
  const [visualPositions, setVisualPositions] = useState(() =>
    Object.fromEntries(players.map((p) => [p.id, p.position ?? 0])),
  )
  const [hoppingPlayers, setHoppingPlayers] = useState({})
  const [teleportingPlayers, setTeleportingPlayers] = useState({})
  const [trailTiles, setTrailTiles] = useState({})
  const [inspectedTileIndex, setInspectedTileIndex] = useState(null)

  const visualPosRef = useRef(Object.fromEntries(players.map((p) => [p.id, p.position ?? 0])))
  const prevTargetPosRef = useRef(Object.fromEntries(players.map((p) => [p.id, p.position ?? 0])))
  const hopTimersRef = useRef(new Map())
  const teleportTimersRef = useRef(new Map())
  const trailTimersRef = useRef(new Map())

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

  useEffect(() => {
    const hopTimers = hopTimersRef.current
    const teleportTimers = teleportTimersRef.current
    const trailTimers = trailTimersRef.current
    return () => {
      hopTimers.forEach((t) => clearTimeout(t))
      teleportTimers.forEach((t) => clearTimeout(t))
      trailTimers.forEach((t) => clearTimeout(t))
      hopTimers.clear()
      teleportTimers.clear()
      trailTimers.clear()
    }
  }, [])

  useEffect(() => {
    const reduced = prefersReducedMotion()

    players.forEach((player) => {
      const targetPos = player.position ?? 0
      const prevTarget = prevTargetPosRef.current[player.id]

      if (prevTarget === undefined) {
        prevTargetPosRef.current[player.id] = targetPos
        visualPosRef.current[player.id] = targetPos
        setTimeout(() => {
          setVisualPositions((prev) => ({ ...prev, [player.id]: targetPos }))
        }, 0)
        return
      }

      if (prevTarget === targetPos) return
      prevTargetPosRef.current[player.id] = targetPos

      const existingHop = hopTimersRef.current.get(player.id)
      if (existingHop) {
        clearTimeout(existingHop)
        hopTimersRef.current.delete(player.id)
      }

      const currentVis = visualPosRef.current[player.id] ?? prevTarget
      const cwDist = (targetPos - currentVis + 40) % 40

      if (cwDist === 0) return

      const isJailSend = Boolean(player.inJail) && targetPos === 10 && currentVis !== 10
      const isTeleport = reduced || isJailSend || cwDist > 14

      if (isTeleport) {
        visualPosRef.current[player.id] = targetPos
        setTimeout(() => {
          setVisualPositions((prev) => ({ ...prev, [player.id]: targetPos }))
          setHoppingPlayers((prev) => ({ ...prev, [player.id]: false }))
          if (!reduced) {
            setTeleportingPlayers((prev) => ({ ...prev, [player.id]: true }))
            const prevTp = teleportTimersRef.current.get(player.id)
            if (prevTp) clearTimeout(prevTp)
            const tpTimer = setTimeout(() => {
              setTeleportingPlayers((prev) => ({ ...prev, [player.id]: false }))
              teleportTimersRef.current.delete(player.id)
            }, TELEPORT_DURATION_MS)
            teleportTimersRef.current.set(player.id, tpTimer)
          }
        }, 0)
        return
      }

      const stepHop = (fromPos) => {
        const nextPos = (fromPos + 1) % 40
        visualPosRef.current[player.id] = nextPos
        setVisualPositions((prev) => ({ ...prev, [player.id]: nextPos }))
        setHoppingPlayers((prev) => ({ ...prev, [player.id]: true }))

        setTrailTiles((prev) => ({ ...prev, [fromPos]: true }))
        const existingTrail = trailTimersRef.current.get(fromPos)
        if (existingTrail) clearTimeout(existingTrail)
        const trTimer = setTimeout(() => {
          setTrailTiles((prev) => {
            const copy = { ...prev }
            delete copy[fromPos]
            return copy
          })
          trailTimersRef.current.delete(fromPos)
        }, TRAIL_DURATION_MS)
        trailTimersRef.current.set(fromPos, trTimer)

        if (nextPos === targetPos) {
          const endTimer = setTimeout(() => {
            setHoppingPlayers((prev) => ({ ...prev, [player.id]: false }))
            hopTimersRef.current.delete(player.id)
          }, HOP_INTERVAL_MS)
          hopTimersRef.current.set(player.id, endTimer)
        } else {
          const nextTimer = setTimeout(() => stepHop(nextPos), HOP_INTERVAL_MS)
          hopTimersRef.current.set(player.id, nextTimer)
        }
      }

      const startTimer = setTimeout(() => stepHop(currentVis), 0)
      hopTimersRef.current.set(player.id, startTimer)
    })
  }, [players])

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
  const playerMap = new Map(players.map((player, idx) => [player.id, { ...player, seat: idx + 1 }]))
  const myPlayer = players.find((p) => p.id === myPlayerId) || null
  const isTargeting = Boolean(itemControls?.pendingItem)
  const targetableSet = new Set(itemControls?.targetableTileIndices || [])
  const upgradableSet = new Set(upgradableTileIndices || [])
  const sellableSet = new Set(sellableTileIndices || [])

  const fxTileIndex = itemFx?.targetTileIndex ?? itemFx?.tileIndex ?? null
  const isShieldAuto = itemFx?.auto === true || itemFx?.auto === 'true'

  const d1 = dice?.dice1 ?? '-'
  const d2 = dice?.dice2 ?? '-'
  const totalText = dice
    ? `Tổng: ${dice.total}${dice.dice1 === dice.dice2 ? ' • Gieo đôi!' : ''}`
    : roomStatus === 'WAITING'
      ? 'Đang chờ bắt đầu ván'
      : 'Chờ gieo xúc xắc'

  const inspectedTile =
    inspectedTileIndex !== null
      ? mergedTiles.find((t) => t.index === inspectedTileIndex) || null
      : null

  const getPlayersAtTile = (tileIndex) =>
    players.filter(
      (player) =>
        player.status !== 'BANKRUPT' &&
        (visualPositions[player.id] ?? player.position ?? 0) === tileIndex,
    )

  return (
    <div className="board-frame">
      <div className="board-surface">
        <div className="monopoly-grid">
          {mergedTiles.map((tile) => {
            const position = positionToGrid(tile.index)
            const playersHere = getPlayersAtTile(tile.index)
            const corner = CORNER_INDICES.has(tile.index)
            const ownerInfo = tile.ownerId ? playerMap.get(tile.ownerId) : null
            const ownerColor = ownerInfo?.color || (tile.ownerId ? playerColorMap.get(tile.ownerId) : null)
            const ownerSeat = ownerInfo ? `P${ownerInfo.seat}` : null
            const isHighlightedOwner = Boolean(highlightedPlayerId && tile.ownerId === highlightedPlayerId)
            const isSellable = sellableSet.has(tile.index)
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
            const rentTable = Array.isArray(tile.rentTable) ? tile.rentTable : []
            let currentRent = 0
            if (ownerInfo && !tile.mortgaged && !tile.frozen && rentTable.length > 0) {
              if (tile.type === 'STREET') {
                const idx = hasHotel ? 4 : Math.min(3, Math.max(0, houseCount))
                currentRent = Number(rentTable[idx] ?? 0) * rentMultiplier
              } else if (tile.type === 'RAILROAD') {
                const ownedRr = mergedTiles.filter((t) => t.type === 'RAILROAD' && t.ownerId === ownerInfo.id).length
                currentRent = Number(rentTable[Math.max(0, Math.min(3, ownedRr - 1))] ?? 0) * rentMultiplier
              } else if (tile.type === 'UTILITY') {
                const ownedUt = mergedTiles.filter((t) => t.type === 'UTILITY' && t.ownerId === ownerInfo.id).length
                currentRent = Number(rentTable[Math.max(0, Math.min(1, ownedUt - 1))] ?? 0)
              }
            }
            const isTargetable = isTargeting && targetableSet.has(tile.index)
            const isTrail = Boolean(trailTiles[tile.index])

            const hasTileFx = itemFx && fxTileIndex === tile.index
            const tileFxClass = hasTileFx
              ? itemFx.itemId === 'DYNAMITE'
                ? 'tile-fx-dynamite'
                : itemFx.itemId === 'HOSTILE_TAKEOVER'
                  ? 'tile-fx-takeover'
                  : itemFx.itemId === 'RENT_SHIELD'
                    ? 'tile-fx-shield'
                    : ''
              : ''

            return (
              <div
                className={`board-tile ${corner ? 'corner-tile' : ''} ${edgeClass} ${hasPawns ? 'has-pawns' : ''} ${tile.frozen ? 'tile-frozen' : ''} ${isActiveHere ? 'tile-active' : ''} ${isChance ? 'tile-chance' : ''} ${isChest ? 'tile-chest' : ''} ${isTrail ? 'tile-trail' : ''} ${isTargetable ? 'tile-targetable' : ''} ${isHighlightedOwner ? 'tile-owner-highlight' : ''} ${isSellable ? 'tile-sellable' : ''} ${tileFxClass}`}
                style={{
                  gridRow: position.row,
                  gridColumn: position.col,
                  ...(ownerColor ? { boxShadow: `inset 0 0 0 2px ${ownerColor}` } : {}),
                }}
                key={tile.index}
                role="button"
                tabIndex={0}
                onClick={() => {
                  if (isTargetable) {
                    itemControls?.onTargetTile?.(tile.index)
                  } else if (!isTargeting) {
                    setInspectedTileIndex(tile.index)
                  }
                }}
                onKeyDown={(e) => {
                  if (e.key === 'Enter') {
                    e.preventDefault()
                    if (isTargetable) {
                      itemControls?.onTargetTile?.(tile.index)
                    } else if (!isTargeting) {
                      setInspectedTileIndex(tile.index)
                    }
                  }
                }}
                title={`#${tile.index} ${tile.name}${tile.price > 0 ? ` (${formatMoney(tile.price)})` : ''}${ownerInfo ? ` • Chủ: ${ownerInfo.name}` : ''}${currentRent > 0 ? ` • Thuê: ${formatMoney(currentRent)}` : ''}${hasHotel ? ' • Khách sạn' : houseCount > 0 ? ` • ${houseCount} nhà` : ''}`}
              >
                {isStreet && (
                  <div
                    className={`tile-color tile-color-${edgeSide}`}
                    style={{ background: tile.color }}
                  />
                )}
                {ownerColor && ownerSeat && (
                  <span className="tile-owner" style={{ background: ownerColor }}>
                    {ownerSeat}
                  </span>
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
                {tile.price > 0 && (
                  <b className="tile-price">{formatCompactMoney(tile.price)}</b>
                )}
                {hasTileFx && itemFx.itemId === 'DYNAMITE' && (
                  <span className="tile-fx-overlay fx-dynamite" aria-hidden="true">
                    <Flame size={18} />
                  </span>
                )}
                {hasTileFx && itemFx.itemId === 'HOSTILE_TAKEOVER' && (
                  <span className="tile-fx-overlay fx-takeover" aria-hidden="true">
                    <Handshake size={18} />
                  </span>
                )}
                {hasTileFx && itemFx.itemId === 'RENT_SHIELD' && (
                  <span className="tile-fx-overlay fx-shield" aria-hidden="true">
                    <Shield size={18} />
                  </span>
                )}
                {playersHere.length > 0 && (
                  <div className="tile-pawns">
                    {playersHere.map((player) => {
                      const isHop = Boolean(hoppingPlayers[player.id])
                      const isTp = Boolean(teleportingPlayers[player.id])
                      const isShieldPawn =
                        itemFx?.itemId === 'RENT_SHIELD' && player.id === itemFx.actorId
                      const isArrestPawn =
                        itemFx?.itemId === 'ARREST_WARRANT' && player.id === itemFx.targetPlayerId
                      const isSwapPawn =
                        itemFx?.itemId === 'FORTUNE_SWAP' &&
                        (player.id === itemFx.targetPlayerId || player.id === itemFx.actorId)

                      return (
                        <span
                          style={{ background: player.color }}
                          key={player.id}
                          title={player.name}
                          className={`${isHop ? 'pawn-hopping' : ''} ${isTp ? 'pawn-teleport' : ''} ${isShieldPawn ? 'pawn-fx-shield' : ''} ${isArrestPawn ? 'pawn-fx-arrest' : ''} ${isSwapPawn ? 'pawn-fx-swap' : ''}`}
                        >
                          {player.avatar}
                        </span>
                      )
                    })}
                  </div>
                )}
              </div>
            )
          })}

          <div className="board-center">
            {itemFx && (
              <div className="item-fx-banner" key={itemFx.key || itemFx.itemId} role="status">
                <Sparkles size={14} />
                <span>
                  <strong>{itemFx.actorName || 'Người chơi'}</strong> dùng{' '}
                  <strong>{itemFx.itemName || itemFx.itemId}</strong>
                </span>
                {isShieldAuto && <span className="item-fx-auto">Tự động</span>}
              </div>
            )}

            {actionBanner && (
              <div className={`action-fx-banner tone-${actionBanner.tone || 'info'}`} key={actionBanner.key} role="status">
                <span>{actionBanner.text}</span>
              </div>
            )}

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
            <div className={`dice-zone ${itemFx?.itemId === 'MAGIC_DICE' ? 'dice-fx-magic' : ''}`}>
              <div className="dice-cubes">
                <div className={`dice-cube ${rolling ? 'dice-rolling' : ''}`}>
                  <span>{d1}</span>
                </div>
                <div className={`dice-cube ${rolling ? 'dice-rolling' : ''}`}>
                  <span>{d2}</span>
                </div>
              </div>
              <div className="dice-total">
                {lastRollerName && dice ? `${lastRollerName}: ` : ''}
                {totalText}
              </div>
              {forcedDiceValue != null && (
                <div className="forced-dice-badge">Xúc xắc đã chọn: {forcedDiceValue}</div>
              )}
            </div>

            <BoardItemBar itemControls={itemControls} />

            <div className="board-action">
              {isMyTurn && phase === 'AWAITING_DECISION' ? (
                <div className="board-quick-actions">
                  {canBuy && (
                    <button type="button" className="quick-btn quick-buy" onClick={onBuy}>
                      Mua đất ({formatCompactMoney(buyPrice)})
                    </button>
                  )}
                  {canUpgrade && (
                    <button type="button" className="quick-btn quick-upgrade" onClick={onUpgrade}>
                      Nâng cấp ({formatCompactMoney(upgradeCost)})
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
                          Bảo lãnh {formatCompactMoney(BAIL_AMOUNT)}
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

      {inspectedTile && (
        <TileInspector
          tile={inspectedTile}
          tiles={mergedTiles}
          players={players}
          playersHere={getPlayersAtTile(inspectedTile.index)}
          myPlayer={myPlayer}
          rentMultiplier={rentMultiplier}
          canBuy={canBuy}
          onBuy={onBuy}
          canUpgradeThis={upgradableSet.has(inspectedTile.index)}
          onUpgradeTile={onUpgradeTile}
          isTargetable={isTargeting && targetableSet.has(inspectedTile.index)}
          onTargetTile={itemControls?.onTargetTile}
          onClose={() => setInspectedTileIndex(null)}
        />
      )}
    </div>
  )
}
