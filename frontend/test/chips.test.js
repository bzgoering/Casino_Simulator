import { describe, it, expect } from 'vitest';
import { CHIP_VALUES, chipBreakdown, topChip } from '../src/lib/chips.js';

/**
 * Colouring up: the pile a stake is shown as.
 *
 * The rule is the fewest chips that make the amount, so what sits on a number stays countable
 * however long a player keeps adding singles to it.
 */

describe('the denominations', () => {
  it('runs largest to smallest', () => {
    expect(CHIP_VALUES).toEqual([100, 50, 25, 10, 5, 1]);
  });
});

describe('breaking a stake into chips', () => {
  it('colours five singles up into one $5 chip', () => {
    expect(chipBreakdown(5)).toEqual([5]);
  });

  it('uses one chip where one will do', () => {
    for (const value of CHIP_VALUES) {
      expect(chipBreakdown(value), `$${value}`).toEqual([value]);
    }
  });

  it('puts the largest chips at the bottom of the pile', () => {
    expect(chipBreakdown(136)).toEqual([100, 25, 10, 1]);
  });

  it('leaves a stake that needs several small chips alone', () => {
    expect(chipBreakdown(3)).toEqual([1, 1, 1]);
    expect(chipBreakdown(7)).toEqual([5, 1, 1]);
  });

  it('always adds back up to the stake, and never uses more chips than needed', () => {
    for (let amount = 1; amount <= 300; amount += 1) {
      const pile = chipBreakdown(amount);
      expect(pile.reduce((sum, chip) => sum + chip, 0), `$${amount}`).toBe(amount);
      // Greedy is optimal for this set; nothing should ever need a sixth chip of one kind,
      // because five of any denomination colour up into the next one.
      for (const value of CHIP_VALUES) {
        if (value === 100) continue;
        expect(pile.filter((chip) => chip === value).length, `$${amount} in $${value}s`)
          .toBeLessThanOrEqual(4);
      }
    }
  });

  it('draws nothing for an empty space', () => {
    expect(chipBreakdown(0)).toEqual([]);
    expect(chipBreakdown(-5)).toEqual([]);
    expect(chipBreakdown(undefined)).toEqual([]);
  });
});

describe('the chip on top', () => {
  it('is the smallest one in the pile', () => {
    expect(topChip(101)).toBe(1);
    expect(topChip(100)).toBe(100);
    expect(topChip(30)).toBe(5);
  });

  it('is nothing at all on an empty space', () => {
    expect(topChip(0)).toBe(0);
  });

  it('empties a space in as many removals as it has chips', () => {
    let amount = 137;
    let removals = 0;
    while (amount > 0) {
      amount -= topChip(amount);
      removals += 1;
    }
    expect(removals).toBe(chipBreakdown(137).length);
  });
});
