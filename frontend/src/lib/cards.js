/**
 * Card display helpers.
 *
 * The API sends a card as a short code: rank symbol followed by suit letter, e.g. "AS" for the
 * ace of spades or "10H" for the ten of hearts.
 */

const SUITS = {
  C: { name: 'clubs', symbol: '\u2663', color: 'black' },
  D: { name: 'diamonds', symbol: '\u2666', color: 'red' },
  H: { name: 'hearts', symbol: '\u2665', color: 'red' },
  S: { name: 'spades', symbol: '\u2660', color: 'black' },
};

const RANK_NAMES = {
  A: 'ace', K: 'king', Q: 'queen', J: 'jack', 10: 'ten', 9: 'nine', 8: 'eight',
  7: 'seven', 6: 'six', 5: 'five', 4: 'four', 3: 'three', 2: 'two',
};

/** Splits a card code into its parts, or returns null if the code is not recognised. */
export function parseCard(code) {
  if (typeof code !== 'string' || code.length < 2) {
    return null;
  }
  const suitLetter = code.slice(-1).toUpperCase();
  const rank = code.slice(0, -1).toUpperCase();
  const suit = SUITS[suitLetter];

  if (!suit || !RANK_NAMES[rank]) {
    return null;
  }
  return {
    code: rank + suitLetter,
    rank,
    suit: suit.name,
    suitSymbol: suit.symbol,
    color: suit.color,
    label: `${RANK_NAMES[rank]} of ${suit.name}`,
  };
}

/** Describes a hand for a screen reader, e.g. "ace of spades, king of hearts". */
export function describeHand(codes) {
  if (!Array.isArray(codes) || codes.length === 0) {
    return 'no cards';
  }
  return codes
    .map((code) => parseCard(code)?.label ?? 'unknown card')
    .join(', ');
}

/** Human-readable hand total, marking a soft total the way a table would. */
export function formatTotal(total, soft) {
  if (typeof total !== 'number') {
    return '';
  }
  return soft ? `soft ${total}` : String(total);
}
