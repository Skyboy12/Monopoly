export const INITIAL_MONEY = 1_500_000;
export const GO_REWARD = 200_000;
export const BAIL_AMOUNT = 50_000;

/**
 * Định dạng tiền đầy đủ có dấu phân cách hàng nghìn, VD: 1500000 -> "$1,500,000"
 */
export function formatMoney(amount) {
  const num = Number(amount ?? 0);
  if (!Number.isFinite(num)) return '$0';
  const sign = num < 0 ? '-' : '';
  const rounded = Math.round(Math.abs(num));
  return `${sign}$${rounded.toLocaleString('en-US')}`;
}

/**
 * Định dạng tiền rút gọn cho nhãn nhỏ trên ô bàn cờ, VD: 60000 -> "$60K", 1500000 -> "$1.5M"
 */
export function formatCompactMoney(amount) {
  const num = Number(amount ?? 0);
  if (!Number.isFinite(num)) return '$0';
  const sign = num < 0 ? '-' : '';
  const abs = Math.abs(num);

  if (abs >= 1_000_000) {
    const val = (abs / 1_000_000).toFixed(2).replace(/\.?0+$/, '');
    return `${sign}$${val}M`;
  }
  if (abs >= 1_000) {
    const val = (abs / 1_000).toFixed(1).replace(/\.0$/, '');
    return `${sign}$${val}K`;
  }
  return `${sign}$${Math.round(abs)}`;
}

export default {
  INITIAL_MONEY,
  GO_REWARD,
  BAIL_AMOUNT,
  formatMoney,
  formatCompactMoney,
};
