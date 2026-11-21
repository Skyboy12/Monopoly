export const playersSeed = [
  { id: 0, name: 'Hoàng Vũ', avatar: 'H', color: '#f43f5e', cash: 1380, position: 1, bot: false },
  { id: 1, name: 'Minh An', avatar: 'M', color: '#3b82f6', cash: 1120, position: 6, bot: false },
  { id: 2, name: 'Lan Nhi', avatar: 'L', color: '#10b981', cash: 960, position: 13, bot: true },
]

export const roomsSeed = [
  { id: 'MNP-2408', host: 'LanNhi', map: 'Khu phố Hạ Long', players: 3, mode: 'Party', status: 'Đang chờ' },
  { id: 'MNP-1182', host: 'Tuấn Anh', map: 'Cổ tích', players: 2, mode: 'Classic', status: 'Đang chờ' },
  { id: 'MNP-7731', host: 'Minh Khang', map: 'Đường đua', players: 4, mode: 'Party', status: 'Đang chơi' },
]

export const powerUpsSeed = [
  { id: 'magnet', name: 'Đá đổi', icon: 'Magnet', count: 2, tone: 'rose' },
  { id: 'shield', name: 'Bảo vệ', icon: 'ShieldCheck', count: 1, tone: 'sky' },
  { id: 'rocket', name: 'Tên lửa', icon: 'Rocket', count: 1, tone: 'amber' },
]

export const finishPlayersSeed = [
  { id: 0, rank: 1, name: 'Hoàng Vũ', avatar: 'H', color: '#f43f5e', cash: 1380, assets: 520, properties: 4, rounds: 12, itemsUsed: 2 },
  { id: 1, rank: 2, name: 'Minh An', avatar: 'M', color: '#3b82f6', cash: 1120, assets: 380, properties: 3, rounds: 12, itemsUsed: 3 },
  { id: 2, rank: 3, name: 'Lan Nhi', avatar: 'L', color: '#10b981', cash: 960, assets: 260, properties: 2, rounds: 11, itemsUsed: 1 },
]
