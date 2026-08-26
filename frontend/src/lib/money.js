/**
 * Currency formatting.
 *
 * The browser only ever displays money; it never computes a balance. Every figure shown comes
 * from a server response, because a total calculated on the client is a total the player can
 * change.
 */

const formatter = new Intl.NumberFormat('en-US', {
  style: 'currency',
  currency: 'USD',
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
});

/** Formats an amount for display, tolerating the string form the API sends. */
export function formatMoney(amount) {
  const value = typeof amount === 'string' ? Number.parseFloat(amount) : amount;
  if (value === null || value === undefined || Number.isNaN(value)) {
    return '$0.00';
  }
  return formatter.format(value);
}

/** Formats a net result with an explicit sign, for win/loss readouts. */
export function formatDelta(amount) {
  const value = typeof amount === 'string' ? Number.parseFloat(amount) : amount;
  if (!value || Number.isNaN(value)) {
    return '$0.00';
  }
  const sign = value > 0 ? '+' : '-';
  return `${sign}${formatter.format(Math.abs(value))}`;
}

/**
 * Validates a bet typed into the UI.
 *
 * This is for immediate feedback only. The server enforces the same limits and is the only
 * check that matters; a player who bypasses this one simply gets a 400 back.
 */
export function validateBet(raw, { min, max, balance }) {
  const value = typeof raw === 'string' ? Number.parseFloat(raw) : raw;

  if (raw === '' || raw === null || raw === undefined || Number.isNaN(value)) {
    return { valid: false, reason: 'Enter a bet amount.' };
  }
  if (value <= 0) {
    return { valid: false, reason: 'Bet must be greater than zero.' };
  }
  if (Math.round(value * 100) !== Number((value * 100).toFixed(4))) {
    return { valid: false, reason: 'Bets can have at most 2 decimal places.' };
  }
  if (min !== undefined && value < min) {
    return { valid: false, reason: `Minimum bet is ${formatMoney(min)}.` };
  }
  if (max !== undefined && value > max) {
    return { valid: false, reason: `Maximum bet is ${formatMoney(max)}.` };
  }
  if (balance !== undefined && value > balance) {
    return { valid: false, reason: 'That is more than your balance.' };
  }
  return { valid: true, amount: Number(value.toFixed(2)) };
}
