import { describe, it, expect } from 'vitest';
import { formatMoney, formatDelta, validateBet } from '../src/lib/money.js';

describe('formatMoney', () => {
  it('formats numbers as US currency with two decimals', () => {
    expect(formatMoney(100)).toBe('$100.00');
    expect(formatMoney(10000)).toBe('$10,000.00');
    expect(formatMoney(0.5)).toBe('$0.50');
  });

  it('accepts the string form the API sends', () => {
    expect(formatMoney('1234.50')).toBe('$1,234.50');
  });

  it('falls back to zero rather than showing NaN to the player', () => {
    expect(formatMoney(undefined)).toBe('$0.00');
    expect(formatMoney(null)).toBe('$0.00');
    expect(formatMoney('not a number')).toBe('$0.00');
  });
});

describe('formatDelta', () => {
  it('marks a win with a plus and a loss with a minus', () => {
    expect(formatDelta(25)).toBe('+$25.00');
    expect(formatDelta(-10)).toBe('-$10.00');
  });

  it('shows a break-even round without a sign', () => {
    expect(formatDelta(0)).toBe('$0.00');
  });
});

describe('validateBet', () => {
  const limits = { min: 1, max: 5000, balance: 100 };

  it('accepts a bet inside every limit', () => {
    expect(validateBet('25', limits)).toEqual({ valid: true, amount: 25 });
    expect(validateBet(1, limits)).toEqual({ valid: true, amount: 1 });
  });

  it('rejects an empty or non-numeric entry', () => {
    expect(validateBet('', limits).valid).toBe(false);
    expect(validateBet('abc', limits).valid).toBe(false);
    expect(validateBet(null, limits).valid).toBe(false);
  });

  it('rejects zero and negative bets, which would otherwise add balance', () => {
    expect(validateBet(0, limits).valid).toBe(false);
    expect(validateBet(-50, limits).valid).toBe(false);
  });

  it('rejects a bet below the table minimum', () => {
    const result = validateBet('0.50', limits);
    expect(result.valid).toBe(false);
    expect(result.reason).toContain('minimum');
  });

  it('rejects a bet above the table maximum', () => {
    const result = validateBet('9999', limits);
    expect(result.valid).toBe(false);
    expect(result.reason).toContain('maximum');
  });

  it('rejects a bet larger than the balance', () => {
    const result = validateBet('500', limits);
    expect(result.valid).toBe(false);
    expect(result.reason).toContain('Not enough money');
  });

  it('works when only some limits are supplied', () => {
    expect(validateBet('10', {}).valid).toBe(true);
    expect(validateBet('10', { min: 20 }).valid).toBe(false);
  });
});
