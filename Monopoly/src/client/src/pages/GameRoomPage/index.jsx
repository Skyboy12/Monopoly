import { useCallback, useEffect, useRef, useState } from 'react'
import { useLocation, useNavigate, useSearchParams } from 'react-router-dom'
import {
  Dices,
  MapPin,
  LogOut,
  Sparkles,
  Timer,
  Users,
  ScrollText,
  Home,
  Zap,
  Landmark,
  ArrowRightCircle,
  Backpack,
  ShieldCheck,
  Wand2,
  Flame,
  Gavel,
  Shuffle,
  Play,
  Copy,
  Wifi,
  WifiOff,
  KeyRound,
  RefreshCw,
  Building2,
} from 'lucide-react'
import Board from '../../components/Board'
import {
  BOARD_TILE_META,
  ITEM_UI_META,
  PLAYER_COLORS,
  PURCHASABLE_TYPES,
} from '../../data/gameData'
import api, { getApiError, parseGamePayload } from '../../plugins/api'
import { createGameSocket } from '../../plugins/gameSocket'
import { BAIL_AMOUNT, formatMoney } from '../../utils/money'

function readStoredUser() {
  try {
    return JSON.parse(localStorage.getItem('currentUser') || '{}')
  } catch {
    return {}
  }
}

function decoratePlayers(players = []) {
  return players.map((player, index) => ({
    ...player,
    seatNumber: index + 1,
    color: PLAYER_COLORS[index % PLAYER_COLORS.length],
    avatar: (player.name || player.id || '?').charAt(0).toUpperCase(),
  }))
}

function isItemPhaseCompatible(enginePhase, triggerPhase) {
  if (!triggerPhase) return false
  if (triggerPhase === 'PRE_ROLL') return enginePhase === 'AWAITING_ROLL'
  if (triggerPhase === 'PRE_ROLL_OR_DECISION') {
    return enginePhase === 'AWAITING_ROLL' || enginePhase === 'AWAITING_DECISION'
  }
  if (triggerPhase === 'ON_LAND') {
    return enginePhase === 'RESOLVING_ACTION' || enginePhase === 'AWAITING_DECISION'
  }
  if (triggerPhase === 'AWAITING_DECISION') return enginePhase === 'AWAITING_DECISION'
  return false
}

function ItemIcon({ itemId }) {
  switch (itemId) {
    case 'MAGIC_DICE':
      return <Wand2 size={18} />
    case 'RENT_SHIELD':
      return <ShieldCheck size={18} />
    case 'DYNAMITE':
      return <Flame size={18} />
    case 'ARREST_WARRANT':
      return <Gavel size={18} />
    case 'FORTUNE_SWAP':
      return <Shuffle size={18} />
    case 'HOSTILE_TAKEOVER':
    default:
      return <Zap size={18} />
  }
}

function getTargetableTiles(itemId, tiles = [], playerId = '', myMoney = 0) {
  if (itemId === 'DYNAMITE') {
    return tiles.filter(
      (t) => t.type === 'STREET' && ((t.houseCount ?? 0) > 0 || Boolean(t.hasHotel)),
    )
  }
  if (itemId === 'HOSTILE_TAKEOVER') {
    return tiles.filter((t) => {
      if (!t.ownerId || t.ownerId === playerId) return false
      const buildingCost = t.buildingCost || 50000
      const buildingVal = t.hasHotel
        ? 5 * buildingCost
        : (t.houseCount ?? 0) * buildingCost
      const takeoverCost = (t.price || 0) + buildingVal
      return (myMoney ?? 0) >= takeoverCost
    })
  }
  return tiles.filter((t) => t.type === 'STREET' && t.ownerId)
}

function describePhase(roomStatus, phase) {
  if (roomStatus === 'WAITING') return 'Đang chờ người chơi'
  if (roomStatus === 'FINISHED') return 'Ván đấu kết thúc'
  switch (phase) {
    case 'AWAITING_ROLL':
      return 'Chờ gieo xúc xắc'
    case 'RESOLVING_ACTION':
      return 'Đang xử lý ô cờ'
    case 'AWAITING_DECISION':
      return 'Quyết định mua / kết thúc lượt'
    case 'AWAITING_DEBT_SETTLEMENT':
      return 'Cần bán tài sản trả nợ'
    case 'TIMEOUT':
      return 'Hết giờ — Bot tiếp quản'
    case 'FINISHED':
      return 'Đã kết thúc'
    default:
      return phase || 'Đang chơi'
  }
}

