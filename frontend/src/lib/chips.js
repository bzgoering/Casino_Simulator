/**
 * Casino chips: what they are worth, and how a stake is shown as a pile of them.
 *
 * One list drives both the picker the player chooses from and the piles drawn on the cloth, so
 * a denomination cannot exist as a button without the cloth knowing how to draw it.
 */

/** Denominations, largest first, which is also the order a pile is built in. */
export const CHIP_VALUES = [100, 50, 25, 10, 5, 1];

/**
 * The pile a stake is drawn as: the fewest chips that add up to it, largest first.
 *
 * This is a dealer colouring up. Five singles are not left sitting on the number as five
 * singles; they become one $5 chip, and the pile stays readable however long the player keeps
 * adding to it.
 *
 * Greedy is optimal for this set, the way it is for coins: every denomination divides evenly
 * into the ones above it or is reached by them, so taking the largest that fits never forces
 * more small chips later.
 *
 * @param {number} amount a whole-dollar stake
 * @returns {number[]} the chips, largest first, so index 0 sits at the bottom of the pile
 */
export function chipBreakdown(amount) {
  const chips = [];
  // Chips are whole dollars, so a stake built from them is too; rounding guards against a
  // fractional stray leaving a remainder that no chip could represent.
  let left = Math.round(Number(amount) || 0);
  if (left <= 0) return chips;

  for (const value of CHIP_VALUES) {
    while (left >= value) {
      chips.push(value);
      left -= value;
    }
  }
  return chips;
}

/**
 * The chip on top of the pile, which is the smallest one in it.
 *
 * This is what a right-click takes back off, so removal follows the pile that is actually on
 * screen rather than a history of clicks the player can no longer see.
 *
 * @returns {number} the top chip's value, or 0 if there is no pile
 */
export function topChip(amount) {
  const pile = chipBreakdown(amount);
  return pile.length ? pile[pile.length - 1] : 0;
}
