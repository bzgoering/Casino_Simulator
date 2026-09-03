import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { createRouletteView } from '../src/games/roulette.js';
import { POCKET_ORDER, colorOf, labelOf } from '../src/lib/roulette-layout.js';

/**
 * The wheel: what the animation shows against what the server actually decided.
 *
 * Two faults lived here. The marker was a child of the wheel, so it turned with it and pointed
 * at the same pocket for ever. And each spin computed an absolute rotation, so the second one
 * asked the wheel to go somewhere it already was: it twitched, or unwound, and stopped on a
 * pocket that had nothing to do with the number being announced beside it.
 */

// import.meta.url is an http URL under the jsdom environment, so resolve from the project root.
const html = readFileSync(resolve(process.cwd(), 'index.html'), 'utf8');

const SLICE = 360 / POCKET_ORDER.length;

function resultFor(wheelIndex) {
  return {
    wheelIndex,
    pocket: labelOf(POCKET_ORDER[wheelIndex]),
    color: colorOf(POCKET_ORDER[wheelIndex]),
    net: '-1.00',
    balance: '999.00',
    bets: [{ type: 'STRAIGHT', selection: '17', won: false, payout: '0.00' }],
  };
}

function mountRoulette(spinRoulette) {
  document.body.innerHTML = html.slice(html.indexOf('<body>') + 6, html.indexOf('</body>'));
  return createRouletteView({
    api: { spinRoulette: spinRoulette ?? vi.fn() },
    onBalance: vi.fn(),
    onError: vi.fn(),
    config: () => ({ minBet: 1, maxBet: 5000, balance: 100000 }),
  });
}

const wheelNode = () => document.querySelector('#roulette-wheel');

/** The degrees in `rotate(Ndeg)`, or 0 before the wheel has ever been spun. */
function rotationOf() {
  const match = /rotate\(([-\d.]+)deg\)/.exec(wheelNode().style.transform);
  return match ? Number.parseFloat(match[1]) : 0;
}

/** Where a pocket ends up relative to the marker, as a distance in degrees from dead top. */
function offsetFromMarker(wheelIndex) {
  const pocketAngle = wheelIndex * SLICE + SLICE / 2;
  const settled = (((rotationOf() + pocketAngle) % 360) + 360) % 360;
  return Math.min(settled, 360 - settled);
}

/** Runs a spin to completion. Reduced motion is on, so the wheel lands on a zero-length timer. */
async function spinTo(wheelIndex, spinRoulette) {
  spinRoulette.mockResolvedValueOnce(resultFor(wheelIndex));
  document.querySelector('#roulette-spin').click();
  for (let i = 0; i < 4; i += 1) await new Promise((r) => { setTimeout(r, 0); });
}

describe('the wheel face', () => {
  beforeEach(() => mountRoulette());

  it('keeps the marker out of the wheel, so it cannot turn with it', () => {
    // The bug in one assertion: the marker used to be appended inside #roulette-wheel.
    expect(document.querySelector('#roulette-wheel .wheel-marker')).toBeNull();
    expect(document.querySelector('.wheel-frame > .wheel-marker')).not.toBeNull();
  });

  it('carries the wheel and the marker in a frame that does not move', () => {
    const frame = document.querySelector('.wheel-frame');

    expect(frame.querySelector('#roulette-wheel')).not.toBeNull();
    expect(frame.querySelector('.wheel-marker')).not.toBeNull();
  });

  it('prints every pocket number on the wheel, in wheel order', () => {
    const numbers = [...document.querySelectorAll('#roulette-wheel .wheel-number')];

    expect(numbers).toHaveLength(38);
    expect(numbers.map((n) => n.dataset.pocket)).toEqual(POCKET_ORDER.map(labelOf));
    // The double zero is written as the cloth writes it, not as the number it is held as.
    expect(numbers.map((n) => n.dataset.pocket)).toContain('00');
  });

  it('sets each number out along its own wedge', () => {
    const numbers = [...document.querySelectorAll('#roulette-wheel .wheel-number')];

    numbers.forEach((node, index) => {
      const angle = Number.parseFloat(/rotate\(([-\d.]+)deg\)/.exec(node.style.transform)[1]);
      // The middle of the wedge, which is where the marker will look for it.
      expect(angle, `pocket ${node.dataset.pocket}`)
        .toBeCloseTo(index * SLICE + SLICE / 2, 2);
      expect(node.style.transform).toContain('translateY(-');
    });
  });

  it('turns the numbers with the wheel, since they are part of it', () => {
    expect(document.querySelectorAll('#roulette-wheel .wheel-number').length).toBeGreaterThan(0);
  });
});

