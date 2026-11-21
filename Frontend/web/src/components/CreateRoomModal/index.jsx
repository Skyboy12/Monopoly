export default function CreateRoomModal({ open, onClose, onCreate }) {
  if (!open) return null

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="create-modal" onClick={(event) => event.stopPropagation()}>
        <button className="modal-close" onClick={onClose}>×</button>
        <p className="eyebrow">Tạo phòng mới</p>
        <h2>Thiết lập ván đấu</h2>
        <label>Tên phòng<input defaultValue="Party của Hoàng Vũ" /></label>
        <div className="modal-grid">
          <label>Chế độ chơi<select defaultValue="Party"><option>Party</option><option>Classic</option></select></label>
          <label>Số người<select defaultValue="4"><option>2</option><option>3</option><option>4</option></select></label>
        </div>
        <button className="primary wide" onClick={onCreate}>Tạo &amp; vào phòng</button>
      </div>
    </div>
  )
}
