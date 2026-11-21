import { Client } from '@stomp/stompjs'

export function createGameSocket({ roomId, userId, onMessage, onError }) {
  const configuredUrl = import.meta.env.VITE_WS_URL
  const wsUrl = configuredUrl || `${location.protocol === 'https:' ? 'wss:' : 'ws:'}//${location.hostname}:8080/ws`
  const token = localStorage.getItem('accessToken')

  const client = new Client({
    brokerURL: wsUrl,
    reconnectDelay: 3000,
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,
    connectHeaders: token ? { Authorization: `Bearer ${token}` } : {},
  })

  client.onConnect = () => {
    if (roomId) client.subscribe(`/topic/room/${roomId}`, (frame) => {
      try { onMessage?.(JSON.parse(frame.body)) } catch { onError?.('Không thể đọc dữ liệu từ máy chủ.') }
    })
    if (userId) client.subscribe(`/queue/player/${userId}/errors`, (frame) => {
      try { onMessage?.(JSON.parse(frame.body)) } catch { onError?.('Không thể đọc thông báo lỗi.') }
    })
  }
  client.onStompError = (frame) => onError?.(frame.headers.message || 'Lỗi kết nối trò chơi.')
  client.onWebSocketError = () => onError?.('Không thể kết nối WebSocket.')
  client.activate()

  const send = (destination, body = {}) => {
    if (!client.connected) return false
    client.publish({ destination, body: JSON.stringify(body) })
    return true
  }

  return {
    sendRoll: (turnId) => roomId && send(`/app/room/${roomId}/roll-dice`, { roomId, playerId: userId, turnId }),
    sendBuy: (turnId, tileIndex) => roomId && send(`/app/room/${roomId}/buy-property`, { roomId, playerId: userId, turnId, tileIndex }),
    sendItem: (turnId, itemId, context = {}) => roomId && send(`/app/room/${roomId}/use-item`, { roomId, playerId: userId, turnId, itemId, context }),
    sendEndTurn: (turnId) => roomId && send(`/app/room/${roomId}/end-turn`, { roomId, playerId: userId, turnId }),
    disconnect: () => client.deactivate(),
  }
}
