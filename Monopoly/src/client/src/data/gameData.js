export const PLAYER_COLORS = ['#f43f5e', '#3b82f6', '#10b981', '#f59e0b']

export const PURCHASABLE_TYPES = new Set(['STREET', 'RAILROAD', 'UTILITY'])

export const CORNER_INDICES = new Set([0, 10, 20, 30])

/**
 * Presentation metadata matching Monopoly/src/model/Board.java (indices 0..39).
 * Authoritative ownerId, price, name, type, and frozen state always come from server snapshots.
 */
export const BOARD_TILE_META = [
  { index: 0, name: 'GO', type: 'GO', sub: '+$200K', price: 0 },
  { index: 1, name: 'Mediterranean Avenue', type: 'STREET', price: 60000, color: '#92400e', group: 'BROWN' },
  { index: 2, name: 'Community Chest 1', type: 'COMMUNITY_CHEST', sub: 'Cộng đồng', price: 0 },
  { index: 3, name: 'Baltic Avenue', type: 'STREET', price: 60000, color: '#92400e', group: 'BROWN' },
  { index: 4, name: 'Income Tax', type: 'TAX', sub: 'Thuế $200K', price: 0, taxAmount: 200000 },
  { index: 5, name: 'Reading Railroad', type: 'RAILROAD', price: 200000 },
  { index: 6, name: 'Oriental Avenue', type: 'STREET', price: 100000, color: '#38bdf8', group: 'LIGHT_BLUE' },
  { index: 7, name: 'Chance 1', type: 'CHANCE', sub: 'Cơ hội', price: 0 },
  { index: 8, name: 'Vermont Avenue', type: 'STREET', price: 100000, color: '#38bdf8', group: 'LIGHT_BLUE' },
  { index: 9, name: 'Connecticut Avenue', type: 'STREET', price: 120000, color: '#38bdf8', group: 'LIGHT_BLUE' },
  { index: 10, name: 'Jail / Just Visiting', type: 'JAIL', sub: 'Thăm tù', price: 0 },
  { index: 11, name: 'St. Charles Place', type: 'STREET', price: 140000, color: '#ec4899', group: 'PINK' },
  { index: 12, name: 'Electric Company', type: 'UTILITY', price: 150000 },
  { index: 13, name: 'States Avenue', type: 'STREET', price: 140000, color: '#ec4899', group: 'PINK' },
  { index: 14, name: 'Virginia Avenue', type: 'STREET', price: 160000, color: '#ec4899', group: 'PINK' },
  { index: 15, name: 'Pennsylvania Railroad', type: 'RAILROAD', price: 200000 },
  { index: 16, name: 'St. James Place', type: 'STREET', price: 180000, color: '#f97316', group: 'ORANGE' },
  { index: 17, name: 'Community Chest 2', type: 'COMMUNITY_CHEST', sub: 'Cộng đồng', price: 0 },
  { index: 18, name: 'Tennessee Avenue', type: 'STREET', price: 180000, color: '#f97316', group: 'ORANGE' },
  { index: 19, name: 'New York Avenue', type: 'STREET', price: 200000, color: '#f97316', group: 'ORANGE' },
  { index: 20, name: 'Free Parking', type: 'FREE_PARKING', sub: 'Nghỉ ngơi', price: 0 },
  { index: 21, name: 'Kentucky Avenue', type: 'STREET', price: 220000, color: '#ef4444', group: 'RED' },
  { index: 22, name: 'Chance 2', type: 'CHANCE', sub: 'Cơ hội', price: 0 },
  { index: 23, name: 'Indiana Avenue', type: 'STREET', price: 220000, color: '#ef4444', group: 'RED' },
  { index: 24, name: 'Illinois Avenue', type: 'STREET', price: 240000, color: '#ef4444', group: 'RED' },
  { index: 25, name: 'B&O Railroad', type: 'RAILROAD', price: 200000 },
  { index: 26, name: 'Atlantic Avenue', type: 'STREET', price: 260000, color: '#eab308', group: 'YELLOW' },
  { index: 27, name: 'Ventnor Avenue', type: 'STREET', price: 260000, color: '#eab308', group: 'YELLOW' },
  { index: 28, name: 'Water Works', type: 'UTILITY', price: 150000 },
  { index: 29, name: 'Marvin Gardens', type: 'STREET', price: 280000, color: '#eab308', group: 'YELLOW' },
  { index: 30, name: 'Go To Jail', type: 'GO_TO_JAIL', sub: 'Vào tù!', price: 0 },
  { index: 31, name: 'Pacific Avenue', type: 'STREET', price: 300000, color: '#10b981', group: 'GREEN' },
  { index: 32, name: 'North Carolina Avenue', type: 'STREET', price: 300000, color: '#10b981', group: 'GREEN' },
  { index: 33, name: 'Community Chest 3', type: 'COMMUNITY_CHEST', sub: 'Cộng đồng', price: 0 },
  { index: 34, name: 'Pennsylvania Avenue', type: 'STREET', price: 320000, color: '#10b981', group: 'GREEN' },
  { index: 35, name: 'Short Line Railroad', type: 'RAILROAD', price: 200000 },
  { index: 36, name: 'Chance 3', type: 'CHANCE', sub: 'Cơ hội', price: 0 },
  { index: 37, name: 'Park Place', type: 'STREET', price: 350000, color: '#4f46e5', group: 'DARK_BLUE' },
  { index: 38, name: 'Luxury Tax', type: 'TAX', sub: 'Thuế $100K', price: 0, taxAmount: 100000 },
  { index: 39, name: 'Boardwalk', type: 'STREET', price: 400000, color: '#4f46e5', group: 'DARK_BLUE' },
]

export const ITEM_UI_META = {
  MAGIC_DICE: { tone: 'amber', contextType: 'desiredDiceValue' },
  RENT_SHIELD: { tone: 'sky', contextType: 'none' },
  HOSTILE_TAKEOVER: { tone: 'rose', contextType: 'targetTileIndex' },
  DYNAMITE: { tone: 'rose', contextType: 'targetTileIndex' },
  ARREST_WARRANT: { tone: 'rose', contextType: 'targetPlayerId' },
  FORTUNE_SWAP: { tone: 'amber', contextType: 'none' },
}
