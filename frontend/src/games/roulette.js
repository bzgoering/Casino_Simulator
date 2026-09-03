import { el, clear, setText, qs, qsa } from '../lib/dom.js';
import { formatMoney, formatDelta, validateBet } from '../lib/money.js';
import {
  POCKET_ORDER, DOUBLE_ZERO, colorOf, labelOf, layoutGrid, pocketsFor, payoutFor,
} from '../lib/roulette-layout.js';
import { CHIP_VALUES, chipBreakdown, topChip } from '../lib/chips.js';

/**
 * Roulette view.
 *
 * Chips are accumulated locally and sent as one spin. The wheel animation rotates to the pocket
 * the server chose: the browser is told the result and then renders it, never the other way
 * round.
 */
/** How far out from the middle the numbers sit, inside a 220px wheel with a hub at 26px. */
const NUMBER_RADIUS = 84;

export function createRouletteView({ api, onBalance, onError, config }) {
  const cloth = qs('#roulette-cloth');
  const wheel = qs('#roulette-wheel');
  const resultNode = qs('#roulette-result');
  const recentList = qs('#roulette-recent');
  const betList = qs('#roulette-bets');
  const stakedNode = qs('#roulette-staked');
  const spinButton = qs('#roulette-spin');
  const clearButton = qs('#roulette-clear');
  const chipPicker = qs('#chip-picker');

  /** @type {Array<{type: string, selection: string, amount: number}>} */
  let bets = [];
  let chipValue = CHIP_VALUES[CHIP_VALUES.length - 1];
  let busy = false;
  // Degrees the wheel has turned in total. It only ever grows: see animateTo.
  let rotation = 0;
  const recent = [];

  buildWheel();
  buildCloth();
  buildChipPicker();

  /**
   * The picker, built from the same list the cloth draws its piles from.
   *
   * Each button is the chip it selects rather than a pill naming it, so the thing you pick and
   * the thing that lands on the number are recognisably the same object.
   */
  function buildChipPicker() {
    clear(chipPicker);
    for (const value of [...CHIP_VALUES].reverse()) {
      const button = el('button', {
        className: value === chipValue ? 'chip-btn active' : 'chip-btn',
        text: `$${value}`,
        attrs: {
          type: 'button',
          'data-chip': String(value),
          'aria-label': `${value} dollar chip`,
          'aria-pressed': String(value === chipValue),
        },
      });
      button.addEventListener('click', () => {
        chipValue = value;
        for (const other of qsa('.chip-btn', chipPicker)) {
          other.classList.toggle('active', other === button);
          other.setAttribute('aria-pressed', String(other === button));
        }
      });
      chipPicker.append(button);
    }
  }

  clearButton.addEventListener('click', () => {
    bets = [];
    renderBets();
    renderChips();
  });

  spinButton.addEventListener('click', spin);

  /**
   * Draws the wheel as coloured sectors in the real pocket order, each with its number on it.
   *
   * The numbers are children of the wheel, so they turn with it. The marker is not: it lives
   * beside the wheel in the markup, because a pointer that turns with the thing it is pointing
   * at never points at anything.
   *
   * A conic gradient starts at twelve o'clock and runs clockwise, so pocket i covers the wedge
   * from i*slice, and its middle -- where its number goes, and where the marker has to find it
   * -- sits at i*slice + slice/2 clockwise from the top.
   */
  function buildWheel() {
    clear(wheel);
    const slice = 360 / POCKET_ORDER.length;
    const stops = POCKET_ORDER.map((pocket, index) => {
      const shade = colorOf(pocket) === 'RED' ? '#d63b3b'
        : colorOf(pocket) === 'GREEN' ? '#2fa360' : '#1c1f26';
      return `${shade} ${index * slice}deg ${(index + 1) * slice}deg`;
    });
    wheel.style.background = `conic-gradient(${stops.join(', ')})`;

    POCKET_ORDER.forEach((pocket, index) => {
      const label = el('span', {
        className: 'wheel-number',
        text: labelOf(pocket),
        attrs: { 'data-pocket': labelOf(pocket) },
      });
      // Out to the rim along its own wedge. Left level rather than turned to face outwards, so
      // the pocket under the marker -- the only one anybody reads -- always ends up upright.
      label.style.transform = 'translate(-50%, -50%) '
        + `rotate(${(index * slice + slice / 2).toFixed(3)}deg) translateY(-${NUMBER_RADIUS}px)`;
      wheel.append(label);
    });
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
    // Two greens side by side, which is what a double-zero cloth opens with.
    cloth.append(cell({ label: '0', className: 'cell GREEN zero', type: 'STRAIGHT', selection: '0' }));
    cloth.append(cell({
      label: labelOf(DOUBLE_ZERO),
      className: 'cell GREEN zero',
      type: 'STRAIGHT',
      selection: labelOf(DOUBLE_ZERO),
    }));

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

    // The five-number bet, labelled with its price because it is the worst bet on the cloth:
    // 6:1 on five pockets in 38 is a 7.89% edge, against 5.26% on everything else here.
    cloth.append(cell({
      label: '0-00-1-2-3 \u00b7 6:1',
      className: 'cell outside top-line',
      type: 'TOP_LINE',
      selection: `0,${labelOf(DOUBLE_ZERO)},1,2,3`,
      title: 'Five number bet, pays 6 to 1 (the worst bet on the table)',
    }));

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
      attrs: {
        type: 'button',
        'data-type': type,
        'data-selection': selection,
        // Kept so the tooltip can be rebuilt around it as chips come and go.
        'data-title': title ?? label,
        title: title ?? label,
      },
      children: [
        el('span', { className: 'cell-label', text: label }),
        el('span', { className: 'chip-stack' }),
      ],
    });
    node.addEventListener('click', () => placeChip(type, selection));
    // Right-click lifts the top chip back off, the way you would take it back at the table.
    // Without preventDefault the browser menu opens over the cloth on every removal.
    node.addEventListener('contextmenu', (event) => {
      event.preventDefault();
      removeChip(type, selection);
    });
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

    if (existing) {
      existing.amount = Number((existing.amount + chipValue).toFixed(2));
    } else {
      bets.push({ type, selection, amount: chipValue });
    }
    renderBets();
    renderChips();
  }

  /**
   * Takes the top chip off a space, and the bet with it once nothing is left.
   *
   * The top of a pile is its smallest chip, so this removes what the player can see sitting on
   * top rather than a click they made earlier and can no longer pick out. Taking the top off
   * $101 leaves the $100 chip; taking it off a lone $5 clears the space.
   */
  function removeChip(type, selection) {
    if (busy) return;

    const index = bets.findIndex((b) => b.type === type && b.selection === selection);
    if (index === -1) return;

    const left = Number((bets[index].amount - topChip(bets[index].amount)).toFixed(2));
    if (left > 0) bets[index].amount = left;
    else bets.splice(index, 1);

    renderBets();
    renderChips();
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
          // Won or lost said in words, not just in the colour of the row.
          result ? el('span', {
            className: 'bet-outcome',
            text: result.won ? 'Won' : 'Lost',
          }) : null,
          // Always what the chips on that space would return. A settled bet stays on the cloth
          // for the next spin, so the figure it is riding on has to stay legible: replacing it
          // with the last result left a standing bet quoting nothing at all.
          el('span', { text: `pays ${formatMoney(payoutFor(bet.type, bet.amount))}` }),
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
      TOP_LINE: 'Five number 0-00-1-2-3',
      COLUMN: `Column ${bet.selection}`,
      DOZEN: `Dozen ${bet.selection}`,
      COLOR: bet.selection === 'RED' ? 'Red' : 'Black',
      PARITY: bet.selection === 'ODD' ? 'Odd' : 'Even',
      HALF: bet.selection === 'LOW' ? '1-18' : '19-36',
    };
    return readable[bet.type] ?? `${bet.type} ${bet.selection}`;
  }

  /**
   * Draws each space's pile: one token per chip actually placed, so the stake can be counted off
   * the cloth rather than read from the list beside it.
   *
   * The tokens are spread by a step that shrinks as the pile grows. A fixed offset would look
   * right for three chips and send thirty climbing out of the cell and over the numbers above.
   */
  function renderChips() {
    for (const node of qsa('.cell', cloth)) {
      const stack = qs('.chip-stack', node);
      clear(stack);
      node.classList.remove('winner');

      const bet = bets.find(
        (b) => b.type === node.dataset.type && b.selection === node.dataset.selection,
      );
      if (!bet) {
        node.setAttribute('title', node.dataset.title);
        node.removeAttribute('aria-label');
        continue;
      }

      // Drawn from the stake, not from a history of clicks: five singles show as one $5 chip.
      const pile = chipBreakdown(bet.amount);
      const step = Math.min(4, 20 / Math.max(pile.length - 1, 1));
      pile.forEach((value, index) => {
        const token = el('span', {
          className: 'chip-token',
          attrs: { 'data-chip': String(value) },
        });
        token.style.bottom = `${(index * step).toFixed(2)}px`;
        stack.append(token);
      });

      const summary = `${node.dataset.title}: ${formatMoney(bet.amount)} staked`;
      node.setAttribute('title', summary);
      // A pile of coloured discs says nothing to a screen reader; the count and total must.
      node.setAttribute('aria-label', summary);
    }
  }

  async function spin() {
    if (busy || bets.length === 0) return;

    busy = true;
    spinButton.disabled = true;
    clearButton.disabled = true;
    setText(resultNode, '');

    // Back to the pending state before the wheel starts. Without this the bets keep last
    // round's won and lost against them, so a player spinning the same bets again never sees
    // what the spin could pay -- and the previous winning number stays lit on the cloth
    // through a spin it has nothing to do with.
    renderBets();
    renderChips();

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

  /**
   * Spins the wheel to the pocket the server already picked.
   *
   * The pocket sits at pocketAngle clockwise from the top, so for it to finish under the marker
   * the wheel's total rotation has to come out at minus that, modulo a full turn. Any such
   * rotation lands correctly, so this takes the first one at least five turns on from wherever
   * the wheel already is.
   *
   * That "from where it already is" is the whole point. An absolute target left the second
   * spin asking for a rotation near the one it was already sitting at: the wheel twitched a few
   * degrees, or unwound backwards, and came to rest somewhere with no relation to the number
   * being announced beside it.
   */
  function animateTo(wheelIndex) {
    const slice = 360 / POCKET_ORDER.length;
    const pocketAngle = wheelIndex * slice + slice / 2;

    const landing = (((-pocketAngle) % 360) + 360) % 360;
    const atLeast = rotation + 360 * 5;
    rotation = landing + Math.ceil((atLeast - landing) / 360) * 360;

    const reduceMotion = globalThis.matchMedia?.('(prefers-reduced-motion: reduce)')?.matches;
    // Otherwise the wheel glides on for 3.2s after the result is already on screen.
    wheel.style.transition = reduceMotion ? 'none' : '';
    wheel.style.transform = `rotate(${rotation}deg)`;

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
      if (node.dataset.type === 'STRAIGHT' && node.dataset.selection === String(result.pocket)) {
        node.classList.add('winner');
      }
    }
  }

  function describeTable(cfg) {
    if (!cfg?.roulette) return;
    setText(qs('#roulette-info'),
      'Double zero \u00b7 38 pockets \u00b7 house edge '
      + `${cfg.roulette.houseEdgePercent.toFixed(2)}% on every bet bar the five number, `
      + 'which runs at 7.89%');
  }

  return { describeTable };
}
