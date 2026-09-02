import { describe, it, expect, beforeEach, vi } from 'vitest';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { createSlotsView } from '../src/games/slots.js';

/**
 * The rendered machine, not just the numbers behind it.
 *
 * What matters here is what the glass shows: three symbols on every reel, a legend that makes
 * clear which lines were actually bought, and highlighting that follows the lines the server
 * says paid rather than anything the browser worked out for itself.
 */

// import.meta.url is an http URL under the jsdom environment, so resolve from the project root.
const html = readFileSync(resolve(process.cwd(), 'index.html'), 'utf8');

const PAYLINES = [
  { id: 'MIDDLE', name: 'Middle row', rows: [1, 1, 1] },
  { id: 'TOP', name: 'Top row', rows: [0, 0, 0] },
  { id: 'BOTTOM', name: 'Bottom row', rows: [2, 2, 2] },
  { id: 'DIAGONAL_DOWN', name: 'Diagonal down', rows: [0, 1, 2] },
  { id: 'DIAGONAL_UP', name: 'Diagonal up', rows: [2, 1, 0] },
];

const SLOTS_CONFIG = {
  rtp: 96.005,
  paytable: { 'Three Sevens': 200 },
  creditOptions: [1, 3, 5],
  paylines: PAYLINES,
  maxTotalBet: '5000.00',
};

function mount({ spinSlots = vi.fn() } = {}) {
  document.body.innerHTML = html.slice(html.indexOf('<body>') + 6, html.indexOf('</body>'));
  const onError = vi.fn();
  const view = createSlotsView({
    api: { spinSlots },
    onBalance: vi.fn(),
    onError,
    config: () => ({ balance: 1000, slots: SLOTS_CONFIG }),
  });
  view.describeMachine({ slots: SLOTS_CONFIG });
  return { view, onError };
}

/** A server response with the given window and winning lines. */
function spinResponse({ window, lines, net = -3, balance = 997 }) {
  return {
    roundId: 'r1',
    stops: [0, 0, 0],
    window,
    lines,
    totalMultiplier: lines.reduce((sum, l) => sum + l.multiplier, 0),
    combination: lines.some((l) => l.win) ? 'a win' : 'No win',
    win: lines.some((l) => l.win),
    betPerLine: 1,
    credits: lines.length,
    totalStaked: lines.length,
    payout: 0,
    net,
    balance,
  };
}

describe('the slot machine', () => {
  beforeEach(() => { mount(); });

  it('shows a three-by-three window: three symbols on every reel', () => {
    const reels = [...document.querySelectorAll('#slot-window .reel')];

    expect(reels).toHaveLength(3);
    for (const reel of reels) {
      expect(reel.querySelectorAll('.stop')).toHaveLength(3);
    }
  });

  it('offers the fixed credit buttons and nothing in between', () => {
    const buttons = [...document.querySelectorAll('.credit-btn')];

    expect(buttons.map((b) => b.textContent)).toEqual(['1', '3', '5']);
    // The bet is free-form, the credits are not.
    expect(document.querySelector('#slots-bet').type).toBe('number');
  });

  it('has no minimum bet on the field', () => {
    // A machine takes any denomination; only the cent that two decimals express is a floor.
    expect(document.querySelector('#slots-bet').min).toBe('0.01');
  });

  it('lights the centre line on one credit, adding rows then diagonals', () => {
    const lit = () => [...document.querySelectorAll('#payline-key li.lit')]
      .map((li) => li.dataset.payline);

    expect(lit()).toEqual(['MIDDLE']);

    document.querySelector('.credit-btn[data-credits="3"]').click();
    expect(lit()).toEqual(['MIDDLE', 'TOP', 'BOTTOM']);

    document.querySelector('.credit-btn[data-credits="5"]').click();
    expect(lit()).toEqual(['MIDDLE', 'TOP', 'BOTTOM', 'DIAGONAL_DOWN', 'DIAGONAL_UP']);
  });

  it('shows the lines that were not bought as dark rather than hiding them', () => {
    const dark = [...document.querySelectorAll('#payline-key li.dark')]
      .map((li) => li.dataset.payline);

    expect(dark).toEqual(['TOP', 'BOTTOM', 'DIAGONAL_DOWN', 'DIAGONAL_UP']);
  });

  it('spells out what the spin will cost, since every lit line is charged the bet', () => {
    document.querySelector('#slots-bet').value = '0.25';
    document.querySelector('#slots-bet').dispatchEvent(new Event('input'));
    expect(document.querySelector('#slots-stake').textContent).toContain('$0.25');

    document.querySelector('.credit-btn[data-credits="5"]').click();
    expect(document.querySelector('#slots-stake').textContent).toContain('$1.25');
  });
});

