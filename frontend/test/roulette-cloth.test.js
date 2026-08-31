import { describe, it, expect, beforeEach, vi } from 'vitest';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { createRouletteView } from '../src/games/roulette.js';

/**
 * The rendered cloth, not just the layout maths.
 *
 * The "2 to 1" bug lived entirely in how the cloth was built: every box resolved to the same
 * column, so the layout functions were correct while the screen was not. Testing the DOM is the
 * only place that distinction shows up.
 */

// import.meta.url is an http URL under the jsdom environment, so resolve from the project root.
const html = readFileSync(resolve(process.cwd(), 'index.html'), 'utf8');

function mountRoulette() {
  document.body.innerHTML = html.slice(html.indexOf('<body>') + 6, html.indexOf('</body>'));
  return createRouletteView({
    api: { spinRoulette: vi.fn() },
    onBalance: vi.fn(),
    onError: vi.fn(),
    config: () => ({ minBet: 1, maxBet: 5000, maxRouletteBets: 20, balance: 1000 }),
  });
}

describe('the roulette cloth', () => {
  beforeEach(mountRoulette);

  it('draws exactly three column boxes, one per column', () => {
    const boxes = [...document.querySelectorAll('#roulette-cloth [data-type="COLUMN"]')];

    expect(boxes).toHaveLength(3);
    expect(boxes.map((b) => b.dataset.selection)).toEqual(['1', '2', '3']);
  });

  it('lights only the column box that was clicked', () => {
    const boxes = [...document.querySelectorAll('#roulette-cloth [data-type="COLUMN"]')];

    boxes[1].click();

    expect(boxes[0].classList.contains('has-chip')).toBe(false);
    expect(boxes[1].classList.contains('has-chip')).toBe(true);
    expect(boxes[2].classList.contains('has-chip')).toBe(false);

    const listed = [...document.querySelectorAll('#roulette-bets li')].map((li) => li.textContent);
    expect(listed).toHaveLength(1);
    expect(listed[0]).toContain('Column 2');
  });

  it('still draws every number and the zero', () => {
    const straights = [...document.querySelectorAll('#roulette-cloth [data-type="STRAIGHT"]')];

    expect(straights).toHaveLength(37);
    expect(straights[0].dataset.selection).toBe('0');
  });

  it('puts the bets list under the wheel and the actions beside the chips', () => {
    expect(document.querySelector('.wheel-area .placed #roulette-bets')).not.toBeNull();
    expect(document.querySelector('.chip-row .bet-actions #roulette-spin')).not.toBeNull();
    expect(document.querySelector('.chip-row .bet-actions #roulette-clear')).not.toBeNull();
  });
});
