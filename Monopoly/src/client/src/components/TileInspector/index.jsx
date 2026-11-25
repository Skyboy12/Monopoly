import { useEffect } from 'react'
import { Building2, Crosshair, Lock, ShieldAlert, Snowflake, Users, X } from 'lucide-react'
import { BAIL_AMOUNT, GO_REWARD, formatMoney } from '../../utils/money'

const TYPE_LABELS = {
  GO: 'Ô Bắt đầu (GO)',
  STREET: 'Đất phố',
  RAILROAD: 'Nhà ga',
  UTILITY: 'Tiện ích công cộng',
  TAX: 'Ô Thuế',
  CHANCE: 'Cơ hội',
  COMMUNITY_CHEST: 'Khí vận (Cộng đồng)',
  JAIL: 'Nhà tù / Thăm tù',
  GO_TO_JAIL: 'Vào tù',
  FREE_PARKING: 'Bãi đỗ xe miễn phí',
}

const SPECIAL_DESCRIPTIONS = {
  GO: `Đi qua hoặc dừng lại ô GO để nhận ${formatMoney(GO_REWARD)}.`,
  JAIL: `Thăm tù miễn phí. Nếu đang bị giam, bạn có thể nộp bảo lãnh ${formatMoney(BAIL_AMOUNT)} hoặc gieo đôi để ra tù (tối đa 3 lượt).`,
  GO_TO_JAIL: 'Di chuyển thẳng tới Nhà tù (#10), không nhận thưởng qua GO.',
  FREE_PARKING: 'Ô nghỉ ngơi an toàn, không phải trả tiền thuê.',
  CHANCE: 'Rút ngẫu nhiên 1 thẻ Cơ hội với nhiều hiệu ứng bất ngờ.',
  COMMUNITY_CHEST: 'Rút ngẫu nhiên 1 thẻ Khí vận (Cộng đồng).',
}

const STREET_LEVEL_LABELS = ['Tiền thuê gốc', '1 Nhà', '2 Nhà', '3 Nhà', 'Khách sạn']
const RAILROAD_LEVEL_LABELS = ['1 Nhà ga', '2 Nhà ga', '3 Nhà ga', '4 Nhà ga']
const UTILITY_LEVEL_LABELS = ['1 Tiện ích (× xúc xắc)', '2 Tiện ích (× xúc xắc)']