describe('settling a spin', () => {
  it('highlights a diagonal win on the cells the server named', async () => {
    const window3x3 = [
      ['SEVEN', 'BELL', 'PLUM'],
      ['BELL', 'SEVEN', 'PLUM'],
      ['BELL', 'PLUM', 'SEVEN'],
    ];
    const spinSlots = vi.fn().mockResolvedValue(spinResponse({
      window: window3x3,
      lines: [
        { payline: 'MIDDLE', name: 'Middle row', rows: [1, 1, 1], symbols: ['BELL', 'SEVEN', 'PLUM'], multiplier: 0, payout: 0, combination: 'No win', win: false },
        { payline: 'DIAGONAL_DOWN', name: 'Diagonal down', rows: [0, 1, 2], symbols: ['SEVEN', 'SEVEN', 'SEVEN'], multiplier: 200, payout: 200, combination: 'Three Sevens', win: true },
      ],
      net: 198,
      balance: 1198,
    }));

    mount({ spinSlots });
    document.querySelector('#slots-spin').click();
    await vi.waitFor(() => {
      expect(document.querySelectorAll('#slot-window .stop.win')).toHaveLength(3);
    }, { timeout: 5000 });

    // The diagonal reads row 0 of reel 0, row 1 of reel 1, row 2 of reel 2.
    const reels = [...document.querySelectorAll('#slot-window .reel')];
    expect(reels[0].querySelectorAll('.stop')[0].classList.contains('win')).toBe(true);
    expect(reels[1].querySelectorAll('.stop')[1].classList.contains('win')).toBe(true);
    expect(reels[2].querySelectorAll('.stop')[2].classList.contains('win')).toBe(true);
    // The losing centre line is not highlighted.
    expect(reels[0].querySelectorAll('.stop')[1].classList.contains('win')).toBe(false);

    const wins = [...document.querySelectorAll('#slots-line-wins li')].map((li) => li.textContent);
    expect(wins).toHaveLength(1);
    expect(wins[0]).toContain('Diagonal down');
    expect(wins[0]).toContain('Three Sevens');
  });

  it('renders the whole window, including rows the player did not buy', async () => {
    const window3x3 = [
      ['CHERRY', 'BELL', 'PLUM'],
      ['ORANGE', 'BELL', 'SEVEN'],
      ['BAR1', 'BELL', 'PLUM'],
    ];
    const spinSlots = vi.fn().mockResolvedValue(spinResponse({
      window: window3x3,
      lines: [
        { payline: 'MIDDLE', name: 'Middle row', rows: [1, 1, 1], symbols: ['BELL', 'BELL', 'BELL'], multiplier: 20, payout: 20, combination: 'Three Bells', win: true },
      ],
      net: 19,
      balance: 1019,
    }));

    mount({ spinSlots });
    document.querySelector('#slots-spin').click();
    await vi.waitFor(() => {
      expect(document.querySelectorAll('#slot-window .stop.win')).toHaveLength(3);
    }, { timeout: 5000 });

    const reels = [...document.querySelectorAll('#slot-window .reel')];
    // Every cell carries a symbol, not only the paid line.
    for (let reel = 0; reel < 3; reel += 1) {
      for (let row = 0; row < 3; row += 1) {
        expect(reels[reel].querySelectorAll('.stop')[row].textContent).not.toBe('');
      }
    }
  });

  it('refuses a bet of zero without calling the server', () => {
    const spinSlots = vi.fn();
    const { onError } = mount({ spinSlots });

    document.querySelector('#slots-bet').value = '0';
    document.querySelector('#slots-spin').click();

    expect(spinSlots).not.toHaveBeenCalled();
    expect(onError).toHaveBeenCalledWith('Enter a bet.');
  });

  it('sends the chosen credits with the bet', async () => {
    const spinSlots = vi.fn().mockResolvedValue(spinResponse({
      window: [['BELL', 'BELL', 'BELL'], ['BELL', 'BELL', 'BELL'], ['BELL', 'BELL', 'BELL']],
      lines: [],
    }));
    mount({ spinSlots });

    document.querySelector('#slots-bet').value = '0.05';
    document.querySelector('.credit-btn[data-credits="3"]').click();
    document.querySelector('#slots-spin').click();

    await vi.waitFor(() => expect(spinSlots).toHaveBeenCalledWith(0.05, 3), { timeout: 5000 });
  });
});
