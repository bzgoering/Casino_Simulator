import { describe, it, expect, afterEach, vi } from 'vitest';
import { createReels } from '../src/games/slot-reels.js';

/**
 * The reels as moving strips.
 *
 * These run the real animation on real timers, so each spin takes a couple of seconds. What they
 * pin down is what a player can check by eye: the reel comes to rest showing the server's window,
 * the symbols just off the glass are that stop's true neighbours on the strip, reels stop left to
 * right, and nothing moves at all for someone who has asked for reduced motion.
 */

const STRIP = ['A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J'];

function mount() {
  document.body.innerHTML = '<div id="window"></div>';
  const container = document.querySelector('#window');
  const reels = createReels(container, {
    count: 3,
    rows: 3,
    strip: STRIP,
    glyph: (symbol) => {
      const span = document.createElement('span');
      span.textContent = symbol;
      return span;
    },
  });
  return { container, reels };
}

/** The three symbols on the glass for one reel. */
const onGlass = (reels, reel) => reels.cells[reel].map((cell) => cell.textContent);

/** Every cell on one reel, top to bottom, including the one above and below the glass. */
const wholeReel = (container, reel) => [...container.querySelectorAll('.reel')[reel]
  .querySelectorAll('.reel-cell')].map((cell) => cell.textContent);

afterEach(() => { vi.unstubAllGlobals(); });

describe('the reels', () => {
  it('rolls into each stop through its real neighbours on the strip', async () => {
    const { container, reels } = mount();

    reels.start();
    // A stop is the centre row: stop 4 shows D E F, stop 0 wraps to J A B.
    await reels.stopOn(
      [['D', 'E', 'F'], ['J', 'A', 'B'], ['I', 'J', 'A']],
      { stops: [4, 0, 9] },
    );

    expect(wholeReel(container, 0)).toEqual(['C', 'D', 'E', 'F', 'G']);
    expect(wholeReel(container, 1)).toEqual(['I', 'J', 'A', 'B', 'C']);
    expect(wholeReel(container, 2)).toEqual(['H', 'I', 'J', 'A', 'B']);
    expect(container.hasAttribute('aria-busy')).toBe(false);
  }, 8000);

  it('shows the server\'s window even where the strip it holds disagrees', async () => {
    const { reels } = mount();

    reels.start();
    await reels.stopOn([['X', 'Y', 'Z'], ['Z', 'Z', 'Z'], ['Y', 'X', 'Y']], { stops: [0, 0, 0] });

    expect(onGlass(reels, 0)).toEqual(['X', 'Y', 'Z']);
    expect(onGlass(reels, 1)).toEqual(['Z', 'Z', 'Z']);
    expect(onGlass(reels, 2)).toEqual(['Y', 'X', 'Y']);
    expect(reels.cells[0][1].getAttribute('aria-label')).toBe('Y');
  }, 8000);

  it('holds a teased reel on after the others have stopped', async () => {
    const { container, reels } = mount();
    const windows = [['A', 'B', 'C'], ['A', 'B', 'C'], ['A', 'B', 'C']];

    reels.start();
    const landed = reels.stopOn(windows, { stops: [1, 1, 1], tease: [2] });
    const lastReel = container.querySelectorAll('.reel')[2];

    await vi.waitFor(() => expect(lastReel.classList.contains('tease')).toBe(true),
      { timeout: 5000 });
    // The first two are already down, left to right, while the third is still turning.
    expect(onGlass(reels, 0)).toEqual(windows[0]);
    expect(onGlass(reels, 1)).toEqual(windows[1]);
    expect(container.getAttribute('aria-busy')).toBe('true');

    await landed;
    expect(lastReel.classList.contains('tease')).toBe(false);
    expect(onGlass(reels, 2)).toEqual(windows[2]);
  }, 10000);

  it('goes back to where it was when the spin never happened', async () => {
    const { reels } = mount();
    const before = [0, 1, 2].map((reel) => onGlass(reels, reel));

    reels.start();
    await reels.halt();

    expect([0, 1, 2].map((reel) => onGlass(reels, reel))).toEqual(before);
  }, 8000);

  it('does not move at all under reduced motion', async () => {
    vi.stubGlobal('matchMedia', () => ({ matches: true }));
    const { container, reels } = mount();
    const transforms = () => [...container.querySelectorAll('.reel-cell')]
      .map((cell) => cell.style.transform);
    const still = transforms();

    reels.start();
    expect(transforms()).toEqual(still);

    const started = performance.now();
    await reels.stopOn([['D', 'E', 'F'], ['D', 'E', 'F'], ['D', 'E', 'F']], { stops: [4, 4, 4] });

    expect(performance.now() - started).toBeLessThan(100);
    expect(onGlass(reels, 0)).toEqual(['D', 'E', 'F']);
  });
});
