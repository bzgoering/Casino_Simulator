/**
 * The sign-up age check.
 *
 * A date of birth is a piece of information the casino has decided not to have. So the whole
 * check lives here, in the browser: a date goes in, a verdict comes out, and the date itself is
 * never returned, never stored and never sent anywhere. Nothing in this module keeps state
 * between calls, so there is no copy of the date left behind once a call has returned.
 */

/** The age at which an account may be held. */
export const LEGAL_AGE = 21;

/**
 * Splits an `<input type="date">` value into calendar parts, or null if it is not a real date.
 *
 * Deliberately not `new Date(value)`: that reads a bare `YYYY-MM-DD` as UTC midnight, so west of
 * Greenwich the date comes back as the day before the one that was typed, which is enough to
 * turn somebody's twenty-first birthday into the day they are still twenty. It also accepts
 * dates that do not exist, rolling 31 February forward into March rather than rejecting it.
 */
function parseDateParts(value) {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(String(value ?? '').trim());
  if (!match) return null;

  const year = Number(match[1]);
  const month = Number(match[2]);
  const day = Number(match[3]);

  // A local Date normalises out-of-range parts, so a value that survives the round trip
  // unchanged is a day that actually exists.
  const date = new Date(year, month - 1, day);
  if (date.getFullYear() !== year || date.getMonth() !== month - 1 || date.getDate() !== day) {
    return null;
  }
  return { year, month, day };
}

/**
 * Whole years old on `today`, counted on the calendar.
 *
 * Counting elapsed days and dividing would drift: a year is not a fixed number of days, and the
 * error lands exactly on the birthdays of people born either side of a leap day.
 *
 * @returns {number|null} the age, or null if `value` is not a real past date.
 */
export function ageOn(value, today = new Date()) {
  const dob = parseDateParts(value);
  if (!dob) return null;

  let age = today.getFullYear() - dob.year;
  const months = (today.getMonth() + 1) - dob.month;
  // Their birthday has not come round yet this year, so they are still a year younger.
  if (months < 0 || (months === 0 && today.getDate() < dob.day)) age -= 1;

  return age < 0 ? null : age;
}

/**
 * The verdict on one date of birth: `'eligible'`, `'underage'`, or `'invalid'` for anything that
 * is not a real date already past.
 *
 * The caller gets this and nothing else. It is the only thing that outlives the date.
 */
export function verifyAge(value, today = new Date()) {
  const age = ageOn(value, today);
  if (age === null) return 'invalid';
  return age >= LEGAL_AGE ? 'eligible' : 'underage';
}

/** Today as `YYYY-MM-DD`, for capping the date field so no future date can be picked. */
export function todayIso(today = new Date()) {
  const month = String(today.getMonth() + 1).padStart(2, '0');
  const day = String(today.getDate()).padStart(2, '0');
  return `${today.getFullYear()}-${month}-${day}`;
}
