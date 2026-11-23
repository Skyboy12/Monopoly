import { useCallback, useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  Plus,
  KeyRound,
  Users,
  Crown,
  MapPin,
  Gamepad2,
  RefreshCw,
  AlertCircle,
  DoorOpen,
} from 'lucide-react'
import Topbar from '../../components/Topbar'
import Logo from '../../components/Logo'
import CreateRoomModal from '../../components/CreateRoomModal'
import api, { getApiError, parseGamePayload } from '../../plugins/api'

function readStoredUser() {
  try {
    return JSON.parse(localStorage.getItem('currentUser') || '{}')
  } catch {
    return {}
  }
}

function normalizeRoom(room) {
  const statusCode = room.status || 'WAITING'
  const statusLabel =
    statusCode === 'PLAYING'
      ? 'Đang chơi'
      : statusCode === 'FINISHED'
        ? 'Đã kết thúc'
        : 'Đang chờ'
  return {
    id: room.roomId || room.id,
    hostId: room.hostId || '',
    host: room.hostName || room.hostId || 'Chủ phòng',
    roomName: room.roomName || 'Phòng Monopoly',
    players: Number(room.currentPlayers ?? room.players ?? 0),
    maxPlayers: Number(room.maxPlayers ?? 4),
    statusCode,
    statusLabel,
  }
}

