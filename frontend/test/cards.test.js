import { describe, it, expect } from 'vitest';
import { parseCard, describeHand, formatTotal } from '../src/lib/cards.js';

describe('parseCard', () => {
  it('parses the ace of spades', () => {
    expect(parseCard('AS')).toMatchObject({
      rank: 'A',
      suit: 'spades',
      color: 'black',
      label: 'ace of spades',
    });
  });

  it('parses a ten, whose rank is two characters', () => {
    const card = parseCard('10H');
    expect(card).toMatchObject({ rank: '10', suit: 'hearts', color: 'red' });
    expect(card.label).toBe('ten of hearts');
  });

  it('assigns the right colour to each suit', () => {
    expect(parseCard('2D').color).toBe('red');
    expect(parseCard('2H').color).toBe('red');
    expect(parseCard('2C').color).toBe('black');
    expect(parseCard('2S').color).toBe('black');
  });

  it('returns null for anything it does not recognise', () => {
    expect(parseCard('ZZ')).toBeNull();
    expect(parseCard('1S')).toBeNull();
    expect(parseCard('')).toBeNull();
    expect(parseCard(null)).toBeNull();
    expect(parseCard(42)).toBeNull();
  });
});

describe('describeHand', () => {
  it('lists every card for a screen reader', () => {
    expect(describeHand(['AS', '10H'])).toBe('ace of spades, ten of hearts');
  });

  it('handles an empty or missing hand', () => {
    expect(describeHand([])).toBe('no cards');
    expect(describeHand(undefined)).toBe('no cards');
  });
});

describe('formatTotal', () => {
  it('marks a soft total the way a table would', () => {
    expect(formatTotal(17, true)).toBe('soft 17');
    expect(formatTotal(17, false)).toBe('17');
  });
});
