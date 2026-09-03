/**
 * Roulette layout geometry for the browser.
 *
 * This mirrors the server's validation so the UI can grey out an impossible chip before the
 * player commits to it. It is a convenience, not a control: the server revalidates every bet
 * against the same rules and is the only authority on what is placeable.
 */

/**
 * The double zero, held as 37 so a pocket stays a number here as it does on the server.
 * It is written out as "00" by labelOf, which is the only spelling the cloth or the API uses.
 */
export const DOUBLE_ZERO = 37;

/** The physical clockwise order of an American wheel. The two greens sit opposite each other. */
export const POCKET_ORDER = [
  0, 28, 9, 26, 30, 11, 7, 20, 32, 17, 5, 22, 34, 15, 3, 24, 36, 13, 1,
  DOUBLE_ZERO, 27, 10, 25, 29, 12, 8, 19, 31, 18, 6, 21, 33, 16, 4, 23, 35, 14, 2,
];

/** How a pocket is written. The only place 37 becomes "00". */
export function labelOf(pocket) {
  return pocket === DOUBLE_ZERO ? '00' : String(pocket);
}

const RED_NUMBERS = new Set([
  1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36,
]);

export const PAYOUTS = {
  STRAIGHT: 35,
  // The five-number bet, American cloths only. Five pockets in 38 at 6:1 is a 7.89% edge
  // against 5.26% everywhere else, which is why it is the one bet worth naming as a bad one.
  TOP_LINE: 6,
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
  if (pocket === 0 || pocket === DOUBLE_ZERO) return 'GREEN';
  return RED_NUMBERS.has(pocket) ? 'RED' : 'BLACK';
}

export function isGreen(pocket) {
  return pocket === 0 || pocket === DOUBLE_ZERO;
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
    case 'TOP_LINE':
      return validateInside(selection, 5, isValidTopLine);
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
    const value = parsePocket(part);
    if (value === null) return null;
    if (numbers.includes(value)) return null;
    numbers.push(value);
  }
  return numbers.sort((a, b) => a - b);
}

/**
 * Reads a pocket as the cloth writes it, or null if it names none.
 *
 * Strict in the same two ways the server is: "37" is refused even though that is how the double
 * zero is held, and a padded number is refused so "00" cannot be reached by any other spelling.
 */
export function parsePocket(text) {
  if (typeof text !== 'string') return null;
  const trimmed = text.trim();
  if (trimmed === '00') return DOUBLE_ZERO;
  if (!/^[0-9]{1,2}$/.test(trimmed)) return null;
  if (trimmed.length > 1 && trimmed.startsWith('0')) return null;
  const value = Number(trimmed);
  return value >= 0 && value <= 36 ? value : null;
}

// The bets that touch a green, which is where an American cloth differs from a European one.
// A green has no row or column, so the arithmetic below cannot speak for it.
const GREEN_SPLITS = [[0, DOUBLE_ZERO], [0, 1], [0, 2], [2, DOUBLE_ZERO], [3, DOUBLE_ZERO]];
const GREEN_TRIOS = [[0, 1, 2], [0, 2, DOUBLE_ZERO], [2, 3, DOUBLE_ZERO]];
const TOP_LINE_POCKETS = [0, 1, 2, 3, DOUBLE_ZERO];

function touchesGreen(numbers) {
  return numbers.some(isGreen);
}

function isValidSplit(numbers) {
  if (touchesGreen(numbers)) return GREEN_SPLITS.some((pair) => sameSet(numbers, pair));
  const [low, high] = numbers;
  const sameRow = rowOf(low) === rowOf(high) && Math.abs(columnOf(low) - columnOf(high)) === 1;
  const sameColumn = columnOf(low) === columnOf(high) && Math.abs(rowOf(low) - rowOf(high)) === 1;
  return sameRow || sameColumn;
}

/** Both greens and the first street. The only bet an American cloth adds. */
function isValidTopLine(numbers) {
  return sameSet(numbers, TOP_LINE_POCKETS);
}

function isValidStreet(numbers) {
  if (touchesGreen(numbers)) return GREEN_TRIOS.some((trio) => sameSet(numbers, trio));
  const [first] = numbers;
  if (first < 1 || columnOf(first) !== 0) return false;
  return sameSet(numbers, [first, first + 1, first + 2]);
}

function isValidCorner(numbers) {
  // No green corner here: the European 0-1-2-3 basket paid 8:1, and on this cloth those
  // pockets are the five-number bet at 6:1 instead.
  if (touchesGreen(numbers)) return false;
  const [topLeft] = numbers;
  if (topLeft < 1 || columnOf(topLeft) === 2 || rowOf(topLeft) >= 11) return false;
  return sameSet(numbers, [topLeft, topLeft + 1, topLeft + 3, topLeft + 4]);
}

function isValidSixLine(numbers) {
  if (touchesGreen(numbers)) return false;
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