export default function HomePage() {
  const navigate = useNavigate()
  const [createModal, setCreateModal] = useState(false)
  const [creatingRoom, setCreatingRoom] = useState(false)
  const [createError, setCreateError] = useState('')
  const [roomCode, setRoomCode] = useState('')
  const [activeTab, setActiveTab] = useState('Tất cả')
  const [rooms, setRooms] = useState([])
  const [loadingRooms, setLoadingRooms] = useState(true)
  const [joiningId, setJoiningId] = useState('')
  const [lobbyError, setLobbyError] = useState('')
  const [currentUser, setCurrentUser] = useState(readStoredUser)

  const fetchRooms = useCallback(async () => {
    try {
      const response = await api.get('/api/v1/rooms')
      const payload = parseGamePayload(response.data)
      if (Array.isArray(payload)) {
        setRooms(payload.map(normalizeRoom))
      } else {
        setRooms([])
      }
      setLobbyError('')
    } catch (error) {
      if (error.response?.status === 401) {
        navigate('/login', { replace: true })
        return
      }
      setLobbyError(getApiError(error, 'Không thể tải danh sách phòng.'))
    } finally {
      setLoadingRooms(false)
    }
  }, [navigate])

  const fetchProfile = useCallback(async () => {
    try {
      const response = await api.get('/api/v1/auth/me')
      const payload = parseGamePayload(response.data)
      if (payload && (payload.userId || payload.username)) {
        setCurrentUser(payload)
        localStorage.setItem('currentUser', JSON.stringify(payload))
      }
    } catch (error) {
      if (error.response?.status === 401) {
        navigate('/login', { replace: true })
      }
    }
  }, [navigate])

  useEffect(() => {
    const token = localStorage.getItem('accessToken')
    if (!token) {
      navigate('/login', { replace: true })
      return undefined
    }
    let cancelled = false
    Promise.resolve().then(() => {
      if (cancelled) return
      void fetchProfile()
      void fetchRooms()
    })
    const timer = window.setInterval(() => {
      if (!cancelled) void fetchRooms()
    }, 5000)
    return () => {
      cancelled = true
      window.clearInterval(timer)
    }
  }, [navigate, fetchProfile, fetchRooms])

  const openGameRoom = (roomId, turnId = 1) => {
    sessionStorage.setItem('activeRoomId', roomId)
    navigate(`/game?roomId=${encodeURIComponent(roomId)}`, {
      state: { roomId, turnId },
    })
  }

  const createRoom = async ({ roomName, maxPlayers }) => {
    setLobbyError('')
    setCreateError('')
    setCreatingRoom(true)
    try {
      const response = await api.post('/api/v1/rooms', { roomName, maxPlayers })
      if (response.data?.type === 'ERROR') {
        const errPayload = parseGamePayload(response.data)
        const msg = errPayload.message || errPayload.error || 'Không thể tạo phòng.'
        setCreateError(msg)
        setLobbyError(msg)
        return
      }
      const payload = parseGamePayload(response.data)
      if (!payload?.roomId) {
        const msg = 'Máy chủ không trả về mã phòng.'
        setCreateError(msg)
        setLobbyError(msg)
        return
      }
      setCreateModal(false)
      openGameRoom(payload.roomId, payload.turnId ?? 1)
    } catch (error) {
      if (error.response?.status === 401) {
        navigate('/login', { replace: true })
        return
      }
      const msg = getApiError(error, 'Không thể tạo phòng.')
      setCreateError(msg)
      setLobbyError(msg)
    } finally {
      setCreatingRoom(false)
    }
  }

  const joinRoom = async (rawRoomId) => {
    const cleanId = rawRoomId.trim()
    if (!cleanId) {
      setLobbyError('Vui lòng nhập mã phòng hợp lệ.')
      return
    }
    setLobbyError('')
    setJoiningId(cleanId)
    try {
      const response = await api.post(`/api/v1/rooms/${encodeURIComponent(cleanId)}/join`, {
        userName: currentUser.displayName || currentUser.username,
      })
      if (response.data?.type === 'ERROR') {
        const errPayload = parseGamePayload(response.data)
        setLobbyError(errPayload.message || errPayload.error || 'Không thể tham gia phòng.')
        return
      }
      const payload = parseGamePayload(response.data)
      const targetRoomId = payload.roomId || cleanId
      openGameRoom(targetRoomId, payload.turnId ?? 1)
    } catch (error) {
      if (error.response?.status === 401) {
        navigate('/login', { replace: true })
        return
      }
      setLobbyError(getApiError(error, 'Không thể tham gia phòng.'))
    } finally {
      setJoiningId('')
    }
  }

  const handleJoinByCode = (event) => {
    event.preventDefault()
    joinRoom(roomCode)
  }

  const filteredRooms = rooms.filter((room) => {
    if (activeTab === 'Đang chờ') return room.statusCode === 'WAITING'
    if (activeTab === 'Đang chơi') return room.statusCode === 'PLAYING'
    return true
  })

  const displayName = currentUser.displayName || currentUser.username || 'Người chơi'
  const usernameHandle = currentUser.username ? `@${currentUser.username}` : 'Đang đồng bộ...'
  const ratingPoints = currentUser.ratingPoints ?? 1000
  const avatarInitial = displayName.charAt(0).toUpperCase()
  const activeRoomId = sessionStorage.getItem('activeRoomId')

  return (
    <main className="lobby app-shell lobby-redesign">
      <Topbar user={currentUser} />
      <div className="lobby-layout lobby-layout-new">
        <aside className="profile-card lobby-profile">
          <div className="profile-cover">
            <Logo small />
          </div>
          <div className="profile-main">
            <div className="avatar large">{avatarInitial}</div>
            <div>
              <h2>{displayName}</h2>
              <p>{usernameHandle}</p>
            </div>
          </div>
          <div className="stats">
            <div>
              <span>ELO</span>
              <b>{Number(ratingPoints).toLocaleString()}</b>
            </div>
            <div>
              <span>TRẠNG THÁI</span>
              <b>Online</b>
            </div>
            <div>
              <span>PHÒNG MỞ</span>
              <b>{rooms.length}</b>
            </div>
          </div>
        </aside>

        <section className="rooms-section">
          <div className="page-heading">
            <div>
              <p className="eyebrow">Sảnh chính</p>
              <h1>Chọn phòng chơi</h1>
            </div>
            <div className="heading-tools">
              <div className="quick-heading">
                <span>
                  <span className="online-dot" />
                  {rooms.length} phòng đang mở
                </span>
              </div>
              <button
                type="button"
                className="refresh-button"
                onClick={fetchRooms}
                disabled={loadingRooms}
              >
                <RefreshCw size={14} className={loadingRooms ? 'spin' : ''} />
                Làm mới
              </button>
            </div>
          </div>

          {lobbyError && (
            <div className="lobby-error-banner" role="alert">
              <AlertCircle size={15} />
              <span>{lobbyError}</span>
            </div>
          )}

          {activeRoomId && (
            <div className="resume-room-banner">
              <span>Bạn đang có phiên chơi tại phòng <b>{activeRoomId}</b></span>
              <button type="button" onClick={() => openGameRoom(activeRoomId)}>
                Quay lại phòng
              </button>
            </div>
          )}

          <div className="quick-actions">
            <button
              type="button"
              className="create-room-hero"
              onClick={() => {
                setCreateError('')
                setCreateModal(true)
              }}
            >
              <div className="quick-icon">
                <Plus size={21} />
              </div>
              <div>
                <small>Tạo ván mới</small>
                <strong>Tạo phòng mới</strong>
                <span>
                  <Crown size={13} /> Party Mode · Server Authoritative
                </span>
              </div>
              <Gamepad2 className="quick-decor" size={38} />
            </button>

            <form className="join-room-card" onSubmit={handleJoinByCode}>
              <div className="join-title">
                <KeyRound size={16} />
                <span>Tham gia bằng mã</span>
              </div>
              <div className="join-input-row">
                <input
                  value={roomCode}
                  onChange={(event) => setRoomCode(event.target.value)}
                  placeholder="room-xxxxxxxx"
                  aria-label="Mã phòng"
                />
                <button type="submit" disabled={!roomCode.trim() || Boolean(joiningId)}>
                  {joiningId ? 'Đang vào...' : 'Vào phòng'}
                </button>
              </div>
            </form>
          </div>

          <div className="room-toolbar">
            <div className="tabs">
              {['Tất cả', 'Đang chờ', 'Đang chơi'].map((tab) => (
                <button
                  type="button"
                  className={activeTab === tab ? 'active' : ''}
                  onClick={() => setActiveTab(tab)}
                  key={tab}
                >
                  {tab}
                </button>
              ))}
            </div>
            <span className="filter">Tự động cập nhật mỗi 5s</span>
          </div>

          {loadingRooms && rooms.length === 0 ? (
            <div className="empty-rooms-card">
              <RefreshCw size={24} className="spin" />
              <h3>Đang tải danh sách phòng...</h3>
            </div>
          ) : filteredRooms.length === 0 ? (
            <div className="empty-rooms-card">
              <DoorOpen size={28} />
              <h3>Chưa có phòng nào đang mở</h3>
              <p>Hãy nhấn &quot;Tạo phòng mới&quot; để khởi tạo bàn đấu và mời bạn bè tham gia.</p>
            </div>
          ) : (
            <div className="room-grid">
              {filteredRooms.map((room, index) => {
                const isFull = room.players >= room.maxPlayers
                const isWaiting = room.statusCode === 'WAITING'
                const isHostSelf = room.hostId === currentUser.userId
                return (
                  <article className="room-card" key={room.id}>
                    <div className={`room-cover room-cover-${index % 3}`}>
                      <div className="room-cover-icon">
                        <MapPin size={22} />
                      </div>
                      <span className={room.statusCode === 'PLAYING' ? 'playing' : ''}>
                        {room.statusLabel}
                      </span>
                    </div>
                    <div className="room-card-body">
                      <div className="room-card-top">
                        <div>
                          <small>Mã phòng</small>
                          <h3>{room.id}</h3>
                        </div>
                        <span className="mode-badge">Party</span>
                      </div>
                      <p className="room-map">
                        <MapPin size={13} />
                        {room.roomName}
                      </p>
                      <p className="room-host">
                        Chủ phòng: <b>{room.host}{isHostSelf ? ' (Bạn)' : ''}</b>
                      </p>
                      <div className="seat-progress">
                        <div>
                          <Users size={14} />
                          <span>
                            <b>{room.players}</b>/{room.maxPlayers} người
                          </span>
                        </div>
                        <div className="seat-bar">
                          <i style={{ width: `${Math.min(100, (room.players / room.maxPlayers) * 100)}%` }} />
                        </div>
                      </div>
                      <button
                        type="button"
                        className="join-btn room-card-button"
                        disabled={isFull || !isWaiting || joiningId === room.id}
                        onClick={() => joinRoom(room.id)}
                      >
                        {joiningId === room.id
                          ? 'Đang vào...'
                          : !isWaiting
                            ? room.statusLabel
                            : isFull
                              ? 'Đã đầy'
                              : 'Vào phòng'}
                      </button>
                    </div>
                  </article>
                )
              })}
            </div>
          )}
        </section>
      </div>

      {createModal && (
        <CreateRoomModal
          open={createModal}
          onClose={() => setCreateModal(false)}
          onCreate={createRoom}
          loading={creatingRoom}
          serverError={createError}
        />
      )}
    </main>
  )
}
