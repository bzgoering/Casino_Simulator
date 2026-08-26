import { el, clear, setText, qs, qsa } from '../lib/dom.js';
import { formatMoney, formatDelta, validateBet } from '../lib/money.js';

const SYMBOL_GLYPHS = {
  CHERRY: '\u{1F352}',
  ORANGE: '\u{1F34A}',
  PLUM: '\u{1F347}',
  BELL: '\u{1F514}',
  BAR1: 'BAR',
  BAR2: 'BAR\u00d72',
  BAR3: 'BAR\u00d73',
  SEVEN: '7\u20e3',
};

/**
 * Slots view.
 *
 * The spin animation is decoration played over an outcome the server has already decided. The
 * reels are stopped on the exact stops the response reports, so what the player watches always
 * matches what they were paid.
 */
export function createSlotsView({ api, onBalance, onError, config }) {
  const reelNodes = qsa('#slot-reels .reel');
  const outcome = qs('#slots-outcome');
  const betInput = qs('#slots-bet');
  const spinButton = qs('#slots-spin');

  let busy = false;

  spinButton.addEventListener('click', spin);

  async function spin() {
    if (busy) return;

    const check = validateBet(betInput.value, {
      min: config()?.minBet, max: config()?.maxBet, balance: config()?.balance,
    });
    if (!check.valid) {
      onError(check.reason);
      return;
    }

    busy = true;
    spinButton.disabled = true;
    setText(outcome, '');
    startSpinning();

    try {
      const result = await api.spinSlots(check.amount);
      await settle(result);
      onBalance(result.balance);
    } catch (error) {
      onError(error.message);
    } finally {
      stopSpinning();
      busy = false;
      spinButton.disabled = false;
    }
  }

  function startSpinning() {
    for (const reel of reelNodes) {
      reel.classList.add('spinning');
      reel.classList.remove('win');
    }
  }

  function stopSpinning() {
    for (const reel of reelNodes) reel.classList.remove('spinning');
  }

  /** Stops the reels left to right, then shows the result. */
  async function settle(result) {
    for (let index = 0; index < reelNodes.length; index += 1) {
      await delay(280);
      const reel = reelNodes[index];
      reel.classList.remove('spinning');
      clear(reel);
      reel.append(el('span', { text: SYMBOL_GLYPHS[result.symbols[index]] ?? '?' }));
      reel.setAttribute('aria-label', result.symbols[index]);
    }

    if (result.win) {
      for (const reel of reelNodes) reel.classList.add('win');
      setText(outcome, `${result.combination} \u2014 pays ${result.multiplier}\u00d7 \u2014 ${formatDelta(result.net)}`);
    } else {
      setText(outcome, `No win \u2014 ${formatDelta(result.net)}`);
    }
  }

  function delay(ms) {
    return new Promise((resolve) => { setTimeout(resolve, ms); });
  }

  /** Renders the paytable and the machine's advertised return, both served by the API. */
  function describeMachine(cfg) {
    if (!cfg?.slots) return;
    setText(qs('#slots-rtp'),
      `Three reels, single payline \u00b7 return to player ${cfg.slots.rtp}%`);

    const body = qs('#slots-paytable tbody');
    clear(body);
    const rows = Object.entries(cfg.slots.paytable).sort((a, b) => b[1] - a[1]);
    for (const [name, multiplier] of rows) {
      body.append(el('tr', {
        children: [
          el('td', { text: name }),
          el('td', { text: `${multiplier}\u00d7` }),
        ],
      }));
    }
  }

  return { describeMachine };
}
