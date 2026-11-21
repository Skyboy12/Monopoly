import { Dices, Zap } from 'lucide-react'
import Logo from '../Logo'

// eslint-disable-next-line react-refresh/only-export-components
export const tileData = [
  { name:'GO', sub:'+$200', color:'#10b981', type:'corner', icon:'flag' },
  { name:'Hội An', price:140, color:'#f43f5e', type:'prop', owner:null },
  { name:'Cộng đồng', color:'#38bdf8', type:'chest', icon:'gift' },
  { name:'Cửa Lò', price:160, color:'#f43f5e', type:'prop', owner:null },
  { name:'Thuế', price:100, color:'#94a3b8', type:'tax', icon:'coins' },
  { name:'Ga Hà Nội', price:200, color:'#334155', type:'station', icon:'train' },
  { name:'Nha Trang', price:180, color:'#38bdf8', type:'prop', owner:1 },
  { name:'May mắn', color:'#fbbf24', type:'chance', icon:'sparkles' },
  { name:'Đà Nẵng', price:200, color:'#38bdf8', type:'prop', owner:null },
  { name:'Huế', price:220, color:'#38bdf8', type:'prop', owner:null },
  { name:'Tạm giam', sub:'Thăm tù', color:'#f59e0b', type:'corner', icon:'lock' },
  { name:'Hải Phòng', price:240, color:'#ec4899', type:'prop', owner:null },
  { name:'Điện lực', price:150, color:'#eab308', type:'util', icon:'zap' },
  { name:'Phú Quốc', price:260, color:'#ec4899', type:'prop', owner:2 },
  { name:'Mộc Châu', price:280, color:'#ec4899', type:'prop', owner:null },
  { name:'Ga Sài Gòn', price:200, color:'#334155', type:'station', icon:'train' },
  { name:'Tây Nguyên', price:300, color:'#f97316', type:'prop', owner:null },
  { name:'Cộng đồng', color:'#38bdf8', type:'chest', icon:'gift' },
  { name:'Đà Lạt', price:320, color:'#f97316', type:'prop', owner:0 },
  { name:'Sa Pa', price:340, color:'#f97316', type:'prop', owner:null },
  { name:'Đỗ xe', sub:'Miễn phí', color:'#10b981', type:'corner', icon:'car' },
  { name:'Bình Dương', price:360, color:'#ef4444', type:'prop', owner:null },
  { name:'May mắn', color:'#fbbf24', type:'chance', icon:'sparkles' },
  { name:'Cần Thơ', price:380, color:'#ef4444', type:'prop', owner:null },
  { name:'Vũng Tàu', price:400, color:'#ef4444', type:'prop', owner:1 },
  { name:'Ga Đà Nẵng', price:200, color:'#334155', type:'station', icon:'train' },
  { name:'Biên Hòa', price:420, color:'#eab308', type:'prop', owner:null },
  { name:'Long An', price:440, color:'#eab308', type:'prop', owner:null },
  { name:'Nước sạch', price:150, color:'#06b6d4', type:'util', icon:'droplets' },
  { name:'Cà Mau', price:460, color:'#eab308', type:'prop', owner:null },
  { name:'Vào tù', sub:'Bị bắt!', color:'#ef4444', type:'corner', icon:'siren' },
  { name:'Hạ Long', price:500, color:'#10b981', type:'prop', owner:0 },
  { name:'Cát Bà', price:520, color:'#10b981', type:'prop', owner:null },
  { name:'Cộng đồng', color:'#38bdf8', type:'chest', icon:'gift' },
  { name:'Cần Giờ', price:540, color:'#10b981', type:'prop', owner:null },
  { name:'Ga Huế', price:200, color:'#334155', type:'station', icon:'train' },
  { name:'May mắn', color:'#fbbf24', type:'chance', icon:'sparkles' },
  { name:'TP. HCM', price:650, color:'#6366f1', type:'prop', owner:null },
  { name:'Thuế VIP', price:150, color:'#94a3b8', type:'tax', icon:'coins' },
  { name:'Hà Nội', price:750, color:'#6366f1', type:'prop', owner:null },
]

function positionToGrid(index) {
  if (index === 0) return { row:11, col:11 }
  if (index < 10) return { row:11, col:11-index }
  if (index === 10) return { row:11, col:1 }
  if (index < 20) return { row:11-(index-10), col:1 }
  if (index === 20) return { row:1, col:1 }
  if (index < 30) return { row:1, col:1+(index-20) }
  if (index === 30) return { row:1, col:11 }
  return { row:1+(index-30), col:11 }
}

export default function Board({ players, onRoll, dice, rolled, onFinish, rolling }) {
  return (
    <div className="board-frame">
      <div className="board-surface">
        <div className="monopoly-grid">
          {tileData.map((tile, index) => {
            const position = positionToGrid(index)
            const playersHere = players.filter((player) => player.position === index)
            const corner = tile.type === 'corner'
            return (
              <div
                className={`board-tile ${corner ? 'corner-tile' : ''}`}
                style={{ gridRow:position.row, gridColumn:position.col }}
                key={`${tile.name}-${index}`}
              >
                {tile.type === 'prop' && <div className={`tile-color tile-color-${position.row===11?'bottom':position.row===1?'top':position.col===1?'left':'right'}`} style={{ background:tile.color }} />}
                {tile.owner !== null && tile.owner !== undefined && <span className="tile-owner" style={{ background:players[tile.owner]?.color }} />}
                <span className="tile-name">{tile.name}</span>
                {tile.sub && <small className="tile-sub">{tile.sub}</small>}
                {tile.price && <b className="tile-price">${tile.price}</b>}
                {playersHere.length > 0 && <div className="tile-pawns">{playersHere.map((player)=><span style={{background:player.color}} key={player.id}>{player.avatar}</span>)}</div>}
              </div>
            )
          })}

          <div className="board-center">
            <div className="center-brand"><Logo small /></div>
            <div className="event-banner"><div className="event-icon"><Zap size={17} fill="currentColor"/></div><div><small>Sự kiện đang bật</small><strong>Bão may mắn</strong></div><b className="event-x">×2</b></div>
            <div className="dice-zone">
              <div className={`dice-cube ${rolling?'dice-rolling':''}`}><span>{dice[0]}</span></div>
              <div className={`dice-cube ${rolling?'dice-rolling':''}`}><span>{dice[1]}</span></div>
              <div className="dice-total">Tổng: {dice[0]+dice[1]} • Cặp đôi may mắn</div>
            </div>
            <div className="board-action">
              <button className="roll-main" onClick={onRoll} disabled={rolled}><Dices size={20}/>{rolled?'Đã gieo xúc xắc':'Gieo xúc xắc'}</button>
              <small>Nhấn hoặc bấm phím <kbd>Space</kbd> để tung</small>
              <button className="finish-demo" onClick={onFinish}>Xem kết quả</button>
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}
