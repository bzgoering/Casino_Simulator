import { describe, it, expect } from 'vitest';
import { ageOn, verifyAge, todayIso, LEGAL_AGE } from '../src/lib/age.js';

/**
 * The sign-up age check.
 *
 * `today` is passed in throughout so the boundary cases are fixed dates rather than whatever
 * day the suite happens to run on. The date of birth is only ever an argument here: the module
 * holds no state, so there is nothing for a test to assert was cleared.
 */

// Local, not UTC: parsing a date field's value must not shift a day either way.
const on = (iso) => {
  const [y, m, d] = iso.split('-').map(Number);
  return new Date(y, m - 1, d, 12);
};

describe('the legal age', () => {
  it('is 21', () => {
    expect(LEGAL_AGE).toBe(21);
  });
});

describe('working out an age', () => {
  it('counts whole years', () => {
    expect(ageOn('1990-06-15', on('2026-09-03'))).toBe(36);
  });

  it('does not count a birthday that has not come round yet this year', () => {
    expect(ageOn('1990-12-25', on('2026-09-03'))).toBe(35);
  });

  it('counts the birthday itself', () => {
    expect(ageOn('1990-09-03', on('2026-09-03'))).toBe(36);
  });

  it('is a day out on nothing, leap-day births included', () => {
    // 29 February 2004: the day before the anniversary is still 20, the day after is 21.
    expect(ageOn('2004-02-29', on('2025-02-28'))).toBe(20);
    expect(ageOn('2004-02-29', on('2025-03-01'))).toBe(21);
  });
});

describe('the verdict on a date of birth', () => {
  it('lets through somebody who turned 21 today', () => {
    expect(verifyAge('2005-09-03', on('2026-09-03'))).toBe('eligible');
  });

  it('turns away somebody whose 21st birthday is tomorrow', () => {
    expect(verifyAge('2005-09-04', on('2026-09-03'))).toBe('underage');
  });

  it('calls anyone under 21 underage rather than invalid, since they still get a guest seat', () => {
    expect(verifyAge('2010-01-01', on('2026-09-03'))).toBe('underage');
  });

  it.each([
    ['nothing at all', ''],
    ['a date that does not exist', '2001-02-30'],
    ['a month that does not exist', '2001-13-01'],
    ['prose', 'a while ago'],
    ['a future date', '2030-01-01'],
    ['a partial date', '2001-02'],
  ])('rejects %s', (_name, value) => {
    expect(verifyAge(value, on('2026-09-03'))).toBe('invalid');
  });

  it('rejects a missing value without throwing', () => {
    expect(verifyAge(undefined, on('2026-09-03'))).toBe('invalid');
    expect(verifyAge(null, on('2026-09-03'))).toBe('invalid');
  });

  it('returns only a verdict, never the date it was given', () => {
    // The point of the whole module: what comes back cannot be turned back into a birth date.
    expect(['eligible', 'underage', 'invalid']).toContain(verifyAge('1990-06-15', on('2026-09-03')));
  });
});

describe('the cap on the date field', () => {
  it('is today, zero-padded so the field accepts it', () => {
    expect(todayIso(on('2026-09-03'))).toBe('2026-09-03');
    expect(todayIso(on('2026-12-25'))).toBe('2026-12-25');
  });
});
