import { el, clear, setText, qs, qsa } from '../lib/dom.js';
import { formatMoney, formatDelta, validateBet } from '../lib/money.js';
import { POCKET_ORDER, colorOf, layoutGrid, pocketsFor, payoutFor } from '../lib/roulette-layout.js';

/**
 * Roulette view.
 *
 * Chips are accumulated locally and sent as one spin. The wheel animation rotates to the pocket
 * the server chose: the browser is told the result and then renders it, never the other way
 * round.
 */
export function createRouletteView({ api, onBalance, onError, config }) {
  const cloth = qs('#roulette-cloth');
  const wheel = qs('#roulette-wheel');
  const resultNode = qs('#roulette-result');
  const recentList = qs('#roulette-recent');
  const betList = qs('#roulette-bets');
  const stakedNode = qs('#roulette-staked');
  const spinButton = qs('#roulette-spin');
  const clearButton = qs('#roulette-clear');

  /** @type {Array<{type: string, selection: string, amount: number}>} */
  let bets = [];
  let chipValue = 1;
  let busy = false;
  const recent = [];

  buildWheel();
  buildCloth();

  for (const button of qsa('.chip-btn')) {
    button.addEventListener('click', () => {
      chipValue = Number(button.dataset.chip);
      for (const other of qsa('.chip-btn')) other.classList.toggle('active', other === button);
    });
  }

  clearButton.addEventListener('click', () => {
    bets = [];
    renderBets();
    markChips();
  });

  spinButton.addEventListener('click', spin);

  /** Draws the wheel as coloured sectors in the real pocket order. */
  function buildWheel() {
    const slice = 360 / POCKET_ORDER.length;
    const stops = POCKET_ORDER.map((pocket, index) => {
      const shade = colorOf(pocket) === 'RED' ? '#d63b3b'
        : colorOf(pocket) === 'GREEN' ? '#2fa360' : '#1c1f26';
      return `${shade} ${index * slice}deg ${(index + 1) * slice}deg`;
    });
    wheel.style.background = `conic-gradient(${stops.join(', ')})`;
    wheel.append(el('span', { className: 'wheel-marker', text: '\u25bc' }));
  }

  /**
   * Draws the cloth as twelve rows of three, which is a real layout stood on end.
   *
   * <p>In that orientation the three column bets sit at the foot of the grid, one under each
   * printed column, because a column is the vertical run 1-4-7-... A "2 to 1" box at the end of
   * every row would be twelve boxes covering only three distinct bets, and each row spans all
   * three columns anyway, so no single column bet belongs there.
   */
  function buildCloth() {
    clear(cloth);
    cloth.append(cell({ label: '0', className: 'cell GREEN', type: 'STRAIGHT', selection: '0' }));

    for (const row of layoutGrid()) {
      for (const number of row) {
        cloth.append(cell({
          label: String(number),
          className: `cell ${colorOf(number)}`,
          type: 'STRAIGHT',
          selection: String(number),
        }));
      }
    }

    for (const columnNumber of [1, 2, 3]) {
      cloth.append(cell({
        label: '2:1',
        className: 'cell outside',
        type: 'COLUMN',
        selection: String(columnNumber),
        title: `Column ${columnNumber} (${columnNumber}, ${columnNumber + 3}, ${columnNumber + 6}, ...)`,
      }));
    }

    const outside = [
      ['1st 12', 'DOZEN', '1'], ['2nd 12', 'DOZEN', '2'], ['3rd 12', 'DOZEN', '3'],
      ['1-18', 'HALF', 'LOW'], ['Even', 'PARITY', 'EVEN'], ['Red', 'COLOR', 'RED'],
      ['Black', 'COLOR', 'BLACK'], ['Odd', 'PARITY', 'ODD'], ['19-36', 'HALF', 'HIGH'],
    ];
    for (const [label, type, selection] of outside) {
      cloth.append(cell({ label, className: 'cell outside', type, selection }));
    }
  }

  function cell({ label, className, type, selection, title }) {
    const node = el('button', {
      className,
      text: label,
      attrs: { type: 'button', 'data-type': type, 'data-selection': selection, title: title ?? label },
    });
    node.addEventListener('click', () => placeChip(type, selection));
    return node;
  }

  function placeChip(type, selection) {
    if (busy) return;

    // Mirrors the server's layout rules so an impossible chip is refused before it is sent.
    if (pocketsFor(type, selection) === null) {
      onError('Not a valid bet.');
      return;
    }
    const limits = config() ?? {};
    const check = validateBet(chipValue, { min: limits.minBet, max: limits.maxBet });
    if (!check.valid) {
      onError(check.reason);
      return;
    }
    if (limits.balance !== undefined && totalStaked() + chipValue > limits.balance) {
      onError('Not enough money.');
      return;
    }

    const existing = bets.find((b) => b.type === type && b.selection === selection);
    if (!existing && bets.length >= (limits.maxRouletteBets ?? 20)) {
      onError(`At most ${limits.maxRouletteBets ?? 20} bets.`);
      return;
    }

    if (existing) {
      existing.amount = Number((existing.amount + chipValue).toFixed(2));
    } else {
      bets.push({ type, selection, amount: chipValue });
    }
    renderBets();
    markChips();
  }

  function totalStaked() {
    return bets.reduce((sum, bet) => sum + bet.amount, 0);
  }

  function renderBets(results) {
    clear(betList);
    for (const bet of bets) {
      const result = results?.find((r) => r.type === bet.type && r.selection === bet.selection);
      const status = result ? (result.won ? 'won' : 'lost') : '';
      betList.append(el('li', {
        className: status,
        children: [
          el('span', { text: `${describeBet(bet)} \u00b7 ${formatMoney(bet.amount)}` }),
          el('span', {
            text: result
              ? (result.won ? `+${formatMoney(result.payout)}` : '\u2014')
              : `pays ${formatMoney(payoutFor(bet.type, bet.amount))}`,
          }),
        ],
      }));
    }
    setText(stakedNode, bets.length ? `\u00b7 ${formatMoney(totalStaked())} staked` : '');
    spinButton.disabled = bets.length === 0 || busy;
  }

  function describeBet(bet) {
    const readable = {
      STRAIGHT: bet.selection,
      SPLIT: `Split ${bet.selection}`,
      STREET: `Street ${bet.selection}`,
      CORNER: `Corner ${bet.selection}`,
      SIX_LINE: `Six line ${bet.selection}`,
      COLUMN: `Column ${bet.selection}`,
      DOZEN: `Dozen ${bet.selection}`,
      COLOR: bet.selection === 'RED' ? 'Red' : 'Black',
      PARITY: bet.selection === 'ODD' ? 'Odd' : 'Even',
      HALF: bet.selection === 'LOW' ? '1-18' : '19-36',
    };
    return readable[bet.type] ?? `${bet.type} ${bet.selection}`;
  }

  /** Highlights the cells that currently carry a chip. */
  function markChips() {
    for (const node of qsa('.cell', cloth)) {
      const has = bets.some(
        (bet) => bet.type === node.dataset.type && bet.selection === node.dataset.selection,
      );
      node.classList.toggle('has-chip', has);
      node.classList.remove('winner');
    }
  }

  async function spin() {
    if (busy || bets.length === 0) return;

    busy = true;
    spinButton.disabled = true;
    clearButton.disabled = true;
    setText(resultNode, '');

    try {
      const result = await api.spinRoulette(bets);
      await animateTo(result.wheelIndex);
      showResult(result);
      onBalance(result.balance);
    } catch (error) {
      onError(error.message);
    } finally {
      busy = false;
      clearButton.disabled = false;
      spinButton.disabled = bets.length === 0;
    }
  }

  /** Spins the wheel to the pocket the server already picked. */
  function animateTo(wheelIndex) {
    const slice = 360 / POCKET_ORDER.length;
    // Several full turns so the motion reads as a spin, then land on the pocket.
    const target = 360 * 5 - (wheelIndex * slice + slice / 2);
    wheel.style.transform = `rotate(${target}deg)`;

    const reduceMotion = globalThis.matchMedia?.('(prefers-reduced-motion: reduce)')?.matches;
    return new Promise((resolve) => { setTimeout(resolve, reduceMotion ? 0 : 3200); });
  }

  function showResult(result) {
    clear(resultNode);
    resultNode.append(el('span', {
      className: `pocket-badge ${result.color}`,
      text: String(result.pocket),
    }));
    resultNode.append(el('span', { text: ` ${formatDelta(result.net)}` }));

    recent.unshift({ pocket: result.pocket, color: result.color });
    recent.splice(12);
    clear(recentList);
    for (const entry of recent) {
      recentList.append(el('li', { className: entry.color, text: String(entry.pocket) }));
    }

    renderBets(result.bets);
    for (const node of qsa('.cell', cloth)) {
      if (node.dataset.type === 'STRAIGHT' && Number(node.dataset.selection) === result.pocket) {
        node.classList.add('winner');
      }
    }
  }

  function describeTable(cfg) {
    if (!cfg?.roulette) return;
    setText(qs('#roulette-info'),
      'European single zero \u00b7 37 pockets \u00b7 house edge '
      + `${cfg.roulette.houseEdgePercent.toFixed(2)}% on every bet`);
  }

  return { describeTable };
}
