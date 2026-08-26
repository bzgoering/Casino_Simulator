/**
 * Roulette layout geometry for the browser.
 *
 * This mirrors the server's validation so the UI can grey out an impossible chip before the
 * player commits to it. It is a convenience, not a control: the server revalidates every bet
 * against the same rules and is the only authority on what is placeable.
 */

export const POCKET_ORDER = [
  0, 32, 15, 19, 4, 21, 2, 25, 17, 34, 6, 27, 13, 36, 11, 30, 8, 23,
  10, 5, 24, 16, 33, 1, 20, 14, 31, 9, 22, 18, 29, 7, 28, 12, 35, 3, 26,
];

const RED_NUMBERS = new Set([
  1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36,
]);

export const PAYOUTS = {
  STRAIGHT: 35,
  SPLIT: 17,
  STREET: 11,
  CORNER: 8,
  SIX_LINE: 5,
  COLUMN: 2,
  DOZEN: 2,
  COLOR: 1,
  PARITY: 1,
  HALF: 1,
};

export function colorOf(pocket) {
  if (pocket === 0) return 'GREEN';
  return RED_NUMBERS.has(pocket) ? 'RED' : 'BLACK';
}

export function rowOf(n) {
  return Math.floor((n - 1) / 3);
}

export function columnOf(n) {
  return (n - 1) % 3;
}

/** The cloth as rendered: twelve rows of three, 1-36. */
export function layoutGrid() {
  const rows = [];
  for (let row = 0; row < 12; row += 1) {
    rows.push([row * 3 + 1, row * 3 + 2, row * 3 + 3]);
  }
  return rows;
}

/** Which pockets a bet covers, or null if the selection is not placeable. */
export function pocketsFor(type, selection) {
  switch (type) {
    case 'STRAIGHT': {
      const numbers = parseNumbers(selection, 1);
      return numbers && numbers.length === 1 ? numbers : null;
    }
    case 'SPLIT':
      return validateInside(selection, 2, isValidSplit);
    case 'STREET':
      return validateInside(selection, 3, isValidStreet);
    case 'CORNER':
      return validateInside(selection, 4, isValidCorner);
    case 'SIX_LINE':
      return validateInside(selection, 6, isValidSixLine);
    case 'COLOR':
      return selection === 'RED' || selection === 'BLACK'
        ? range(1, 36).filter((n) => colorOf(n) === selection)
        : null;
    case 'PARITY':
      if (selection === 'ODD') return range(1, 36).filter((n) => n % 2 === 1);
      if (selection === 'EVEN') return range(1, 36).filter((n) => n % 2 === 0);
      return null;
    case 'HALF':
      if (selection === 'LOW') return range(1, 18);
      if (selection === 'HIGH') return range(19, 36);
      return null;
    case 'DOZEN': {
      const index = Number(selection);
      if (![1, 2, 3].includes(index)) return null;
      return range((index - 1) * 12 + 1, index * 12);
    }
    case 'COLUMN': {
      const index = Number(selection);
      if (![1, 2, 3].includes(index)) return null;
      return range(1, 36).filter((n) => columnOf(n) === index - 1);
    }
    default:
      return null;
  }
}

function validateInside(selection, size, predicate) {
  const numbers = parseNumbers(selection, size);
  if (!numbers || numbers.length !== size) return null;
  return predicate(numbers) ? numbers : null;
}

function parseNumbers(selection, maxCount) {
  if (typeof selection !== 'string' || selection.trim() === '') return null;
  const parts = selection.split(',');
  if (parts.length > Math.max(maxCount, 6)) return null;

  const numbers = [];
  for (const part of parts) {
    const trimmed = part.trim();
    if (trimmed === '' || !/^\d+$/.test(trimmed)) return null;
    const value = Number(trimmed);
    if (value < 0 || value > 36) return null;
    if (numbers.includes(value)) return null;
    numbers.push(value);
  }
  return numbers.sort((a, b) => a - b);
}

function isValidSplit([low, high]) {
  if (low === 0) return [1, 2, 3].includes(high);
  const sameRow = rowOf(low) === rowOf(high) && Math.abs(columnOf(low) - columnOf(high)) === 1;
  const sameColumn = columnOf(low) === columnOf(high) && Math.abs(rowOf(low) - rowOf(high)) === 1;
  return sameRow || sameColumn;
}

function isValidStreet(numbers) {
  if (sameSet(numbers, [0, 1, 2]) || sameSet(numbers, [0, 2, 3])) return true;
  const [first] = numbers;
  if (first < 1 || columnOf(first) !== 0) return false;
  return sameSet(numbers, [first, first + 1, first + 2]);
}

function isValidCorner(numbers) {
  if (sameSet(numbers, [0, 1, 2, 3])) return true;
  const [topLeft] = numbers;
  if (topLeft < 1 || columnOf(topLeft) === 2 || rowOf(topLeft) >= 11) return false;
  return sameSet(numbers, [topLeft, topLeft + 1, topLeft + 3, topLeft + 4]);
}

function isValidSixLine(numbers) {
  const [first] = numbers;
  if (first < 1 || columnOf(first) !== 0 || rowOf(first) >= 11) return false;
  return sameSet(numbers, range(first, first + 5));
}

function sameSet(a, b) {
  if (a.length !== b.length) return false;
  const sortedA = [...a].sort((x, y) => x - y);
  const sortedB = [...b].sort((x, y) => x - y);
  return sortedA.every((value, index) => value === sortedB[index]);
}

function range(from, to) {
  const values = [];
  for (let n = from; n <= to; n += 1) values.push(n);
  return values;
}

/** True when a chip is placeable, for enabling the confirm button. */
export function isPlaceable(type, selection) {
  return pocketsFor(type, selection) !== null;
}

/** What a winning chip returns, stake included. */
export function payoutFor(type, amount) {
  const odds = PAYOUTS[type];
  if (odds === undefined) return 0;
  return Number((amount * (odds + 1)).toFixed(2));
}
