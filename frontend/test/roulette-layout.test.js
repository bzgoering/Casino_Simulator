import { describe, it, expect } from 'vitest';
import {
  POCKET_ORDER, colorOf, pocketsFor, isPlaceable, payoutFor, layoutGrid,
} from '../src/lib/roulette-layout.js';

/**
 * These mirror the server's rules. The client copy exists to grey out impossible chips before
 * the player commits; the server revalidates everything, so a divergence here is a UX bug
 * rather than a way to win money.
 */
describe('the wheel', () => {
  it('is a single-zero European wheel of 37 pockets', () => {
    expect(POCKET_ORDER).toHaveLength(37);
    expect(new Set(POCKET_ORDER).size).toBe(37);
    expect(POCKET_ORDER).toContain(0);
    expect(POCKET_ORDER).not.toContain(37);
  });

  it('matches the physical pocket sequence used by the server', () => {
    expect(POCKET_ORDER.slice(0, 6)).toEqual([0, 32, 15, 19, 4, 21]);
    expect(POCKET_ORDER.at(-1)).toBe(26);
  });

  it('splits 18 red and 18 black with a green zero', () => {
    const reds = [];
    const blacks = [];
    for (let n = 1; n <= 36; n += 1) {
      (colorOf(n) === 'RED' ? reds : blacks).push(n);
    }
    expect(reds).toHaveLength(18);
    expect(blacks).toHaveLength(18);
    expect(colorOf(0)).toBe('GREEN');
  });
});

describe('the betting cloth', () => {
  it('lays 1-36 out in twelve rows of three', () => {
    const grid = layoutGrid();
    expect(grid).toHaveLength(12);
    expect(grid[0]).toEqual([1, 2, 3]);
    expect(grid[11]).toEqual([34, 35, 36]);
  });
});

describe('resolving a bet to its pockets', () => {
  it('resolves a straight-up to one number', () => {
    expect(pocketsFor('STRAIGHT', '17')).toEqual([17]);
  });

  it('accepts splits that touch on the cloth', () => {
    expect(pocketsFor('SPLIT', '1,2')).toEqual([1, 2]);
    expect(pocketsFor('SPLIT', '1,4')).toEqual([1, 4]);
    expect(pocketsFor('SPLIT', '0,1')).toEqual([0, 1]);
  });

  it('rejects splits that do not touch', () => {
    expect(pocketsFor('SPLIT', '1,5')).toBeNull();
    expect(pocketsFor('SPLIT', '3,4')).toBeNull();
    expect(pocketsFor('SPLIT', '1,36')).toBeNull();
  });

  it('accepts a printed row as a street and rejects anything else', () => {
    expect(pocketsFor('STREET', '1,2,3')).toEqual([1, 2, 3]);
    expect(pocketsFor('STREET', '0,1,2')).toEqual([0, 1, 2]);
    expect(pocketsFor('STREET', '2,3,4')).toBeNull();
  });

  it('accepts a square as a corner and rejects a line of four', () => {
    expect(pocketsFor('CORNER', '1,2,4,5')).toEqual([1, 2, 4, 5]);
    expect(pocketsFor('CORNER', '0,1,2,3')).toEqual([0, 1, 2, 3]);
    expect(pocketsFor('CORNER', '1,2,3,4')).toBeNull();
    expect(pocketsFor('CORNER', '3,4,6,7')).toBeNull();
  });

  it('accepts two adjacent streets as a six line', () => {
    expect(pocketsFor('SIX_LINE', '1,2,3,4,5,6')).toEqual([1, 2, 3, 4, 5, 6]);
    expect(pocketsFor('SIX_LINE', '2,3,4,5,6,7')).toBeNull();
  });

  it('resolves the outside bets to the right groups', () => {
    expect(pocketsFor('COLOR', 'RED')).toHaveLength(18);
    expect(pocketsFor('COLOR', 'RED')).not.toContain(0);
    expect(pocketsFor('PARITY', 'ODD')).toHaveLength(18);
    expect(pocketsFor('HALF', 'LOW')).toEqual(expect.arrayContaining([1, 18]));
    expect(pocketsFor('HALF', 'LOW')).not.toContain(19);
    expect(pocketsFor('DOZEN', '2')).toEqual(expect.arrayContaining([13, 24]));
    expect(pocketsFor('COLUMN', '1')).toEqual(expect.arrayContaining([1, 4, 34]));
  });

  it('rejects unrecognised outside selections', () => {
    expect(pocketsFor('COLOR', 'GREEN')).toBeNull();
    expect(pocketsFor('DOZEN', '4')).toBeNull();
    expect(pocketsFor('COLUMN', '0')).toBeNull();
    expect(pocketsFor('NONSENSE', '1')).toBeNull();
  });

  it('rejects a forged split that would pay 17:1 on six numbers', () => {
    expect(pocketsFor('SPLIT', '1,2,3,4,5,6')).toBeNull();
    expect(isPlaceable('SPLIT', '1,2,3,4,5,6')).toBe(false);
  });

  it('rejects numbers that are not on the wheel', () => {
    expect(pocketsFor('STRAIGHT', '37')).toBeNull();
    expect(pocketsFor('STRAIGHT', '-1')).toBeNull();
    expect(pocketsFor('STRAIGHT', '999')).toBeNull();
  });

  it('rejects malformed selections', () => {
    expect(pocketsFor('STRAIGHT', '')).toBeNull();
    expect(pocketsFor('STRAIGHT', 'abc')).toBeNull();
    expect(pocketsFor('STRAIGHT', '1.5')).toBeNull();
    expect(pocketsFor('SPLIT', '17,')).toBeNull();
    expect(pocketsFor('SPLIT', '17,17')).toBeNull();
  });

  it('always covers exactly the number of pockets the odds assume', () => {
    const cases = [
      ['STRAIGHT', '5', 1],
      ['SPLIT', '5,8', 2],
      ['STREET', '4,5,6', 3],
      ['CORNER', '4,5,7,8', 4],
      ['SIX_LINE', '4,5,6,7,8,9', 6],
      ['COLUMN', '2', 12],
      ['DOZEN', '2', 12],
      ['COLOR', 'BLACK', 18],
      ['PARITY', 'ODD', 18],
      ['HALF', 'LOW', 18],
    ];
    for (const [type, selection, size] of cases) {
      expect(pocketsFor(type, selection), `${type} ${selection}`).toHaveLength(size);
    }
  });
});

describe('payouts', () => {
  it('returns stake plus winnings', () => {
    expect(payoutFor('STRAIGHT', 1)).toBe(36);
    expect(payoutFor('COLOR', 10)).toBe(20);
    expect(payoutFor('DOZEN', 5)).toBe(15);
  });

  it('carries the same 2.70% house edge on every bet type', () => {
    const types = ['STRAIGHT', 'SPLIT', 'STREET', 'CORNER', 'SIX_LINE', 'COLUMN', 'DOZEN', 'COLOR', 'PARITY', 'HALF'];
    for (const type of types) {
      const covered = pocketsFor(type, sampleSelection(type)).length;
      const edge = 1 - (covered / 37) * (payoutFor(type, 1));
      expect(edge, type).toBeCloseTo(0.027027, 5);
    }
  });
});

function sampleSelection(type) {
  return {
    STRAIGHT: '5', SPLIT: '5,8', STREET: '4,5,6', CORNER: '4,5,7,8',
    SIX_LINE: '4,5,6,7,8,9', COLUMN: '2', DOZEN: '2',
    COLOR: 'BLACK', PARITY: 'ODD', HALF: 'LOW',
  }[type];
}
