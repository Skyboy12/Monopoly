import { Client } from '@stomp/stompjs'

/**
 * Create and activate a STOMP-over-WebSocket client bound to a room and user queue.
 */
export function createGameSocket({
  roomId,
  userId,
  onConnect,
  onDisconnect,
  onStatusChange,
  onAuthExpired,
  onMessage,
  onError,
}) {
  const configuredUrl = import.meta.env.VITE_WS_URL
  const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:'
  const wsUrl = configuredUrl || `${protocol}//${location.host}/ws`
  const initialToken = localStorage.getItem('accessToken')

  const noopSender = () => false

  const handleUnauthorized = (msg) => {
    onStatusChange?.('unauthorized')
    localStorage.removeItem('accessToken')
    localStorage.removeItem('currentUser')
    if (msg) {
      onError?.(msg)
    }
    onAuthExpired?.()
  }

  if (!initialToken) {
    handleUnauthorized('Thiếu token xác thực để kết nối WebSocket.')
    onDisconnect?.()
    return {
      sendRoll: noopSender,
      sendBuy: noopSender,
      sendUpgrade: noopSender,
      sendSell: noopSender,
      sendItem: noopSender,
      sendEndTurn: noopSender,
      sendPayBail: noopSender,
      disconnect: () => {},
    }
  }

  let active = true
  let hasConnectedOnce = false

  const client = new Client({
    brokerURL: wsUrl,
    reconnectDelay: 3000,
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,
    connectHeaders: { Authorization: `Bearer ${initialToken}` },
  })

  client.beforeConnect = () => {
    if (!active) return
    const latestToken = localStorage.getItem('accessToken')
    if (!latestToken) {
      active = false
      client.deactivate()
      handleUnauthorized('Phiên đăng nhập đã hết hạn.')
      return
    }
    client.connectHeaders = { Authorization: `Bearer ${latestToken}` }
    onStatusChange?.(hasConnectedOnce ? 'reconnecting' : 'connecting')
  }

  client.onConnect = () => {
    if (!active) return
    hasConnectedOnce = true
    onStatusChange?.('online')
    onConnect?.()

    if (roomId) {
      client.subscribe(`/topic/room/${roomId}`, (frame) => {
        try {
          onMessage?.(JSON.parse(frame.body))
        } catch {
          onError?.('Không thể đọc dữ liệu trạng thái từ máy chủ.')
        }
      })
    }

    if (userId) {
      client.subscribe(`/queue/player/${userId}/errors`, (frame) => {
        try {
          onMessage?.(JSON.parse(frame.body))
        } catch {
          onError?.('Không thể đọc thông báo lỗi từ máy chủ.')
        }
      })
    }
  }

  client.onDisconnect = () => {
    if (active) {
      onStatusChange?.(hasConnectedOnce ? 'reconnecting' : 'connecting')
    }
    onDisconnect?.()
  }

  client.onWebSocketClose = () => {
    if (active) {
      onStatusChange?.(hasConnectedOnce ? 'reconnecting' : 'connecting')
      onDisconnect?.()
    }
  }

  client.onStompError = (frame) => {
    let msg = frame.headers?.message || 'Lỗi giao thức STOMP.'
    if (frame.body) {
      try {
        const parsed = JSON.parse(frame.body)
        if (parsed?.message) {
          msg = parsed.message
        }
      } catch {
        msg = frame.body
      }
    }
    const rawError = `${frame.headers?.message || ''} ${frame.body || ''} ${msg}`
    if (/UNAUTHORIZED/i.test(rawError)) {
      active = false
      client.deactivate()
      handleUnauthorized(msg)
      return
    }
    onError?.(msg)
  }

  client.onWebSocketError = () => {
    if (active) {
      onStatusChange?.(hasConnectedOnce ? 'reconnecting' : 'connecting')
    }
    onError?.('Không thể kết nối WebSocket tới máy chủ.')
  }

  client.activate()

  const send = (destination, body = {}) => {
    if (!roomId || !client.connected) return false
    client.publish({ destination, body: JSON.stringify(body) })
    return true
  }

  return {
    sendRoll: (turnId) =>
      send(`/app/room/${roomId}/roll-dice`, { roomId, playerId: userId, turnId }),
    sendBuy: (turnId, tileIndex) =>
      send(`/app/room/${roomId}/buy-property`, { roomId, playerId: userId, turnId, tileIndex }),
    sendUpgrade: (turnId, tileIndex) =>
      send(`/app/room/${roomId}/upgrade-property`, { roomId, playerId: userId, turnId, tileIndex }),
    sendSell: (turnId, tileIndex) =>
      send(`/app/room/${roomId}/sell-property`, { roomId, playerId: userId, turnId, tileIndex }),
    sendItem: (turnId, itemId, context = {}) =>
      send(`/app/room/${roomId}/use-item`, { roomId, playerId: userId, turnId, itemId, context }),
    sendEndTurn: (turnId) =>
      send(`/app/room/${roomId}/end-turn`, { roomId, playerId: userId, turnId }),
    sendPayBail: (turnId) =>
      send(`/app/room/${roomId}/pay-bail`, { roomId, playerId: userId, turnId }),
    disconnect: () => {
      active = false
      client.deactivate()
    },
  }
}