describe('spinning to the pocket the server chose', () => {
  let spinRoulette;

  beforeEach(() => {
    // Reduced motion: the landing is what is under test, not the 3.2 seconds of gliding.
    globalThis.matchMedia = () => ({ matches: true });
    spinRoulette = vi.fn();
    mountRoulette(spinRoulette);
    document.querySelector('#roulette-cloth [data-selection="17"]').click();
  });

  afterEach(() => { delete globalThis.matchMedia; });

  it('stops with the winning pocket under the marker', async () => {
    await spinTo(0, spinRoulette);

    expect(offsetFromMarker(0)).toBeLessThan(0.001);
  });

  it('lands correctly wherever on the wheel the pocket is', async () => {
    for (const index of [0, 1, 9, 19, 27, 37]) {
      mountRoulette(spinRoulette);
      document.querySelector('#roulette-cloth [data-selection="17"]').click();

      await spinTo(index, spinRoulette);

      expect(offsetFromMarker(index), `pocket index ${index}`).toBeLessThan(0.001);
    }
  });

  it('keeps landing correctly on spin after spin', async () => {
    // The regression: the second spin used to compute a target near where the wheel already
    // was, so it stopped nowhere near the number it had just announced.
    for (const index of [12, 3, 30, 7, 21]) {
      await spinTo(index, spinRoulette);

      expect(offsetFromMarker(index), `pocket index ${index}`).toBeLessThan(0.001);
    }
  });

  it('always turns forwards, never unwinding to reach the next pocket', async () => {
    let previous = rotationOf();

    for (const index of [12, 3, 30, 7, 21]) {
      await spinTo(index, spinRoulette);

      expect(rotationOf(), `pocket index ${index}`).toBeGreaterThan(previous);
      previous = rotationOf();
    }
  });

  it('gives every spin at least five full turns, so it reads as a spin', async () => {
    let previous = rotationOf();

    for (const index of [12, 3, 30]) {
      await spinTo(index, spinRoulette);

      expect(rotationOf() - previous, `pocket index ${index}`).toBeGreaterThanOrEqual(360 * 5);
      previous = rotationOf();
    }
  });

  it('never stops quoting what a standing bet could pay', async () => {
    const listed = () => document.querySelector('#roulette-bets').textContent;

    // Before any spin.
    expect(listed()).toContain('pays');

    // And once it has settled. The chips are still on the cloth riding on that figure, so it
    // has to stay on screen; it used to be replaced by the result and then by a dash.
    await spinTo(5, spinRoulette);
    expect(listed()).toContain('pays');

    // And through the next spin.
    spinRoulette.mockReturnValueOnce(new Promise(() => {}));
    document.querySelector('#roulette-spin').click();
    expect(listed()).toContain('pays');
  });

  it('says whether a settled bet won or lost, rather than only colouring the row', async () => {
    await spinTo(5, spinRoulette);

    const row = document.querySelector('#roulette-bets li');

    expect(['won', 'lost']).toContain(row.className);
    expect(['Won', 'Lost']).toContain(row.querySelector('.bet-outcome').textContent);
    // The outcome sits beside the figure, not on top of it.
    expect(row.textContent).toContain('pays');
  });

  it('carries no outcome marker before a spin, since there is no outcome yet', () => {
    const row = document.querySelector('#roulette-bets li');

    expect(row.className).toBe('');
    expect(row.querySelector('.bet-outcome')).toBeNull();
    expect(row.textContent).toContain('pays');
  });

  it('clears the last winning number off the cloth when the next spin starts', async () => {
    await spinTo(5, spinRoulette);
    const won = document.querySelector('#roulette-cloth .cell.winner');
    expect(won).not.toBeNull();

    spinRoulette.mockReturnValueOnce(new Promise(() => {}));
    document.querySelector('#roulette-spin').click();

    // Otherwise the previous number stays lit through a spin it has nothing to do with.
    expect(document.querySelector('#roulette-cloth .cell.winner')).toBeNull();
  });

  it('keeps the chips on the cloth across a spin, so a repeat bet is one click', async () => {
    await spinTo(5, spinRoulette);

    const cell = document.querySelector('#roulette-cloth [data-selection="17"]');
    expect(cell.querySelectorAll('.chip-token')).toHaveLength(1);
    expect(document.querySelector('#roulette-spin').disabled).toBe(false);
  });

  it('announces the pocket it stopped on', async () => {
    await spinTo(5, spinRoulette);

    expect(document.querySelector('#roulette-result .pocket-badge').textContent)
      .toBe(labelOf(POCKET_ORDER[5]));
    // What is announced and where the wheel came to rest are the same pocket.
    expect(offsetFromMarker(5)).toBeLessThan(0.001);
  });
});