export default function GameRoomPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const [searchParams] = useSearchParams()

  const resolvedRoomId =
    location.state?.roomId ||
    searchParams.get('roomId') ||
    sessionStorage.getItem('activeRoomId') ||
    ''

  const [currentUser, setCurrentUser] = useState(readStoredUser)
  const playerId = currentUser.userId || currentUser.id || ''

  const [gameState, setGameState] = useState({
    roomId: resolvedRoomId,
    roomName: 'Đang tải phòng...',
    hostId: '',
    maxPlayers: 4,
    roomStatus: 'WAITING',
    turnId: location.state?.turnId ?? 1,
    phase: 'AWAITING_ROLL',
    botControl: false,
    currentPlayerId: '',
    players: [],
    tiles: BOARD_TILE_META.map((tile) => ({ ...tile, ownerId: null, frozen: false })),
    lastDice: null,
    turnTimeoutSeconds: 30,
    activeEvents: [],
    rentMultiplier: 1,
  })

  const [loadingSnapshot, setLoadingSnapshot] = useState(true)
  const [startingGame, setStartingGame] = useState(false)
  const [rolling, setRolling] = useState(false)
  const [hoveredPlayerId, setHoveredPlayerId] = useState(null)
  const [pinnedPlayerId, setPinnedPlayerId] = useState(null)
  const [lastRollerName, setLastRollerName] = useState('')
  const [actionBanner, setActionBanner] = useState(null)
  const [moneyDeltas, setMoneyDeltas] = useState({})
  const actionBannerTimerRef = useRef(null)
  const rollingAnimTimerRef = useRef(null)
  const prevMoneyRef = useRef({})
  const [leaving, setLeaving] = useState(false)
  const [wsConnected, setWsConnected] = useState(false)
  const [wsStatus, setWsStatus] = useState('connecting')
  const [serverTimeline, setServerTimeline] = useState([])
  const [localNotices, setLocalNotices] = useState([])
  const [itemFx, setItemFx] = useState(null)
  const [toast, setToast] = useState('')
  const [turnDeadline, setTurnDeadline] = useState(() => Date.now() + 30000)
  const [nowMs, setNowMs] = useState(() => Date.now())

  // Context prompt state when using targeted items
  const [pendingItem, setPendingItem] = useState(null)
  const [desiredDiceValue, setDesiredDiceValue] = useState(7)
  const [targetPlayerId, setTargetPlayerId] = useState('')
  const [targetTileIndex, setTargetTileIndex] = useState('')

  const socketRef = useRef(null)
  const toastTimerRef = useRef(null)
  const itemFxTimerRef = useRef(null)
  const playersRef = useRef([])
  const gameStateRef = useRef(gameState)
  const lastTurnKeyRef = useRef('')
  const navigatedFinishRef = useRef(false)
  const hasConnectedOnceRef = useRef(false)
  const clockSkewRef = useRef(0)
  const localNoticeSeqRef = useRef(0)

  useEffect(() => {
    gameStateRef.current = gameState
    playersRef.current = gameState.players
  }, [gameState])

  const showToast = useCallback((message) => {
    if (!message) return
    setToast(message)
    if (toastTimerRef.current) {
      window.clearTimeout(toastTimerRef.current)
    }
    toastTimerRef.current = window.setTimeout(() => setToast(''), 3600)
  }, [])

  const addLocalNotice = useCallback((message) => {
    const text = String(message || '').trim()
    if (!text) return
    const ts = Date.now() - clockSkewRef.current
    localNoticeSeqRef.current += 1
    const key = `local-${ts}-${localNoticeSeqRef.current}`
    setLocalNotices((prev) => [{ key, ts, message: text, isLocal: true }, ...prev].slice(0, 20))
  }, [])

  const mergeServerTimeline = useCallback((entries) => {
    if (!Array.isArray(entries) || entries.length === 0) return
    setServerTimeline((prev) => {
      const byId = new Map()
      prev.forEach((item) => {
        if (item && item.id != null) byId.set(Number(item.id), item)
      })
      entries.forEach((item) => {
        if (item && item.id != null && item.message) {
          const id = Number(item.id)
          byId.set(id, {
            id,
            ts: Number(item.ts || Date.now()),
            message: String(item.message),
          })
        }
      })
      return Array.from(byId.values())
        .sort((a, b) => b.id - a.id)
        .slice(0, 60)
    })
  }, [])

  const syncTurnDeadline = useCallback((payload, fallbackToDefault = false) => {
    if (typeof payload?.turnDeadlineEpochMs === 'number' && payload.turnDeadlineEpochMs > 0) {
      const skew = payload.serverTimeEpochMs ? Date.now() - payload.serverTimeEpochMs : 0
      setTurnDeadline(payload.turnDeadlineEpochMs + skew)
      setNowMs(Date.now())
      return true
    }
    if (fallbackToDefault) {
      const timeoutSec =
        Number(payload?.turnTimeoutSeconds ?? gameStateRef.current.turnTimeoutSeconds ?? 30) || 30
      setTurnDeadline(Date.now() + timeoutSec * 1000)
      setNowMs(Date.now())
    }
    return false
  }, [])

  useEffect(() => {
    return () => {
      if (toastTimerRef.current) {
        window.clearTimeout(toastTimerRef.current)
      }
      if (itemFxTimerRef.current) {
        window.clearTimeout(itemFxTimerRef.current)
      }
    }
  }, [])

  useEffect(() => {
    const interval = window.setInterval(() => {
      setNowMs(Date.now())
    }, 250)
    return () => window.clearInterval(interval)
  }, [])

  const resolveActorName = useCallback((id) => {
    if (!id) return 'Hệ thống'
    const found = playersRef.current.find((player) => player.id === id)
    return found ? found.name : id
  }, [])

  const goToFinish = useCallback(
    (snapshot) => {
      if (navigatedFinishRef.current) return
      navigatedFinishRef.current = true
      sessionStorage.removeItem('activeRoomId')
      const targetRoomId = snapshot.roomId || resolvedRoomId
      const finishData = {
        roomId: targetRoomId,
        roomName: snapshot.roomName,
        players: snapshot.players,
        tiles: snapshot.tiles,
        turnId: snapshot.turnId,
      }
      try {
        sessionStorage.setItem('lastFinishedMatch', JSON.stringify(finishData))
      } catch {
        // Ignore storage quota errors
      }
      navigate(`/finish?roomId=${encodeURIComponent(targetRoomId)}`, {
        replace: true,
        state: finishData,
      })
    },
    [navigate, resolvedRoomId],
  )

  const applySnapshot = useCallback(
    (payload) => {
      if (!payload || typeof payload !== 'object') return

      const prev = gameStateRef.current
      const nextPlayers = Array.isArray(payload.players)
        ? decoratePlayers(payload.players)
        : prev.players
      const nextTiles =
        Array.isArray(payload.tiles) && payload.tiles.length > 0
          ? payload.tiles
          : prev.tiles
      const nextRoomStatus = payload.roomStatus || prev.roomStatus
      const nextPhase =
        payload.actionPhase ||
        (payload.phase === 'TIMEOUT'
          ? prev.phase === 'TIMEOUT'
            ? 'AWAITING_ROLL'
            : prev.phase
          : payload.phase || prev.phase)
      const nextBotControl = Boolean(payload.botControl || payload.phase === 'TIMEOUT')
      const nextTurnId = payload.turnId ?? prev.turnId
      const nextCurrentPlayerId = payload.currentPlayerId ?? prev.currentPlayerId
      const nextLastDice =
        payload.lastDice !== undefined ? payload.lastDice : prev.lastDice
      const nextTurnTimeoutSeconds = Number(
        payload.turnTimeoutSeconds ?? prev.turnTimeoutSeconds ?? 30,
      )
      const nextActiveEvents = Array.isArray(payload.activeEvents)
        ? payload.activeEvents
        : prev.activeEvents || []
      const nextRentMultiplier = Number(
        payload.rentMultiplier ?? prev.rentMultiplier ?? 1,
      )

      const nextState = {
        roomId: payload.roomId || prev.roomId,
        roomName: payload.roomName || prev.roomName,
        hostId: payload.hostId ?? prev.hostId,
        maxPlayers: payload.maxPlayers ?? prev.maxPlayers,
        roomStatus: nextRoomStatus,
        turnId: nextTurnId,
        phase: nextPhase,
        botControl: nextBotControl,
        currentPlayerId: nextCurrentPlayerId,
        players: nextPlayers,
        tiles: nextTiles,
        lastDice: nextLastDice,
        turnTimeoutSeconds: nextTurnTimeoutSeconds,
        activeEvents: nextActiveEvents,
        rentMultiplier: nextRentMultiplier,
      }

      const turnKey = `${nextTurnId}:${nextCurrentPlayerId}:${nextRoomStatus}`
      const turnChanged =
        lastTurnKeyRef.current !== '' && lastTurnKeyRef.current !== turnKey
      lastTurnKeyRef.current = turnKey

      gameStateRef.current = nextState
      playersRef.current = nextPlayers

      if (typeof payload.serverTimeEpochMs === 'number' && payload.serverTimeEpochMs > 0) {
        clockSkewRef.current = Date.now() - payload.serverTimeEpochMs
      }

      setGameState(nextState)
      const syncedFromServer = syncTurnDeadline(payload, false)
      if (!syncedFromServer && (turnChanged || (nextRoomStatus === 'PLAYING' && prev.roomStatus !== 'PLAYING'))) {
        setTurnDeadline(Date.now() + (nextTurnTimeoutSeconds || 30) * 1000)
        setNowMs(Date.now())
      }
      if (Array.isArray(payload.timeline)) {
        mergeServerTimeline(payload.timeline)
      }
      setRolling(false)
      setLoadingSnapshot(false)

      if (nextRoomStatus === 'FINISHED') {
        goToFinish(nextState)
      }
    },
    [goToFinish, mergeServerTimeline, syncTurnDeadline],
  )

  const fetchSnapshot = useCallback(async () => {
    if (!resolvedRoomId) return
    try {
      const response = await api.get(`/api/v1/rooms/${encodeURIComponent(resolvedRoomId)}`)
      const payload = parseGamePayload(response.data)
      applySnapshot(payload)
    } catch (error) {
      if (error.response?.status === 401) {
        navigate('/login', { replace: true })
        return
      }
      if (error.response?.status === 404) {
        sessionStorage.removeItem('activeRoomId')
        showToast('Phòng chơi không tồn tại hoặc đã kết thúc.')
        navigate('/', { replace: true })
        return
      }
      showToast(getApiError(error, 'Không thể tải trạng thái phòng chơi.'))
      setLoadingSnapshot(false)
    }
  }, [resolvedRoomId, applySnapshot, navigate, showToast])

  // Auth + room guard + initial REST snapshot fetch
  useEffect(() => {
    const token = localStorage.getItem('accessToken')
    if (!token) {
      navigate('/login', { replace: true })
      return undefined
    }
    if (!resolvedRoomId) {
      navigate('/', { replace: true })
      return undefined
    }
    sessionStorage.setItem('activeRoomId', resolvedRoomId)

    let cancelled = false

    if (!playerId) {
      api
        .get('/api/v1/auth/me')
        .then((res) => {
          if (cancelled) return
          const profile = parseGamePayload(res.data)
          if (profile && (profile.userId || profile.username)) {
            setCurrentUser(profile)
            localStorage.setItem('currentUser', JSON.stringify(profile))
          }
        })
        .catch(() => {
          if (!cancelled) navigate('/login', { replace: true })
        })
    }

    Promise.resolve().then(() => {
      if (!cancelled) void fetchSnapshot()
    })

    return () => {
      cancelled = true
    }
  }, [resolvedRoomId, playerId, navigate, fetchSnapshot])

  // STOMP WebSocket subscription
  useEffect(() => {
    if (!resolvedRoomId || !playerId) return undefined

    const socket = createGameSocket({
      roomId: resolvedRoomId,
      userId: playerId,
      onConnect: () => {
        setWsConnected(true)
        setWsStatus('online')
        if (hasConnectedOnceRef.current) {
          addLocalNotice('Đã khôi phục kết nối WebSocket.')
          showToast('Đã khôi phục kết nối WebSocket!')
          void fetchSnapshot()
        } else {
          hasConnectedOnceRef.current = true
          addLocalNotice('Đã kết nối máy chủ WebSocket.')
        }
      },
      onDisconnect: () => {
        setWsConnected(false)
        setRolling(false)
      },
      onStatusChange: (status) => {
        setWsStatus(status)
      },
      onAuthExpired: () => {
        navigate('/login', { replace: true })
      },
      onError: (errMsg) => {
        setRolling(false)
        showToast(errMsg)
        addLocalNotice(`Lỗi kết nối: ${errMsg}`)
      },
      onMessage: (message) => {
        const payload = parseGamePayload(message)
        switch (message.type) {
          case 'GAME_STATE_SNAPSHOT': {
            applySnapshot(payload)
            break
          }
          case 'TIMELINE_APPENDED': {
            if (payload && payload.id != null && payload.message) {
              mergeServerTimeline([payload])
            }
            break
          }
          case 'GAME_STARTED': {
            setGameState((prev) => ({
              ...prev,
              roomStatus: 'PLAYING',
              turnId: payload.turnId ?? message.turnId ?? prev.turnId,
              phase: payload.actionPhase || 'AWAITING_ROLL',
              botControl: false,
            }))
            syncTurnDeadline(payload, true)
            break
          }
          case 'TURN_TIMED_OUT': {
            setRolling(false)
            const eventTurnId = Number(
              payload.newTurnId ?? payload.currentTurnId ?? payload.turnId ?? message.turnId ?? 0,
            )
            const actor = payload.playerName || resolveActorName(payload.playerId || message.senderId)
            setGameState((prev) => {
              if (eventTurnId > 0 && eventTurnId < prev.turnId) return prev
              return {
                ...prev,
                botControl: true,
                phase:
                  payload.actionPhase ||
                  (prev.phase === 'TIMEOUT' ? 'AWAITING_ROLL' : prev.phase),
              }
            })
            showToast(`${actor} hết giờ — Bot đang tiếp quản lượt!`)
            break
          }
          case 'BOT_TAKEOVER': {
            setRolling(false)
            const eventTurnId = Number(
              payload.newTurnId ?? payload.currentTurnId ?? payload.turnId ?? message.turnId ?? 0,
            )
            setGameState((prev) => {
              if (eventTurnId > 0 && eventTurnId < prev.turnId) return prev
              return {
                ...prev,
                botControl: true,
                phase:
                  payload.actionPhase ||
                  (prev.phase === 'TIMEOUT' ? 'AWAITING_ROLL' : prev.phase),
              }
            })
            break
          }
          case 'DICE_ROLLED': {
            if (rollingAnimTimerRef.current) window.clearTimeout(rollingAnimTimerRef.current)
            setRolling(true)
            rollingAnimTimerRef.current = window.setTimeout(() => setRolling(false), 600)
            const eventTurnId = Number(
              payload.newTurnId ?? payload.currentTurnId ?? payload.turnId ?? message.turnId ?? 0,
            )
            const diceObj = {
              dice1: payload.dice1,
              dice2: payload.dice2,
              total: payload.total,
            }
            const rollerId = payload.playerId || message.senderId || gameStateRef.current.currentPlayerId
            const rollerName = payload.playerName || resolveActorName(rollerId)
            if (rollerName) setLastRollerName(rollerName)
            if (actionBannerTimerRef.current) window.clearTimeout(actionBannerTimerRef.current)
            setActionBanner({
              key: `roll-${Date.now()}`,
              text: `${rollerName || 'Người chơi'} tung được ${payload.total}`,
              tone: 'info',
            })
            actionBannerTimerRef.current = window.setTimeout(() => setActionBanner(null), 2500)
            setGameState((prev) => {
              if (eventTurnId > 0 && eventTurnId < prev.turnId) return prev
              const nextPhase =
                payload.actionPhase ||
                (payload.phase === 'TIMEOUT' ? prev.phase : payload.phase || prev.phase)
              return {
                ...prev,
                turnId: payload.currentTurnId ?? message.turnId ?? prev.turnId,
                phase: nextPhase,
                lastDice: diceObj,
              }
            })
            break
          }
          case 'PROPERTY_BOUGHT': {
            const buyerId = payload.playerId || message.senderId
            const buyerName = payload.playerName || resolveActorName(buyerId)
            const tileIdx = Number(payload.tileIndex)
            const tileName = BOARD_TILE_META[tileIdx]?.name || `Ô #${tileIdx}`
            if (actionBannerTimerRef.current) window.clearTimeout(actionBannerTimerRef.current)
            setActionBanner({
              key: `buy-${Date.now()}`,
              text: `${buyerName || 'Người chơi'} đã mua ${tileName}`,
              tone: 'success',
            })
            actionBannerTimerRef.current = window.setTimeout(() => setActionBanner(null), 2500)
            setGameState((prev) => ({
              ...prev,
              tiles: prev.tiles.map((t) =>
                t.index === tileIdx ? { ...t, ownerId: buyerId } : t,
              ),
              players: prev.players.map((p) =>
                p.id === buyerId && payload.playerMoney !== undefined
                  ? { ...p, money: Number(payload.playerMoney) }
                  : p,
              ),
            }))
            break
          }
          case 'PROPERTY_SOLD': {
            const sellerId = payload.playerId || message.senderId
            const sellerName = payload.playerName || resolveActorName(sellerId)
            const tileIdx = Number(payload.tileIndex)
            const tileName = BOARD_TILE_META[tileIdx]?.name || `Ô #${tileIdx}`
            if (actionBannerTimerRef.current) window.clearTimeout(actionBannerTimerRef.current)
            setActionBanner({
              key: `sell-${Date.now()}`,
              text: `${sellerName || 'Người chơi'} đã bán ${tileName}`,
              tone: 'warn',
            })
            actionBannerTimerRef.current = window.setTimeout(() => setActionBanner(null), 2500)
            setGameState((prev) => ({
              ...prev,
              tiles: prev.tiles.map((t) =>
                t.index === tileIdx
                  ? { ...t, ownerId: null, houseCount: 0, hasHotel: false }
                  : t,
              ),
              players: prev.players.map((p) =>
                p.id === sellerId && payload.playerMoney !== undefined
                  ? {
                      ...p,
                      money: Number(payload.playerMoney),
                      debt: payload.playerDebt !== undefined ? Number(payload.playerDebt) : p.debt,
                    }
                  : p,
              ),
            }))
            break
          }
          case 'PROPERTY_UPGRADED': {
            if (payload.tileIndex !== undefined) {
              setGameState((prev) => ({
                ...prev,
                tiles: prev.tiles.map((t) =>
                  t.index === payload.tileIndex
                    ? {
                        ...t,
                        houseCount: payload.houseCount ?? t.houseCount ?? 0,
                        hasHotel: Boolean(payload.hasHotel),
                        buildingCost: payload.buildingCost ?? t.buildingCost,
                      }
                    : t,
                ),
              }))
            }
            break
          }
          case 'SPECIAL_EVENT_TRIGGERED': {
            const eventName = payload.eventName || payload.title || payload.eventId || 'Sự kiện đặc biệt'
            setGameState((prev) => ({
              ...prev,
              activeEvents: Array.isArray(payload.activeEvents)
                ? payload.activeEvents
                : prev.activeEvents,
              rentMultiplier:
                payload.rentMultiplier !== undefined
                  ? Number(payload.rentMultiplier)
                  : prev.rentMultiplier,
            }))
            showToast(`Sự kiện: ${eventName}`)
            break
          }
          case 'ITEM_USED': {
            const actorId = payload.actorId || payload.playerId || message.senderId || ''
            const actorName = payload.playerName || resolveActorName(actorId)
            const fxKey = `${Date.now()}-${Math.random().toString(36).slice(2, 7)}`
            setItemFx({
              key: fxKey,
              itemId: payload.itemId,
              itemName: payload.itemName || payload.itemId,
              actorId,
              actorName,
              targetPlayerId: payload.targetPlayerId || '',
              targetTileIndex:
                payload.targetTileIndex !== undefined && payload.targetTileIndex !== ''
                  ? Number(payload.targetTileIndex)
                  : null,
              tileIndex:
                payload.tileIndex !== undefined && payload.tileIndex !== ''
                  ? Number(payload.tileIndex)
                  : null,
              auto: payload.auto === true || payload.auto === 'true',
            })
            if (itemFxTimerRef.current) {
              window.clearTimeout(itemFxTimerRef.current)
            }
            itemFxTimerRef.current = window.setTimeout(() => setItemFx(null), 2400)
            break
          }
          case 'TURN_CHANGED': {
            setRolling(false)
            const eventTurnId = Number(
              payload.newTurnId ?? payload.currentTurnId ?? payload.turnId ?? message.turnId ?? 0,
            )
            if (eventTurnId <= 0 || eventTurnId >= gameStateRef.current.turnId) {
              syncTurnDeadline(payload, true)
            }
            setGameState((prev) => {
              if (eventTurnId > 0 && eventTurnId < prev.turnId) return prev
              const nextPhase =
                payload.actionPhase ||
                (payload.phase === 'TIMEOUT' ? 'AWAITING_ROLL' : payload.phase || prev.phase)
              return {
                ...prev,
                turnId: payload.newTurnId ?? message.turnId ?? prev.turnId,
                currentPlayerId: payload.currentPlayerId ?? prev.currentPlayerId,
                phase: nextPhase,
                botControl: Boolean(payload.botControl),
                roomStatus: payload.roomStatus || prev.roomStatus,
                lastDice: prev.lastDice,
              }
            })
            break
          }
          case 'GAME_FINISHED': {
            setRolling(false)
            showToast('Ván đấu đã kết thúc!')
            if (Array.isArray(payload.players) && payload.players.length > 0) {
              applySnapshot({ ...payload, roomStatus: 'FINISHED', phase: 'FINISHED' })
            } else {
              void fetchSnapshot()
            }
            break
          }
          case 'ROOM_JOINED':
          case 'ROOM_LEFT': {
            if (payload.hostId) {
              setGameState((prev) => ({ ...prev, hostId: payload.hostId }))
            }
            break
          }
          case 'ROOM_DELETED': {
            sessionStorage.removeItem('activeRoomId')
            showToast('Phòng chơi đã giải tán.')
            navigate('/', { replace: true })
            break
          }
          case 'ERROR': {
            setRolling(false)
            const errText = payload.message || payload.error || 'Hành động bị từ chối.'
            showToast(errText)
            addLocalNotice(`Từ chối lệnh: ${errText}`)
            break
          }
          default:
            break
        }
      },
    })

    socketRef.current = socket
    return () => {
      socket.disconnect()
      socketRef.current = null
      setWsConnected(false)
    }
  }, [resolvedRoomId, playerId, applySnapshot, addLocalNotice, mergeServerTimeline, fetchSnapshot, showToast, syncTurnDeadline, resolveActorName, navigate])

  // 4s fallback REST snapshot polling ONLY while WebSocket is disconnected
  useEffect(() => {
    if (wsConnected || !resolvedRoomId || !playerId) return undefined
    const timer = window.setInterval(() => {
      void fetchSnapshot()
    }, 4000)
    return () => window.clearInterval(timer)
  }, [wsConnected, resolvedRoomId, playerId, fetchSnapshot])

  const myPlayer = gameState.players.find((player) => player.id === playerId) || null
  const currentPlayer =
    gameState.players.find((player) => player.id === gameState.currentPlayerId) ||
    gameState.players[0] ||
    null

  const isWaiting = gameState.roomStatus === 'WAITING'
  const isPlaying = gameState.roomStatus === 'PLAYING'
  const isHost = Boolean(playerId) && gameState.hostId === playerId
  const canStartGame = isWaiting && isHost && gameState.players.length >= 2 && !startingGame

  const isMyTurn = isPlaying && Boolean(playerId) && gameState.currentPlayerId === playerId
  const isRollPhase = gameState.phase === 'AWAITING_ROLL' || gameState.phase === 'TIMEOUT'
  const isInJailTurn = isMyTurn && myPlayer?.status === 'IN_JAIL'
  const canRoll =
    isMyTurn && isRollPhase && (myPlayer?.status === 'ACTIVE' || myPlayer?.status === 'IN_JAIL')
  const canPayBail =
    isMyTurn &&
    isRollPhase &&
    myPlayer?.status === 'IN_JAIL' &&
    (myPlayer?.money ?? 0) >= BAIL_AMOUNT
  const myTile = myPlayer
    ? gameState.tiles.find((tile) => tile.index === myPlayer.position) ||
      BOARD_TILE_META[myPlayer.position]
    : null
  const isPurchasableTile = Boolean(
    myTile &&
      PURCHASABLE_TYPES.has(myTile.type) &&
      (myTile.price ?? 0) > 0 &&
      !myTile.ownerId,
  )
  const canBuy =
    isMyTurn &&
    gameState.phase === 'AWAITING_DECISION' &&
    myPlayer?.status === 'ACTIVE' &&
    isPurchasableTile &&
    (myPlayer?.money ?? 0) >= (myTile?.price ?? 0)
  const upgradableTiles =
    isMyTurn &&
    gameState.phase === 'AWAITING_DECISION' &&
    myPlayer?.status === 'ACTIVE' &&
    (gameState.upgradeTileIndex ?? -1) >= 0 &&
    !gameState.upgradedThisTurn
      ? gameState.tiles.filter(
          (tile) =>
            tile.index === gameState.upgradeTileIndex &&
            tile.type === 'STREET' &&
            tile.ownerId === playerId &&
            !tile.hasHotel &&
            !tile.mortgaged &&
            (myPlayer?.money ?? 0) >= (tile.buildingCost || 50000),
        )
      : []
  const canUpgrade = upgradableTiles.length > 0
  const selectedUpgradeTile = upgradableTiles[0] || null
  const isDebtSettlement =
    isMyTurn && gameState.phase === 'AWAITING_DEBT_SETTLEMENT' && (myPlayer?.debt ?? 0) > 0
  const myOwnedTilesForSale = isDebtSettlement
    ? gameState.tiles.filter((t) => t.ownerId === playerId)
    : []
  const highlightedPlayerId = hoveredPlayerId || pinnedPlayerId || null

  useEffect(() => {
    const prevMap = prevMoneyRef.current
    const updates = {}
    gameState.players.forEach((p) => {
      const curr = Number(p.money ?? 0)
      if (prevMap[p.id] !== undefined && prevMap[p.id] !== curr) {
        updates[p.id] = { diff: curr - prevMap[p.id], key: `${Date.now()}-${p.id}` }
      }
      prevMap[p.id] = curr
    })
    if (Object.keys(updates).length > 0) {
      setMoneyDeltas((prev) => ({ ...prev, ...updates }))
      const timer = window.setTimeout(() => {
        setMoneyDeltas((prev) => {
          const next = { ...prev }
          Object.keys(updates).forEach((id) => {
            if (next[id]?.key === updates[id].key) delete next[id]
          })
          return next
        })
      }, 1900)
      return () => window.clearTimeout(timer)
    }
    return undefined
  }, [gameState.players])
  const canEndTurn =
    isMyTurn &&
    (gameState.phase === 'AWAITING_DECISION' ||
      (isRollPhase && myPlayer?.status === 'IN_JAIL'))

  const activeTile = currentPlayer
    ? gameState.tiles.find((tile) => tile.index === currentPlayer.position) ||
      BOARD_TILE_META[currentPlayer.position]
    : null

  const myItems = Array.isArray(myPlayer?.items) ? myPlayer.items : []
  const timeoutSec = gameState.turnTimeoutSeconds || 30
  const turnTime = isPlaying
    ? Math.min(timeoutSec, Math.max(0, Math.ceil((turnDeadline - nowMs) / 1000)))
    : 0

  // Client-side stuck-at-0s self-heal watchdog
  useEffect(() => {
    if (!isPlaying || turnTime > 0) return undefined
    const timer = window.setTimeout(() => {
      void fetchSnapshot()
    }, 2500)
    return () => window.clearTimeout(timer)
  }, [isPlaying, turnTime, fetchSnapshot])

  const startGame = async () => {
    if (!canStartGame) return
    setStartingGame(true)
    try {
      const response = await api.post(
        `/api/v1/rooms/${encodeURIComponent(resolvedRoomId)}/start`,
        {},
      )
      if (response.data?.type === 'ERROR') {
        const errPayload = parseGamePayload(response.data)
        showToast(errPayload.message || errPayload.error || 'Không thể bắt đầu ván đấu.')
        return
      }
      await fetchSnapshot()
    } catch (error) {
      showToast(getApiError(error, 'Không thể bắt đầu ván đấu.'))
    } finally {
      setStartingGame(false)
    }
  }

  const rollDice = () => {
    if (!canRoll || rolling) return
    setRolling(true)
    const sent = socketRef.current?.sendRoll(gameState.turnId)
    if (!sent) {
      setRolling(false)
      showToast('WebSocket chưa sẵn sàng. Vui lòng thử lại sau giây lát.')
    }
  }

  const buyProperty = () => {
    if (!canBuy || !myPlayer) return
    const sent = socketRef.current?.sendBuy(gameState.turnId, myPlayer.position)
    if (!sent) {
      showToast('WebSocket chưa sẵn sàng.')
    }
  }

  const upgradeProperty = (tileIndexOverride) => {
    if (!canUpgrade) return
    const targetIdx =
      tileIndexOverride !== undefined ? Number(tileIndexOverride) : selectedUpgradeTile?.index
    if (targetIdx === undefined || targetIdx === null || Number.isNaN(targetIdx)) return
    const sent = socketRef.current?.sendUpgrade(gameState.turnId, targetIdx)
    if (!sent) {
      showToast('WebSocket chưa sẵn sàng.')
    }
  }

  const payBail = () => {
    if (!canPayBail) return
    const sent = socketRef.current?.sendPayBail(gameState.turnId)
    if (!sent) {
      showToast('WebSocket chưa sẵn sàng.')
    }
  }

  const endTurn = () => {
    if (!canEndTurn) return
    const sent = socketRef.current?.sendEndTurn(gameState.turnId)
    if (!sent) {
      showToast('WebSocket chưa sẵn sàng.')
    }
  }

  const leaveRoom = async () => {
    if (leaving) return
    setLeaving(true)
    try {
      await api.post(`/api/v1/rooms/${encodeURIComponent(resolvedRoomId)}/leave`, {})
    } catch {
      // Vẫn thoát về sảnh nếu phòng đã đóng trên server
    } finally {
      sessionStorage.removeItem('activeRoomId')
      socketRef.current?.disconnect()
      navigate('/', { replace: true })
    }
  }

  const handleCopyRoomId = () => {
    if (navigator.clipboard?.writeText) {
      navigator.clipboard
        .writeText(resolvedRoomId)
        .then(() => {
          showToast(`Đã sao chép mã phòng: ${resolvedRoomId}`)
        })
        .catch(() => {
          showToast(`Mã phòng: ${resolvedRoomId}`)
        })
    } else {
      showToast(`Mã phòng: ${resolvedRoomId}`)
    }
  }

  const openOrUseItem = (item) => {
    if (item.id === 'RENT_SHIELD' || item.triggerPhase === 'PASSIVE') {
      showToast(`${item.name} tự động kích hoạt khi bạn trả tiền thuê.`)
      return
    }
    if (!isMyTurn || !isItemPhaseCompatible(gameState.phase, item.triggerPhase)) {
      showToast(`Vật phẩm "${item.name}" chỉ dùng được ở pha ${item.triggerPhase}.`)
      return
    }
    const meta = ITEM_UI_META[item.id] || { contextType: 'none' }
    if (meta.contextType === 'none') {
      const sent = socketRef.current?.sendItem(gameState.turnId, item.id, {})
      if (!sent) showToast('WebSocket chưa sẵn sàng.')
      return
    }

    if (meta.contextType === 'targetPlayerId') {
      const firstOpponent = gameState.players.find(
        (p) => p.id !== playerId && p.status === 'ACTIVE',
      )
      setTargetPlayerId(firstOpponent?.id || '')
    } else if (meta.contextType === 'targetTileIndex') {
      const candidates = getTargetableTiles(
        item.id,
        gameState.tiles,
        playerId,
        myPlayer?.money ?? 0,
      )
      setTargetTileIndex(candidates[0] ? String(candidates[0].index) : '')
    }
    setPendingItem(item)
  }

  const submitPendingItem = (event) => {
    event?.preventDefault?.()
    if (!pendingItem) return
    const meta = ITEM_UI_META[pendingItem.id] || { contextType: 'none' }
    const context = {}
    if (meta.contextType === 'desiredDiceValue') {
      context.desiredDiceValue = Number(desiredDiceValue)
    } else if (meta.contextType === 'targetPlayerId') {
      if (!targetPlayerId) {
        showToast('Không có người chơi mục tiêu hợp lệ.')
        return
      }
      context.targetPlayerId = targetPlayerId
    } else if (meta.contextType === 'targetTileIndex') {
      if (targetTileIndex === '') {
        showToast('Không có ô đất mục tiêu hợp lệ.')
        return
      }
      context.targetTileIndex = Number(targetTileIndex)
    }

    const sent = socketRef.current?.sendItem(gameState.turnId, pendingItem.id, context)
    if (!sent) {
      showToast('WebSocket chưa sẵn sàng.')
      return
    }
    setPendingItem(null)
  }

  const statusHeadline = isWaiting
    ? `Phòng chờ (${gameState.players.length}/${gameState.maxPlayers})`
    : currentPlayer
      ? currentPlayer.id === playerId
        ? 'Lượt của BẠN — Hãy hành động!'
        : `Đang chờ ${currentPlayer.name} (P${currentPlayer.seatNumber || 1})`
      : 'Đang đồng bộ...'

  const activeOpponents = gameState.players.filter(
    (p) => p.id !== playerId && p.status === 'ACTIVE',
  )
  const targetableTiles = pendingItem
    ? getTargetableTiles(pendingItem.id, gameState.tiles, playerId, myPlayer?.money ?? 0)
    : []

  const timelineEntries = [
    ...serverTimeline.map((e) => ({ ...e, key: `srv-${e.id}`, isLocal: false })),
    ...localNotices,
  ]
    .sort((a, b) => {
      if (b.ts !== a.ts) return b.ts - a.ts
      if (!a.isLocal && !b.isLocal) return b.id - a.id
      return a.isLocal ? -1 : 1
    })
    .slice(0, 60)

  const itemControls = {
    items: myItems.map((item) => {
      const isPassive = item.id === 'RENT_SHIELD' || item.triggerPhase === 'PASSIVE'
      const phaseOk = isItemPhaseCompatible(gameState.phase, item.triggerPhase)
      const inJailOrDebt = myPlayer?.status === 'IN_JAIL' || (myPlayer?.debt ?? 0) > 0
      const usable = !isPassive && isMyTurn && phaseOk && !inJailOrDebt
      let reason = ''
      if (isPassive) {
        reason = 'Tự động kích hoạt khi trả tiền thuê'
      } else if (!isMyTurn) {
        reason = 'Chưa tới lượt của bạn'
      } else if (myPlayer?.status === 'IN_JAIL') {
        reason = 'Không thể dùng vật phẩm khi đang ở tù'
      } else if ((myPlayer?.debt ?? 0) > 0) {
        reason = 'Cần trả hết nợ trước khi dùng vật phẩm'
      } else if (!phaseOk) {
        reason = `Chỉ dùng ở pha ${item.triggerPhase}`
      }
      return {
        id: item.id,
        name: item.name,
        description: item.description || '',
        triggerPhase: item.triggerPhase,
        usable,
        reason,
      }
    }),
    pendingItem,
    targetableTileIndices: targetableTiles.map((t) => t.index),
    desiredDiceValue,
    setDesiredDiceValue,
    opponents: activeOpponents.map((p) => ({ id: p.id, name: p.name, color: p.color })),
    targetPlayerId,
    setTargetPlayerId,
    onUse: openOrUseItem,
    onSubmit: submitPendingItem,
    onCancel: () => setPendingItem(null),
    onTargetTile: (tileIndex) => {
      if (!pendingItem) return
      const sent = socketRef.current?.sendItem(gameState.turnId, pendingItem.id, {
        targetTileIndex: Number(tileIndex),
      })
      if (!sent) {
        showToast('WebSocket chưa sẵn sàng.')
        return
      }
      setPendingItem(null)
    },
  }

  return (
    <main className="party-game">
      <header className="party-topbar">
        <div className="party-brand">
          <div className="party-logo">
            <Dices size={20} />
          </div>
          <div>
            <div>
              MONOPOLY <b>PARTY</b>
            </div>
            <small>Server Authoritative</small>
          </div>
        </div>

        <div className="party-room-tools">
          <div className="room-pill">
            <button
              type="button"
              className="copy-room-btn"
              onClick={handleCopyRoomId}
              title="Sao chép mã phòng"
            >
              <b>
                <i />
                {resolvedRoomId}
              </b>
              <Copy size={12} />
            </button>
            <span>•</span>
            <span className="room-pill-name" title={gameState.roomName}>
              <MapPin size={13} />
              {gameState.roomName}
            </span>
            <em className={wsConnected ? 'ws-online' : 'ws-offline'}>
              {wsConnected ? <Wifi size={12} /> : <WifiOff size={12} />}
              {wsConnected
                ? 'WS Online'
                : wsStatus === 'reconnecting'
                  ? 'Đang kết nối lại...'
                  : 'Đang kết nối...'}
            </em>
          </div>
          <button
            type="button"
            className="icon-button"
            onClick={fetchSnapshot}
            title="Đồng bộ lại trạng thái"
            aria-label="Đồng bộ lại trạng thái"
          >
            <RefreshCw size={15} className={loadingSnapshot ? 'spin' : ''} />
          </button>
          <button
            type="button"
            className="leave-button"
            onClick={leaveRoom}
            disabled={leaving}
          >
            <LogOut size={15} />
            <span>{leaving ? 'Đang rời...' : 'Rời phòng'}</span>
          </button>
        </div>
      </header>

      {isWaiting && (
        <section className="waiting-banner">
          <div className="waiting-banner-info">
            <span className="waiting-pill">PHÒNG CHỜ • {gameState.players.length}/{gameState.maxPlayers} NGƯỜI CHƠI</span>
            <h2>{gameState.roomName} ({resolvedRoomId})</h2>
            <p>
              {isHost
                ? gameState.players.length >= 2
                  ? 'Đã đủ người chơi. Bạn là chủ phòng — nhấn "Bắt đầu ván đấu" để khởi động!'
                  : 'Đang chờ thêm ít nhất 1 người chơi nữa tham gia để có thể bắt đầu.'
                : 'Đang chờ chủ phòng bắt đầu ván đấu...'}
            </p>
          </div>
          <button
            type="button"
            className="primary start-game-btn"
            disabled={!canStartGame}
            onClick={startGame}
          >
            <Play size={16} />
            {startingGame ? 'Đang bắt đầu...' : 'Bắt đầu ván đấu'}
          </button>
        </section>
      )}

      <div className="party-layout">
        <aside className="left-panel">
          <section className="glass-card turn-card">
            <div className="card-title">
              <span>
                <Sparkles size={14} />
                Lượt hiện tại
              </span>
              <b>
                <Timer size={14} />
                {isPlaying ? `${turnTime}s` : 'Chờ'}
              </b>
            </div>
            {currentPlayer ? (
              <div className="active-player">
                <div
                  className="active-avatar"
                  style={{ background: `linear-gradient(135deg,${currentPlayer.color},#7c3aed)` }}
                >
                  {currentPlayer.avatar}
                </div>
                <div>
                  <h3>
                    {currentPlayer.name}
                    {currentPlayer.id === playerId && <em>BẠN</em>}
                  </h3>
                  <p>
                    <i />
                    {describePhase(gameState.roomStatus, gameState.phase)}
                  </p>
                </div>
              </div>
            ) : (
              <p className="muted-panel-text">Đang tải thông tin lượt...</p>
            )}
          </section>

          <section className="glass-card players-card">
            <div className="card-title">
              <span>
                <Users size={14} />
                Người chơi ({gameState.players.length}/{gameState.maxPlayers})
              </span>
              <b>Turn #{gameState.turnId}</b>
            </div>
            <div className="players-list">
              {gameState.players.map((player) => {
                const isTurn = isPlaying && player.id === gameState.currentPlayerId
                const tileAt =
                  gameState.tiles.find((t) => t.index === player.position) ||
                  BOARD_TILE_META[player.position]
                const ownedCount =
                  Array.isArray(player.ownedTiles)
                    ? player.ownedTiles.length
                    : player.ownedCount ?? 0
                return (
                  <div
                    className={`player-row ${isTurn ? 'is-current' : ''} ${player.status === 'BANKRUPT' ? 'is-bankrupt' : ''} ${highlightedPlayerId === player.id ? 'is-highlighted' : ''}`}
                    key={player.id}
                    onMouseEnter={() => setHoveredPlayerId(player.id)}
                    onMouseLeave={() => setHoveredPlayerId((prev) => (prev === player.id ? null : prev))}
                    onClick={() =>
                      setPinnedPlayerId((prev) => (prev === player.id ? null : player.id))
                    }
                  >
                    <div className="player-info">
                      <div className="player-avatar" style={{ background: player.color }}>
                        {player.avatar}
                      </div>
                      <div>
                        <h4>
                          <em className="player-seat-badge">P{player.seatNumber}</em>
                          {player.name}
                          {player.id === gameState.hostId && ' ★'}
                          {player.id === playerId && ' (Bạn)'}
                          {player.isBot && ' [Bot]'}
                          {isTurn && <i />}
                        </h4>
                        <p>
                          <Home size={10} />
                          {ownedCount} đất <b className="meta-dot">•</b>{' '}
                          {player.status === 'IN_JAIL'
                            ? `Trong tù (${player.turnsInJail ?? 0}/3)`
                            : player.status === 'BANKRUPT'
                              ? 'Phá sản'
                              : tileAt?.name || `Ô #${player.position}`}
                        </p>
                      </div>
                    </div>
                    <strong>
                      {formatMoney(player.money ?? 0)}
                      {moneyDeltas[player.id] && (
                        <span
                          key={moneyDeltas[player.id].key}
                          className={`money-delta ${moneyDeltas[player.id].diff >= 0 ? 'pos' : 'neg'}`}
                        >
                          {moneyDeltas[player.id].diff >= 0 ? '+' : ''}
                          {formatMoney(moneyDeltas[player.id].diff)}
                        </span>
                      )}
                    </strong>
                  </div>
                )
              })}
            </div>
          </section>

          <section className="glass-card log-card">
            <div className="card-title">
              <span>
                <ScrollText size={14} />
                Nhật ký ván đấu
              </span>
            </div>
            <div className="game-logs">
              {timelineEntries.length === 0 ? (
                <p>Chưa có sự kiện nào trong phiên này.</p>
              ) : (
                timelineEntries.map((entry) => {
                  const timeLabel = new Date(entry.ts).toLocaleTimeString('vi-VN', {
                    hour12: false,
                  })
                  return (
                    <p className={entry.isLocal ? 'log-local' : ''} key={entry.key}>
                      [{timeLabel}] {entry.message}
                    </p>
                  )
                })
              )}
            </div>
          </section>
        </aside>

        <section className="board-stage">
          <Board
            tiles={gameState.tiles}
            players={gameState.players}
            currentPlayerId={gameState.currentPlayerId}
            myPlayerId={playerId}
            dice={gameState.lastDice}
            canRoll={canRoll}
            rolling={rolling}
            isInJail={isInJailTurn}
            turnsInJail={myPlayer?.turnsInJail ?? 0}
            onRoll={rollDice}
            isMyTurn={isMyTurn}
            canBuy={canBuy}
            buyPrice={myTile?.price ?? 0}
            onBuy={buyProperty}
            canUpgrade={canUpgrade}
            upgradeCost={selectedUpgradeTile?.buildingCost || 50000}
            onUpgrade={upgradeProperty}
            upgradableTileIndices={upgradableTiles.map((t) => t.index)}
            onUpgradeTile={(idx) => upgradeProperty(idx)}
            canPayBail={canPayBail}
            onPayBail={payBail}
            canEndTurn={canEndTurn}
            onEndTurn={endTurn}
            botControl={gameState.botControl}
            roomStatus={gameState.roomStatus}
            phase={gameState.phase}
            statusHeadline={statusHeadline}
            activeEvents={gameState.activeEvents}
            rentMultiplier={gameState.rentMultiplier}
            itemControls={itemControls}
            itemFx={itemFx}
            highlightedPlayerId={highlightedPlayerId}
            sellableTileIndices={myOwnedTilesForSale.map((t) => t.index)}
            forcedDiceValue={gameState.forcedDiceValue ?? null}
            lastRollerName={lastRollerName}
            actionBanner={actionBanner}
          />
          <div className="status-pill">
            <div>
              <i />
              <span>
                {describePhase(gameState.roomStatus, gameState.phase)}
                {activeTile && (
                  <>
                    {' '}
                    • Đang ở ô <b>{activeTile.name}</b>
                  </>
                )}
              </span>
            </div>
            <b>Turn #{gameState.turnId}</b>
          </div>
        </section>

        <aside className="right-panel">
          <section className="glass-card action-card-panel">
            <div className="card-title">
              <span>
                <Zap size={14} />
                Hành động
              </span>
              <b className="phase-badge">
                {isPlaying ? gameState.phase : gameState.roomStatus}
              </b>
            </div>

            {isWaiting && (
              <button
                type="button"
                className="primary wide start-side-btn"
                disabled={!canStartGame}
                onClick={startGame}
              >
                <Play size={16} />
                {startingGame ? 'Đang bắt đầu...' : 'Bắt đầu ván đấu'}
              </button>
            )}

            <button
              type="button"
              className="side-action roll-action"
              onClick={rollDice}
              disabled={!canRoll || rolling}
            >
              <div>
                <Dices size={20} />
              </div>
              <span>
                <strong>{isInJailTurn ? 'Thử gieo đôi ra tù' : 'Gieo xúc xắc'}</strong>
                <small>
                  {rolling
                    ? 'Đang gửi lệnh...'
                    : isInJailTurn
                      ? `Lượt tù ${myPlayer?.turnsInJail ?? 0}/3 — Gieo đôi để ra tù`
                      : canRoll
                        ? 'Nhấn để tung cặp xúc xắc'
                        : 'Chưa tới pha gieo xúc xắc'}
                </small>
              </span>
            </button>

            {canPayBail && (
              <button
                type="button"
                className="side-action bail-action"
                onClick={payBail}
              >
                <div>
                  <KeyRound size={20} />
                </div>
                <span>
                  <strong>Nộp bảo lãnh ({formatMoney(BAIL_AMOUNT)})</strong>
                  <small>Trả {formatMoney(BAIL_AMOUNT)} để ra tù ngay lập tức</small>
                </span>
              </button>
            )}

            <button
              type="button"
              className={`side-action buy-action ${canBuy ? 'enabled' : ''}`}
              disabled={!canBuy}
              onClick={buyProperty}
            >
              <div>
                <Landmark size={20} />
              </div>
              <span>
                <strong>
                  Mua bất động sản{isPurchasableTile && myTile?.price ? ` (${formatMoney(myTile.price)})` : ''}
                </strong>
                <small>
                  {canBuy
                    ? `Mua ${myTile?.name}`
                    : isMyTurn && gameState.phase === 'AWAITING_DECISION'
                      ? 'Ô hiện tại không thể mua hoặc không đủ tiền'
                      : 'Chưa thể mua ở giai đoạn này'}
                </small>
              </span>
            </button>

            {isDebtSettlement && (
              <div className="debt-panel">
                <div className="debt-panel-head">
                  <span>Cần trả nợ</span>
                  <span>
                    Số dư: {formatMoney((myPlayer?.money ?? 0) - (myPlayer?.debt ?? 0))}
                  </span>
                </div>
                <div className="debt-sell-list">
                  {myOwnedTilesForSale.length === 0 ? (
                    <small>Không còn tài sản để bán.</small>
                  ) : (
                    myOwnedTilesForSale.map((tile) => {
                      const bCost = tile.buildingCost || 50000
                      const bVal = tile.hasHotel
                        ? 5 * bCost
                        : (tile.houseCount ?? 0) * bCost
                      const refund = Math.floor(((tile.price || 0) + bVal) / 2)
                      return (
                        <div className="debt-sell-row" key={tile.index}>
                          <span>
                            #{tile.index} {tile.name} (+{formatMoney(refund)})
                          </span>
                          <button
                            type="button"
                            className="debt-sell-btn"
                            onClick={() =>
                              socketRef.current?.sendSell(gameState.turnId, tile.index)
                            }
                          >
                            Bán
                          </button>
                        </div>
                      )
                    })
                  )}
                </div>
              </div>
            )}

            {canUpgrade && (
              <div className="upgrade-control-group">
                <button
                  type="button"
                  className="side-action upgrade-action enabled"
                  onClick={() => upgradeProperty()}
                >
                  <div>
                    <Building2 size={20} />
                  </div>
                  <span>
                    <strong>Nâng cấp nhà / KS</strong>
                    <small>
                      {selectedUpgradeTile
                        ? `${selectedUpgradeTile.name} (${formatMoney(selectedUpgradeTile.buildingCost || 50000)})`
                        : 'Nâng cấp bất động sản'}
                    </small>
                  </span>
                </button>
              </div>
            )}

            <button
              type="button"
              className="end-turn"
              disabled={!canEndTurn}
              onClick={endTurn}
            >
              <ArrowRightCircle size={16} />
              Kết thúc lượt
            </button>
          </section>

          <section className="glass-card inventory-card">
            <div className="card-title">
              <span>
                <Backpack size={14} />
                Vật phẩm
              </span>
              <b>{myItems.length} / 5</b>
            </div>

            {myItems.length === 0 ? (
              <p className="muted-panel-text">Túi đồ trống</p>
            ) : (
              <div className="inventory-grid">
                {myItems.map((item, idx) => {
                  const meta = ITEM_UI_META[item.id] || { tone: 'rose' }
                  const isPassive = item.id === 'RENT_SHIELD' || item.triggerPhase === 'PASSIVE'
                  const usable =
                    !isPassive &&
                    isMyTurn &&
                    isItemPhaseCompatible(gameState.phase, item.triggerPhase)
                  return (
                    <button
                      type="button"
                      className={`power-${meta.tone} ${isPassive ? 'is-passive-item' : ''}`}
                      disabled={!usable && !isPassive}
                      title={
                        isPassive
                          ? `${item.name}: Tự động chắn tiền thuê`
                          : `${item.name}: ${item.description || ''} (${item.triggerPhase})`
                      }
                      onClick={() => openOrUseItem(item)}
                      key={`${item.id}-${idx}`}
                    >
                      <ItemIcon itemId={item.id} />
                      <span>{item.name}</span>
                      {isPassive && (
                        <small className="item-passive-badge">Tự động chắn tiền thuê</small>
                      )}
                    </button>
                  )
                })}
              </div>
            )}

            {pendingItem && (
              <form className="item-context-box" onSubmit={submitPendingItem}>
                <p>
                  Dùng <b>{pendingItem.name}</b>
                </p>
                {ITEM_UI_META[pendingItem.id]?.contextType === 'desiredDiceValue' && (
                  <label>
                    Chọn tổng xúc xắc (2–12):
                    <input
                      type="number"
                      min={2}
                      max={12}
                      value={desiredDiceValue}
                      onChange={(e) => setDesiredDiceValue(e.target.value)}
                    />
                  </label>
                )}
                {ITEM_UI_META[pendingItem.id]?.contextType === 'targetPlayerId' && (
                  <label>
                    Chọn đối thủ:
                    <select
                      value={targetPlayerId}
                      onChange={(e) => setTargetPlayerId(e.target.value)}
                    >
                      {activeOpponents.map((opp) => (
                        <option value={opp.id} key={opp.id}>
                          {opp.name}
                        </option>
                      ))}
                    </select>
                  </label>
                )}
                {ITEM_UI_META[pendingItem.id]?.contextType === 'targetTileIndex' && (
                  <label>
                    Chọn ô đất mục tiêu:
                    <select
                      value={targetTileIndex}
                      onChange={(e) => setTargetTileIndex(e.target.value)}
                    >
                      {targetableTiles.map((tile) => (
                        <option value={tile.index} key={tile.index}>
                          #{tile.index} {tile.name} ({formatMoney(tile.price)})
                        </option>
                      ))}
                    </select>
                  </label>
                )}
                <div className="item-context-actions">
                  <button type="submit" className="confirm-item-btn">
                    Kích hoạt
                  </button>
                  <button
                    type="button"
                    className="cancel-item-btn"
                    onClick={() => setPendingItem(null)}
                  >
                    Hủy
                  </button>
                </div>
              </form>
            )}
          </section>
        </aside>
      </div>

      {toast && (
        <div className="game-toast" role="status">
          {toast}
        </div>
      )}
    </main>
  )
}
