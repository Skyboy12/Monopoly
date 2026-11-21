import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Plus, KeyRound, Users, Crown, MapPin, Gamepad2 } from 'lucide-react'
import Topbar from '../../components/Topbar'
import Logo from '../../components/Logo'
import CreateRoomModal from '../../components/CreateRoomModal'
import { roomsSeed as mockRooms } from '../../data/gameData'
import api, { getApiError } from '../../plugins/api'

export default function HomePage() {
  const navigate = useNavigate()
  const [createModal, setCreateModal] = useState(false)
  const [roomCode, setRoomCode] = useState('')
  const [activeTab, setActiveTab] = useState('Tất cả')
  const [rooms, setRooms] = useState(mockRooms)
  const [lobbyError, setLobbyError] = useState('')
  const currentUser = JSON.parse(localStorage.getItem('currentUser') || '{}')

  const normalizeRoom = (room) => ({
    id: room.roomId || room.id,
    host: room.hostName || room.hostId || 'Không rõ',
    map: room.roomName || 'Khu phố Hạ Long',
    players: room.currentPlayers || room.players || 1,
    maxPlayers: room.maxPlayers || 4,
    mode: room.mode || 'Party',
    status: room.status === 'PLAYING' ? 'Đang chơi' : 'Đang chờ',
  })

  useEffect(() => {
    api.get('/api/v1/rooms')
      .then((response) => {
        const payload = response.data?.payload ? JSON.parse(response.data.payload) : response.data
        if (Array.isArray(payload) && payload.length) setRooms(payload.map(normalizeRoom))
      })
      .catch(() => setLobbyError(''))
  }, [])

  const createRoom = async () => {
    setLobbyError('')
    try {
      const response = await api.post('/api/v1/rooms', { roomName: 'Party của Hoàng Vũ', maxPlayers: 4 })
      const payload = response.data?.payload ? JSON.parse(response.data.payload) : response.data
      navigate('/game', { state: { roomId: payload.roomId, turnId: payload.turnId || 0 } })
    } catch (error) {
      setLobbyError(getApiError(error, 'Không thể tạo phòng.'))
      navigate('/game')
    } finally {
      setCreateModal(false)
    }
  }

  const joinRoom = async (roomId) => {
    setLobbyError('')
    try {
      const response = await api.post(`/api/v1/rooms/${roomId}/join`, { userName: currentUser.displayName || currentUser.username })
      const payload = response.data?.payload ? JSON.parse(response.data.payload) : response.data
      navigate('/game', { state: { roomId: payload.roomId || roomId, turnId: payload.turnId || 0 } })
    } catch (error) {
      setLobbyError(getApiError(error, 'Không thể tham gia phòng.'))
    }
  }

  const filteredRooms = activeTab === 'Tất cả' ? rooms : rooms.filter((room) => room.mode === activeTab)

  return (
    <main className="lobby app-shell lobby-redesign">
      <Topbar onLogout={() => navigate('/login')} />
      <div className="lobby-layout lobby-layout-new">
        <aside className="profile-card lobby-profile">
          <div className="profile-cover"><Logo small /></div>
          <div className="profile-main"><div className="avatar large">H</div><div><h2>Hoàng Vũ</h2><p>Lv. 12 · Top 18%</p></div></div>
          <div className="stats"><div><span>ELO</span><b>1,248</b></div><div><span>Thắng</span><b>42</b></div><div><span>Chuỗi</span><b>5</b></div></div>
        </aside>
        <section className="rooms-section">
          <div className="page-heading"><div><p className="eyebrow">Sảnh chính</p><h1>Chọn phòng chơi</h1></div><div className="quick-heading"><span><span className="online-dot"/>3 phòng đang mở</span></div></div>
          <div className="quick-actions">
            <button className="create-room-hero" onClick={() => setCreateModal(true)}><div className="quick-icon"><Plus size={21}/></div><div><small>Tạo ván mới</small><strong>Tạo phòng mới</strong><span><Crown size={13}/> Party Mode · Khu phố Hạ Long</span></div><Gamepad2 className="quick-decor" size={38}/></button>
            <div className="join-room-card"><div className="join-title"><KeyRound size={16}/><span>Tham gia bằng mã</span></div><div className="join-input-row"><input value={roomCode} onChange={(event) => setRoomCode(event.target.value.toUpperCase())} placeholder="MNP-2408"/><button onClick={() => roomCode && joinRoom(roomCode)}>Vào phòng</button></div></div>
          </div>
          <div className="room-toolbar"><div className="tabs">{['Tất cả','Party','Classic'].map((tab)=><button className={activeTab===tab?'active':''} onClick={()=>setActiveTab(tab)} key={tab}>{tab}</button>)}</div><button className="filter">≡ Bộ lọc</button></div>
          <div className="room-grid">
            {filteredRooms.map((room, index) => (
              <article className="room-card" key={room.id}>
                <div className={`room-cover room-cover-${index}`}><div className="room-cover-icon"><MapPin size={22}/></div><span className={room.status === 'Đang chơi' ? 'playing' : ''}>{room.status}</span></div>
                <div className="room-card-body">
                  <div className="room-card-top"><div><small>Mã phòng</small><h3>{room.id}</h3></div><span className="mode-badge">{room.mode}</span></div>
                  <p className="room-map"><MapPin size={13}/>{room.map}</p>
                  <p className="room-host">Chủ phòng: <b>{room.host}</b></p>
                  <div className="seat-progress"><div><Users size={14}/><span><b>{room.players}</b>/4 người</span></div><div className="seat-bar"><i style={{width:`${room.players/4*100}%`}}/></div></div>
                  <button className="join-btn room-card-button" disabled={room.players === 4 || room.status === 'Đang chơi'} onClick={() => joinRoom(room.id)}>{room.status === 'Đang chơi' ? 'Đang đấu' : 'Vào phòng'}</button>
                </div>
              </article>
            ))}
          </div>
        </section>
      </div>
          {lobbyError && <div className="lobby-error">{lobbyError}</div>}
      <CreateRoomModal open={createModal} onClose={() => setCreateModal(false)} onCreate={createRoom} />
    </main>
  )
}
