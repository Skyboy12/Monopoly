import { Bomb, Crosshair, Dices, Flame, Handshake, Shield, Sparkles, X } from 'lucide-react'

const ITEM_ICONS = {
  MAGIC_DICE: Dices,
  RENT_SHIELD: Shield,
  HOSTILE_TAKEOVER: Handshake,
  DYNAMITE: Flame,
  ARREST_WARRANT: Crosshair,
  FORTUNE_SWAP: Sparkles,
}

export default function BoardItemBar({ itemControls }) {
  if (!itemControls) return null

  const {
    items = [],
    pendingItem = null,
    targetableTileIndices = [],
    desiredDiceValue = 7,
    setDesiredDiceValue,
    opponents = [],
    targetPlayerId = '',
    setTargetPlayerId,
    onUse,
    onSubmit,
    onCancel,
  } = itemControls

  const hasItems = Array.isArray(items) && items.length > 0
  if (!hasItems && !pendingItem) return null

  const needsTileTarget =
    pendingItem &&
    (pendingItem.id === 'HOSTILE_TAKEOVER' ||
      pendingItem.id === 'DYNAMITE' ||
      (Array.isArray(targetableTileIndices) && targetableTileIndices.length > 0 && pendingItem.id !== 'MAGIC_DICE' && pendingItem.id !== 'ARREST_WARRANT'))

  const needsDiceInput = pendingItem?.id === 'MAGIC_DICE'
  const needsPlayerSelect = pendingItem?.id === 'ARREST_WARRANT'

  return (
    <div className="board-item-bar" data-testid="board-item-bar">
      {pendingItem ? (
        <div className="item-pending-panel">
          <div className="item-pending-header">
            <span className="item-pending-title">
              <Bomb size={13} />
              <span>{pendingItem.name}</span>
            </span>
            <button
              type="button"
              className="item-cancel-btn"
              onClick={() => onCancel?.()}
              title="Hủy dùng vật phẩm"
            >
              <X size={13} />
              <span>Hủy</span>
            </button>
          </div>

          {needsTileTarget && (
            <div className="item-pending-hint">
              Nhấp vào 1 ô được tô sáng trên bàn cờ ({targetableTileIndices.length} ô khả dụng)
            </div>
          )}

          {needsDiceInput && (
            <form
              className="item-pending-form"
              onSubmit={(e) => {
                e.preventDefault()
                onSubmit?.()
              }}
            >
              <label htmlFor="magic-dice-select">Tổng xúc xắc:</label>
              <select
                id="magic-dice-select"
                value={desiredDiceValue ?? 7}
                onChange={(e) => setDesiredDiceValue?.(Number(e.target.value))}
              >
                {Array.from({ length: 11 }, (_, i) => i + 2).map((val) => (
                  <option key={val} value={val}>
                    {val}
                  </option>
                ))}
              </select>
              <button type="submit" className="item-confirm-btn">
                Xác nhận
              </button>
            </form>
          )}

          {needsPlayerSelect && (
            <form
              className="item-pending-form"
              onSubmit={(e) => {
                e.preventDefault()
                onSubmit?.()
              }}
            >
              <label htmlFor="arrest-target-select">Mục tiêu:</label>
              <select
                id="arrest-target-select"
                value={targetPlayerId || opponents[0]?.id || ''}
                onChange={(e) => setTargetPlayerId?.(e.target.value)}
              >
                {opponents.map((opp) => (
                  <option key={opp.id} value={opp.id}>
                    {opp.name}
                  </option>
                ))}
              </select>
              <button
                type="submit"
                className="item-confirm-btn"
                disabled={opponents.length === 0}
              >
                Xác nhận
              </button>
            </form>
          )}
        </div>
      ) : (
        <div className="item-bar-list" role="toolbar" aria-label="Vật phẩm trên bàn cờ">
          {items.map((item, idx) => {
            const Icon = ITEM_ICONS[item.id] || Sparkles
            const disabled = !item.usable
            const tooltip = disabled
              ? item.reason || item.description || 'Chưa thể sử dụng lúc này'
              : item.description || item.name
            return (
              <button
                key={`${item.id}-${idx}`}
                type="button"
                className={`item-bar-btn ${disabled ? 'is-disabled' : 'is-usable'}`}
                disabled={disabled}
                title={tooltip}
                onClick={() => onUse?.(item)}
              >
                <Icon size={13} />
                <span>{item.name}</span>
              </button>
            )
          })}
        </div>
      )}
    </div>
  )
}
