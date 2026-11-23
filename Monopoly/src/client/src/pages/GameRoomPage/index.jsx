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

function readStoredUser() {
  try {
    return JSON.parse(localStorage.getItem('currentUser') || '{}')
  } catch {
    return {}
  }
}

function getRoomLogsKey(roomId) {
  return `roomLogs:${roomId}`
}

function readStoredLogs(roomId) {
  if (!roomId) return []
  try {
    const raw = sessionStorage.getItem(getRoomLogsKey(roomId))
    if (!raw) return []
    const parsed = JSON.parse(raw)
    return Array.isArray(parsed)
      ? parsed.filter((entry) => typeof entry === 'string' && entry.trim())
      : []
  } catch {
    return []
  }
}

function stripLogTimestamp(line) {
  return String(line || '').replace(/^\[\d{2}:\d{2}:\d{2}\]\s*/, '').trim()
}

function decoratePlayers(players = []) {
  return players.map((player, index) => ({
    ...player,
    color: PLAYER_COLORS[index % PLAYER_COLORS.length],
    avatar: (player.name || player.id || '?').charAt(0).toUpperCase(),
  }))
}

function isItemPhaseCompatible(enginePhase, triggerPhase) {
  if (!triggerPhase) return false
  if (triggerPhase === 'PRE_ROLL') return enginePhase === 'AWAITING_ROLL'
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
    currentPlayerId: '',
    players: [],
    tiles: BOARD_TILE_META.map((tile) => ({ ...tile, ownerId: null, frozen: false })),
    lastDice: null,
  })

  const [loadingSnapshot, setLoadingSnapshot] = useState(true)
  const [startingGame, setStartingGame] = useState(false)
  const [rolling, setRolling] = useState(false)
  const [leaving, setLeaving] = useState(false)
  const [wsConnected, setWsConnected] = useState(false)
  const [logs, setLogs] = useState(() => readStoredLogs(resolvedRoomId))
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
  const playersRef = useRef([])
  const gameStateRef = useRef(gameState)
  const lastTurnKeyRef = useRef('')
  const navigatedFinishRef = useRef(false)

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

  const appendLog = useCallback((entry) => {
    if (!entry) return
    const normalized = stripLogTimestamp(entry)
    if (!normalized) return
    const timeLabel = new Date().toLocaleTimeString('vi-VN', {
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
    })
    setLogs((prev) => {
      if (prev.slice(0, 5).some((item) => stripLogTimestamp(item) === normalized)) {
        return prev
      }
      return [`[${timeLabel}] ${normalized}`, ...prev].slice(0, 30)
    })
  }, [])

  const hydrateServerLogs = useCallback((serverLogs) => {
    if (!Array.isArray(serverLogs) || serverLogs.length === 0) return
    const cleaned = serverLogs
      .filter((entry) => typeof entry === 'string' && entry.trim())
      .map((entry) => entry.trim())
    if (cleaned.length === 0) return

    setLogs((prev) => {
      const existing = new Set(prev.map(stripLogTimestamp))
      const missing = [...cleaned]
        .reverse()
        .filter((entry) => {
          const normalized = stripLogTimestamp(entry)
          if (!normalized || existing.has(normalized)) return false
          existing.add(normalized)
          return true
        })
      if (missing.length === 0) return prev
      return [...missing, ...prev].slice(0, 30)
    })
  }, [])

  const syncTurnDeadline = useCallback((payload, fallbackTo30s = false) => {
    if (typeof payload?.turnDeadlineEpochMs === 'number' && payload.turnDeadlineEpochMs > 0) {
      const skew = payload.serverTimeEpochMs ? Date.now() - payload.serverTimeEpochMs : 0
      setTurnDeadline(payload.turnDeadlineEpochMs + skew)
      setNowMs(Date.now())
      return true
    }
    if (fallbackTo30s) {
      setTurnDeadline(Date.now() + 30000)
      setNowMs(Date.now())
    }
    return false
  }, [])

  useEffect(() => {
    if (!resolvedRoomId) return
    try {
      sessionStorage.setItem(getRoomLogsKey(resolvedRoomId), JSON.stringify(logs.slice(0, 30)))
    } catch {
      // Ignore storage quota errors
    }
  }, [resolvedRoomId, logs])

  useEffect(() => {
    return () => {
      if (toastTimerRef.current) {
        window.clearTimeout(toastTimerRef.current)
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
      navigate('/finish', {
        replace: true,
        state: {
          roomId: snapshot.roomId || resolvedRoomId,
          roomName: snapshot.roomName,
          players: snapshot.players,
          tiles: snapshot.tiles,
          turnId: snapshot.turnId,
        },
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
      const nextPhase = payload.phase || prev.phase
      const nextTurnId = payload.turnId ?? prev.turnId
      const nextCurrentPlayerId = payload.currentPlayerId ?? prev.currentPlayerId
      const nextLastDice =
        payload.lastDice !== undefined ? payload.lastDice : prev.lastDice

      const nextState = {
        roomId: payload.roomId || prev.roomId,
        roomName: payload.roomName || prev.roomName,
        hostId: payload.hostId ?? prev.hostId,
        maxPlayers: payload.maxPlayers ?? prev.maxPlayers,
        roomStatus: nextRoomStatus,
        turnId: nextTurnId,
        phase: nextPhase,
        currentPlayerId: nextCurrentPlayerId,
        players: nextPlayers,
        tiles: nextTiles,
        lastDice: nextLastDice,
      }

      const turnKey = `${nextTurnId}:${nextCurrentPlayerId}:${nextRoomStatus}`
      const turnChanged =
        lastTurnKeyRef.current !== '' && lastTurnKeyRef.current !== turnKey
      lastTurnKeyRef.current = turnKey

      gameStateRef.current = nextState
      playersRef.current = nextPlayers

      setGameState(nextState)
      const syncedFromServer = syncTurnDeadline(payload, false)
      if (!syncedFromServer && (turnChanged || (nextRoomStatus === 'PLAYING' && prev.roomStatus !== 'PLAYING'))) {
        setTurnDeadline(Date.now() + 30000)
        setNowMs(Date.now())
      }
      if (Array.isArray(payload.logs)) {
        hydrateServerLogs(payload.logs)
      }
      setRolling(false)
      setLoadingSnapshot(false)

      if (nextRoomStatus === 'FINISHED') {
        goToFinish(nextState)
      }
    },
    [goToFinish, hydrateServerLogs, syncTurnDeadline],
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
        appendLog('Đã kết nối máy chủ WebSocket.')
      },
      onDisconnect: () => {
        setWsConnected(false)
      },
      onError: (errMsg) => {
        setRolling(false)
        showToast(errMsg)
        appendLog(`Lỗi kết nối: ${errMsg}`)
      },
      onMessage: (message) => {
        const payload = parseGamePayload(message)
        switch (message.type) {
          case 'GAME_STATE_SNAPSHOT': {
            applySnapshot(payload)
            break
          }
          case 'GAME_STARTED': {
            setGameState((prev) => ({
              ...prev,
              roomStatus: 'PLAYING',
              turnId: payload.turnId ?? message.turnId ?? prev.turnId,
              phase: 'AWAITING_ROLL',
            }))
            syncTurnDeadline(payload, true)
            appendLog('Ván đấu đã bắt đầu!')
            break
          }
          case 'TURN_TIMED_OUT': {
            setRolling(false)
            const actor = payload.playerName || resolveActorName(payload.playerId || message.senderId)
            const turnNum = payload.turnId ?? message.turnId ?? gameStateRef.current.turnId
            setGameState((prev) => ({
              ...prev,
              phase: 'TIMEOUT',
            }))
            appendLog(`${actor} hết giờ lượt #${turnNum} — Bot đang tiếp quản!`)
            showToast(`${actor} hết giờ — Bot đang tiếp quản lượt!`)
            break
          }
          case 'BOT_TAKEOVER': {
            setRolling(false)
            const actor = payload.playerName || resolveActorName(payload.playerId || message.senderId)
            setGameState((prev) => ({
              ...prev,
              phase: 'TIMEOUT',
            }))
            appendLog(`[Bot] Tiếp quản lượt của ${actor}.`)
            break
          }
          case 'DICE_ROLLED': {
            setRolling(false)
            const actor = payload.playerName || resolveActorName(payload.playerId || message.senderId)
            const prefix = payload.bot === true ? '[Bot] ' : ''
            const diceObj = {
              dice1: payload.dice1,
              dice2: payload.dice2,
              total: payload.total,
            }
            setGameState((prev) => ({
              ...prev,
              turnId: payload.currentTurnId ?? message.turnId ?? prev.turnId,
              phase: payload.phase || prev.phase,
              lastDice: diceObj,
            }))
            const note =
              payload.code === 'THREE_DOUBLES'
                ? ' (Gieo đôi 3 lần -> Vào tù!)'
                : ` -> Ô #${payload.newPosition}`
            appendLog(
              `${prefix}${actor} gieo được ${payload.dice1} + ${payload.dice2} = ${payload.total}${note}`,
            )
            break
          }
          case 'PROPERTY_BOUGHT': {
            const actor = payload.playerName || resolveActorName(payload.ownerId || message.senderId)
            const prefix = payload.bot === true ? '[Bot] ' : ''
            appendLog(`${prefix}${actor} đã mua ${payload.tileName} với giá $${payload.price}.`)
            break
          }
          case 'ITEM_USED': {
            const actor = payload.playerName || resolveActorName(payload.actorId || message.senderId)
            appendLog(`${actor} đã kích hoạt vật phẩm ${payload.itemName || payload.itemId}.`)
            break
          }
          case 'BAIL_PAID': {
            const actor = payload.playerName || resolveActorName(payload.playerId || message.senderId)
            appendLog(`${actor} đã nộp $50 bảo lãnh ra tù.`)
            break
          }
          case 'TURN_CHANGED': {
            setRolling(false)
            const nextPlayerName =
              payload.currentPlayerName || resolveActorName(payload.currentPlayerId)
            const prefix = payload.bot === true ? '[Bot] ' : ''
            syncTurnDeadline(payload, true)
            setGameState((prev) => ({
              ...prev,
              turnId: payload.newTurnId ?? message.turnId ?? prev.turnId,
              currentPlayerId: payload.currentPlayerId ?? prev.currentPlayerId,
              phase: payload.phase || prev.phase,
              roomStatus: payload.roomStatus || prev.roomStatus,
              lastDice: payload.code === 'EXTRA_ROLL' ? prev.lastDice : null,
            }))
            if (payload.code === 'EXTRA_ROLL') {
              appendLog(`${prefix}${nextPlayerName} gieo đôi và được tung thêm lượt!`)
            } else {
              appendLog(`Chuyển lượt sang ${nextPlayerName} (Turn #${payload.newTurnId}).`)
            }
            break
          }
          case 'GAME_FINISHED': {
            setRolling(false)
            appendLog('Ván đấu đã kết thúc!')
            showToast('Ván đấu đã kết thúc!')
            if (Array.isArray(payload.players) && payload.players.length > 0) {
              applySnapshot({ ...payload, roomStatus: 'FINISHED', phase: 'FINISHED' })
            } else {
              void fetchSnapshot()
            }
            break
          }
          case 'ROOM_JOINED': {
            const actor = payload.playerName || resolveActorName(payload.playerId || message.senderId)
            appendLog(`${actor} đã tham gia phòng.`)
            if (payload.hostId) {
              setGameState((prev) => ({ ...prev, hostId: payload.hostId }))
            }
            break
          }
          case 'ROOM_LEFT': {
            const actor = payload.playerName || resolveActorName(payload.playerId || message.senderId)
            appendLog(`${actor} đã rời phòng.`)
            if (payload.hostId) {
              setGameState((prev) => ({ ...prev, hostId: payload.hostId }))
            }
            break
          }
          case 'ROOM_DELETED': {
            sessionStorage.removeItem('activeRoomId')
            sessionStorage.removeItem(getRoomLogsKey(resolvedRoomId))
            showToast('Phòng chơi đã giải tán.')
            navigate('/', { replace: true })
            break
          }
          case 'ERROR': {
            setRolling(false)
            const errText = payload.message || payload.error || 'Hành động bị từ chối.'
            showToast(errText)
            appendLog(`Từ chối lệnh: ${errText}`)
            break
          }
          default: {
            if (message.type) {
              appendLog(`Sự kiện: ${message.type}`)
            }
            break
          }
        }
      },
    })

    socketRef.current = socket
    return () => {
      socket.disconnect()
      socketRef.current = null
      setWsConnected(false)
    }
  }, [resolvedRoomId, playerId, applySnapshot, appendLog, fetchSnapshot, showToast, syncTurnDeadline, resolveActorName, navigate])

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
  const canRoll = isMyTurn && gameState.phase === 'AWAITING_ROLL' && myPlayer?.status === 'ACTIVE'
  const canPayBail = isMyTurn && myPlayer?.status === 'IN_JAIL'
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
  const canEndTurn =
    isMyTurn && (gameState.phase === 'AWAITING_DECISION' || myPlayer?.status === 'IN_JAIL')

  const activeTile = currentPlayer
    ? gameState.tiles.find((tile) => tile.index === currentPlayer.position) ||
      BOARD_TILE_META[currentPlayer.position]
    : null

  const myItems = Array.isArray(myPlayer?.items) ? myPlayer.items : []
  const turnTime = isPlaying
    ? Math.min(30, Math.max(0, Math.ceil((turnDeadline - nowMs) / 1000)))
    : 0

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
      sessionStorage.removeItem(getRoomLogsKey(resolvedRoomId))
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
      const candidates = gameState.tiles.filter((tile) =>
        item.id === 'HOSTILE_TAKEOVER'
          ? tile.ownerId && tile.ownerId !== playerId
          : tile.type === 'STREET' && tile.ownerId,
      )
      setTargetTileIndex(candidates[0] ? String(candidates[0].index) : '')
    }
    setPendingItem(item)
  }

  const submitPendingItem = (event) => {
    event.preventDefault()
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
      ? `Lượt của ${currentPlayer.name}${currentPlayer.id === playerId ? ' (Bạn)' : ''}`
      : 'Đang đồng bộ...'

  const activeOpponents = gameState.players.filter(
    (p) => p.id !== playerId && p.status === 'ACTIVE',
  )
  const targetableTiles =
    pendingItem?.id === 'HOSTILE_TAKEOVER'
      ? gameState.tiles.filter((t) => t.ownerId && t.ownerId !== playerId)
      : gameState.tiles.filter((t) => t.type === 'STREET' && t.ownerId)

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
            <span>
              <MapPin size={13} />
              {gameState.roomName}
            </span>
            <em className={wsConnected ? 'ws-online' : 'ws-offline'}>
              {wsConnected ? <Wifi size={12} /> : <WifiOff size={12} />}
              {wsConnected ? 'WS Online' : 'Đang kết nối'}
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
                    className={`player-row ${isTurn ? 'is-current' : ''} ${player.status === 'BANKRUPT' ? 'is-bankrupt' : ''}`}
                    key={player.id}
                  >
                    <div className="player-info">
                      <div className="player-avatar" style={{ background: player.color }}>
                        {player.avatar}
                      </div>
                      <div>
                        <h4>
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
                            ? 'Trong tù'
                            : player.status === 'BANKRUPT'
                              ? 'Phá sản'
                              : tileAt?.name || `Ô #${player.position}`}
                        </p>
                      </div>
                    </div>
                    <strong>${Number(player.money ?? 0).toLocaleString()}</strong>
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
              {logs.length === 0 ? (
                <p>Chưa có sự kiện nào trong phiên này.</p>
              ) : (
                logs.map((entry, idx) => <p key={`${idx}-${entry}`}>{entry}</p>)
              )}
            </div>
          </section>
        </aside>

        <section className="board-stage">
          <Board
            tiles={gameState.tiles}
            players={gameState.players}
            currentPlayerId={gameState.currentPlayerId}
            dice={gameState.lastDice}
            canRoll={canRoll}
            rolling={rolling}
            onRoll={rollDice}
            roomStatus={gameState.roomStatus}
            phase={gameState.phase}
            statusHeadline={statusHeadline}
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
                <strong>Gieo xúc xắc</strong>
                <small>
                  {rolling
                    ? 'Đang gửi lệnh...'
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
                  <strong>Nộp bảo lãnh ($50)</strong>
                  <small>Trả $50 để ra tù ngay lập tức</small>
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
                  Mua bất động sản{isPurchasableTile && myTile?.price ? ` ($${myTile.price})` : ''}
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
                  const usable =
                    isMyTurn && isItemPhaseCompatible(gameState.phase, item.triggerPhase)
                  return (
                    <button
                      type="button"
                      className={`power-${meta.tone}`}
                      disabled={!usable}
                      title={`${item.name}: ${item.description || ''} (${item.triggerPhase})`}
                      onClick={() => openOrUseItem(item)}
                      key={`${item.id}-${idx}`}
                    >
                      <ItemIcon itemId={item.id} />
                      <span>{item.name}</span>
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
                          #{tile.index} {tile.name} (${tile.price})
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
