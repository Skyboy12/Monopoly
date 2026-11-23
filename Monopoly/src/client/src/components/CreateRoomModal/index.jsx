import { useState } from 'react'

export default function CreateRoomModal({
  open,
  onClose,
  onCreate,
  loading = false,
  serverError = '',
}) {
  const [roomName, setRoomName] = useState('')
  const [maxPlayers, setMaxPlayers] = useState(4)
  const [localError, setLocalError] = useState('')

  if (!open) return null

  const handleSubmit = (event) => {
    event.preventDefault()
    const trimmed = roomName.trim()
    if (!trimmed) {
      setLocalError('Vui lòng nhập tên phòng.')
      return
    }
    setLocalError('')
    onCreate({ roomName: trimmed, maxPlayers: Number(maxPlayers) })
  }

  const displayedError = localError || serverError

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <form
        className="create-modal"
        onClick={(event) => event.stopPropagation()}
        onSubmit={handleSubmit}
      >
        <button type="button" className="modal-close" onClick={onClose} aria-label="Đóng">
          ×
        </button>
        <p className="eyebrow">Tạo phòng mới</p>
        <h2>Thiết lập ván đấu</h2>
        {displayedError && <div className="modal-error">{displayedError}</div>}
        <label>
          Tên phòng
          <input
            value={roomName}
            onChange={(event) => setRoomName(event.target.value)}
            placeholder="VD: Bàn cờ Monopoly Party"
            maxLength={50}
            autoFocus
          />
        </label>
        <div className="modal-grid">
          <label>
            Chế độ chơi
            <input value="Party Mode" disabled readOnly />
          </label>
          <label>
            Số người tối đa
            <select
              value={maxPlayers}
              onChange={(event) => setMaxPlayers(Number(event.target.value))}
            >
              <option value={2}>2 người</option>
              <option value={3}>3 người</option>
              <option value={4}>4 người</option>
            </select>
          </label>
        </div>
        <button type="submit" className="primary wide" disabled={loading}>
          {loading ? 'Đang tạo phòng...' : 'Tạo & vào phòng'}
        </button>
      </form>
    </div>
  )
}