export default function TileInspector({
  tile,
  tiles = [],
  players = [],
  playersHere = [],
  myPlayer = null,
  rentMultiplier = 1,
  canBuy = false,
  onBuy,
  canUpgradeThis = false,
  onUpgradeTile,
  isTargetable = false,
  onTargetTile,
  onClose,
}) {
  useEffect(() => {
    if (!tile) return undefined
    const handleKeyDown = (event) => {
      if (event.key === 'Escape') {
        event.preventDefault()
        onClose?.()
      }
    }
    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [tile, onClose])

  if (!tile) return null

  const owner = tile.ownerId ? players.find((p) => p.id === tile.ownerId) : null
  const isPurchasable =
    tile.type === 'STREET' || tile.type === 'RAILROAD' || tile.type === 'UTILITY'
  const houseCount = Number(tile.houseCount ?? 0)
  const hasHotel = Boolean(tile.hasHotel)
  const rentTable = Array.isArray(tile.rentTable) ? tile.rentTable : []

  let activeRentIdx = -1
  if (owner && !tile.mortgaged && !tile.frozen) {
    if (tile.type === 'STREET') {
      activeRentIdx = hasHotel ? 4 : Math.min(3, Math.max(0, houseCount))
    } else if (tile.type === 'RAILROAD') {
      const ownedRailroads = tiles.filter(
        (t) => t.type === 'RAILROAD' && t.ownerId === owner.id,
      ).length
      activeRentIdx = Math.max(0, Math.min(3, ownedRailroads - 1))
    } else if (tile.type === 'UTILITY') {
      const ownedUtilities = tiles.filter(
        (t) => t.type === 'UTILITY' && t.ownerId === owner.id,
      ).length
      activeRentIdx = Math.max(0, Math.min(1, ownedUtilities - 1))
    }
  }

  const levelLabels =
    tile.type === 'STREET'
      ? STREET_LEVEL_LABELS
      : tile.type === 'RAILROAD'
        ? RAILROAD_LEVEL_LABELS
        : tile.type === 'UTILITY'
          ? UTILITY_LEVEL_LABELS
          : []

  const showBuyBtn = Boolean(canBuy && myPlayer && myPlayer.position === tile.index)

  return (
    <div
      className="tile-inspector-backdrop"
      role="dialog"
      aria-modal="true"
      aria-label={`Thông tin ô ${tile.name}`}
      onClick={(e) => {
        if (e.target === e.currentTarget) onClose?.()
      }}
    >
      <div className="tile-inspector-card">
        <div className="tile-inspector-head">
          <div className="tile-inspector-title-wrap">
            {tile.color && (
              <span
                className="tile-inspector-swatch"
                style={{ background: tile.color }}
                aria-hidden="true"
              />
            )}
            <div>
              <div className="tile-inspector-meta">
                <span>Ô #{tile.index}</span>
                <span>•</span>
                <span>{TYPE_LABELS[tile.type] || tile.type}</span>
                {tile.colorGroup && <span>({tile.colorGroup})</span>}
              </div>
              <h3>{tile.name}</h3>
            </div>
          </div>
          <button
            type="button"
            className="tile-inspector-close"
            onClick={() => onClose?.()}
            aria-label="Đóng bảng thông tin ô"
          >
            <X size={16} />
          </button>
        </div>

        {(tile.frozen || tile.mortgaged || rentMultiplier > 1) && (
          <div className="tile-inspector-flags">
            {tile.frozen && (
              <span className="inspector-flag flag-frozen">
                <Snowflake size={12} /> Đang đóng băng
              </span>
            )}
            {tile.mortgaged && (
              <span className="inspector-flag flag-mortgaged">
                <Lock size={12} /> Đã thế chấp
              </span>
            )}
            {rentMultiplier > 1 && isPurchasable && (
              <span className="inspector-flag flag-multiplier">
                <ShieldAlert size={12} /> Sự kiện ×{rentMultiplier} tiền thuê
              </span>
            )}
          </div>
        )}

        <div className="tile-inspector-body">
          {isPurchasable && (
            <div className="inspector-stats-grid">
              <div className="inspector-stat">
                <span>Giá mua</span>
                <strong>{formatMoney(tile.price)}</strong>
              </div>
              <div className="inspector-stat">
                <span>Chủ sở hữu</span>
                {owner ? (
                  <strong className="inspector-owner">
                    <i style={{ background: owner.color }} />
                    {owner.name}
                  </strong>
                ) : (
                  <strong className="is-muted">Chưa có chủ</strong>
                )}
              </div>
              {tile.type === 'STREET' && (
                <div className="inspector-stat">
                  <span>Công trình</span>
                  <strong>
                    {hasHotel
                      ? 'Khách sạn'
                      : houseCount > 0
                        ? `${houseCount} nhà`
                        : 'Chưa xây'}
                  </strong>
                </div>
              )}
              {tile.buildingCost > 0 && tile.type === 'STREET' && (
                <div className="inspector-stat">
                  <span>Phí nâng cấp</span>
                  <strong>{formatMoney(tile.buildingCost)}</strong>
                </div>
              )}
              <div className="inspector-stat">
                <span>Tiền thuê hiện tại</span>
                <strong>
                  {tile.rent > 0
                    ? formatMoney(tile.rent)
                    : tile.type === 'UTILITY' && owner
                      ? 'Theo xúc xắc'
                      : owner
                        ? '$0'
                        : '—'}
                </strong>
              </div>
            </div>
          )}

          {tile.type === 'TAX' && (
            <div className="inspector-special-box">
              <strong>Thuế phải nộp: {formatMoney(tile.taxAmount || 0)}</strong>
              <p>Người chơi dừng tại ô này sẽ bị trừ trực tiếp vào số dư tiền mặt.</p>
            </div>
          )}

          {SPECIAL_DESCRIPTIONS[tile.type] && (
            <div className="inspector-special-box">
              <p>{SPECIAL_DESCRIPTIONS[tile.type]}</p>
            </div>
          )}

          {rentTable.length > 0 && (
            <div className="inspector-rent-section">
              <div className="inspector-section-title">
                <Building2 size={13} />
                <span>Bảng tiền thuê</span>
              </div>
              <ul className="inspector-rent-list">
                {rentTable.map((val, idx) => {
                  const isCurrent = idx === activeRentIdx
                  return (
                    <li key={idx} className={isCurrent ? 'is-current-level' : ''}>
                      <span>{levelLabels[idx] || `Cấp ${idx}`}</span>
                      <strong>
                        {tile.type === 'UTILITY'
                          ? `${formatMoney(val)} × xúc xắc`
                          : formatMoney(val)}
                      </strong>
                    </li>
                  )
                })}
              </ul>
            </div>
          )}

          <div className="inspector-players-section">
            <div className="inspector-section-title">
              <Users size={13} />
              <span>Người chơi tại ô ({playersHere.length})</span>
            </div>
            {playersHere.length > 0 ? (
              <div className="inspector-players-list">
                {playersHere.map((p) => (
                  <span key={p.id} className="inspector-player-chip">
                    <i style={{ background: p.color }}>{p.avatar}</i>
                    <span>
                      {p.name}
                      {p.inJail && tile.index === 10 ? ' (Đang ở tù)' : ''}
                    </span>
                  </span>
                ))}
              </div>
            ) : (
              <p className="inspector-empty-players">Không có người chơi nào ở ô này.</p>
            )}
          </div>
        </div>

        {(showBuyBtn || canUpgradeThis || isTargetable) && (
          <div className="tile-inspector-actions">
            {showBuyBtn && (
              <button
                type="button"
                className="inspector-action-btn btn-buy"
                onClick={() => {
                  onBuy?.()
                  onClose?.()
                }}
              >
                Mua đất ({formatMoney(tile.price)})
              </button>
            )}
            {canUpgradeThis && (
              <button
                type="button"
                className="inspector-action-btn btn-upgrade"
                onClick={() => {
                  onUpgradeTile?.(tile.index)
                  onClose?.()
                }}
              >
                Nâng cấp ({formatMoney(tile.buildingCost || 50_000)})
              </button>
            )}
            {isTargetable && (
              <button
                type="button"
                className="inspector-action-btn btn-target"
                onClick={() => {
                  onTargetTile?.(tile.index)
                  onClose?.()
                }}
              >
                <Crosshair size={14} /> Chọn làm mục tiêu
              </button>
            )}
          </div>
        )}
      </div>
    </div>
  )
}
