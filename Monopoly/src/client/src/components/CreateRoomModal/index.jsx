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
  const [botCount, setBotCount] = useState(0)
  const [localError, setLocalError] = useState('')

  if (!open) return null

  const handleMaxPlayersChange = (val) => {
    const nextMax = Number(val)
    setMaxPlayers(nextMax)
    if (botCount >= nextMax) {
      setBotCount(Math.max(0, nextMax - 1))
    }
  }

  const handleSubmit = (event) => {
    event.preventDefault()
    const trimmed = roomName.trim()
    if (!trimmed) {
      setLocalError('Vui lòng nhập tên phòng.')
      return
    }
    setLocalError('')
    onCreate({
      roomName: trimmed,
      maxPlayers: Number(maxPlayers),
      botCount: Number(botCount),
    })
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
        <label>
          Số người tối đa
          <select
            value={maxPlayers}
            onChange={(event) => handleMaxPlayersChange(event.target.value)}
          >
            <option value={2}>2 người</option>
            <option value={3}>3 người</option>
            <option value={4}>4 người</option>
          </select>
        </label>
        <label>
          Đấu với Bot
          <select
            value={botCount}
            onChange={(event) => setBotCount(Number(event.target.value))}
          >
            <option value={0}>Không (Chỉ đấu với người)</option>
            <option value={1}>1 Bot</option>
            {maxPlayers >= 3 && <option value={2}>2 Bot</option>}
            {maxPlayers >= 4 && <option value={3}>3 Bot</option>}
          </select>
        </label>
        <button type="submit" className="primary wide" disabled={loading}>
          {loading ? 'Đang tạo phòng...' : 'Tạo & vào phòng'}
        </button>
      </form>
    </div>
  )
}
