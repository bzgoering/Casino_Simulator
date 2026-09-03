import { el, clear, setText, qs, qsa } from '../lib/dom.js';
import { formatMoney, formatDelta } from '../lib/money.js';

const SYMBOL_GLYPHS = {
  CHERRY: '\u{1F352}',
  ORANGE: '\u{1F34A}',
  PLUM: '\u{1F347}',
  BELL: '\u{1F514}',
  BAR1: 'BAR',
  BAR2: 'BAR×2',
  BAR3: 'BAR×3',
  SEVEN: '7',
};

/**
 * Symbols drawn as words rather than pictures. They need a smaller, bolder treatment: "BAR x3"
 * at emoji size overflows the cell, and the keycap-7 emoji does not render on every platform,
 * so the seven is a plain character with its own styling.
 */
const TEXT_SYMBOLS = new Set(['BAR1', 'BAR2', 'BAR3', 'SEVEN']);

const REELS = 3;
const ROWS = 3;

/**
 * Slots view.
 *
 * The spin animation is decoration played over an outcome the server has already decided. The
 * reels are stopped on the exact window the response reports, so what the player watches always
 * matches what they were paid.
 *
 * Unlike the table games this machine has no house minimum: the player dials in whatever
 * denomination they like and buys a fixed number of credits, each of which lights one more
 * payline. Only lit lines pay, but the whole window is shown, so a near miss on a line that was
 * not bought is visible exactly as it would be on a real cabinet.
 */
export function createSlotsView({ api, onBalance, onError, config }) {
  const windowNode = qs('#slot-window');
  const paylineKey = qs('#payline-key');
  const outcome = qs('#slots-outcome');
  const lineWins = qs('#slots-line-wins');
  const betInput = qs('#slots-bet');
  const creditRow = qs('#slots-credits');
  const stakeNote = qs('#slots-stake');
  const spinButton = qs('#slots-spin');

  /** @type {Array<Array<HTMLElement>>} reel -> row -> cell */
  let cells = [];
  let creditOptions = [1];
  let credits = 1;
  let busy = false;

  buildWindow();
  spinButton.addEventListener('click', spin);
  betInput.addEventListener('input', showStake);

  /** One symbol, sized for a picture or for a word. */
  function glyph(symbol) {
    return el('span', {
      className: TEXT_SYMBOLS.has(symbol) ? `glyph-text ${symbol}` : 'glyph',
      text: SYMBOL_GLYPHS[symbol] ?? '?',
    });
  }

  /** The three-by-three glass. Reels are columns, so a reel spins as one. */
  function buildWindow() {
    clear(windowNode);
    cells = [];
    for (let reel = 0; reel < REELS; reel += 1) {
      const column = [];
      const reelNode = el('div', { className: 'reel', attrs: { 'data-reel': String(reel) } });
      for (let row = 0; row < ROWS; row += 1) {
        const cell = el('div', {
          className: 'stop',
          attrs: { 'data-row': String(row) },
          children: [glyph('CHERRY')],
        });
        column.push(cell);
        reelNode.append(cell);
      }
      cells.push(column);
      windowNode.append(reelNode);
    }
  }

  /** The fixed credit buttons on the cabinet, and what each one lights. */
  function renderCreditButtons() {
    clear(creditRow);
    for (const option of creditOptions) {
      const button = el('button', {
        className: `credit-btn${option === credits ? ' active' : ''}`,
        text: String(option),
        attrs: {
          type: 'button',
          'data-credits': String(option),
          title: `${option} credit${option === 1 ? '' : 's'}: ${describeLines(option)}`,
          'aria-pressed': option === credits ? 'true' : 'false',
        },
      });
      button.addEventListener('click', () => {
        if (busy) return;
        credits = option;
        renderCreditButtons();
        renderPaylineKey();
        showStake();
      });
      creditRow.append(button);
    }
  }

  function describeLines(count) {
    const lines = paylines().slice(0, count).map((line) => line.name.toLowerCase());
    return lines.length ? lines.join(', ') : 'no lines';
  }

  function paylines() {
    return config()?.slots?.paylines ?? [];
  }

  /** A legend of which lines this credit setting lights, dark ones included. */
  function renderPaylineKey() {
    clear(paylineKey);
    paylines().forEach((line, index) => {
      const lit = index < credits;
      paylineKey.append(el('li', {
        className: lit ? 'lit' : 'dark',
        text: line.name,
        attrs: { 'data-payline': line.id, title: lit ? 'Lit' : 'Not bought: cannot pay' },
      }));
    });
  }

  /** Spells out the whole commitment, since every lit line is charged the bet. */
  function showStake() {
    const bet = Number.parseFloat(betInput.value);
    if (!Number.isFinite(bet) || bet <= 0) {
      setText(stakeNote, '');
      return;
    }
    setText(stakeNote,
      `${credits} line${credits === 1 ? '' : 's'} × ${formatMoney(bet)} = `
      + `${formatMoney(bet * credits)} a spin`);
  }

  async function spin() {
    if (busy) return;

    const bet = Number.parseFloat(betInput.value);
    // No minimum: a machine takes any denomination, down to the cent two decimals can express.
    if (!Number.isFinite(bet) || bet <= 0) {
      onError('Enter a bet.');
      return;
    }
    if (Math.round(bet * 100) !== Number((bet * 100).toFixed(4))) {
      onError('Two decimal places max.');
      return;
    }
    const total = Number((bet * credits).toFixed(2));
    const balance = config()?.balance;
    if (balance !== undefined && total > balance) {
      onError('Not enough money.');
      return;
    }

    busy = true;
    spinButton.disabled = true;
    betInput.disabled = true;
    for (const button of qsa('.credit-btn', creditRow)) button.disabled = true;
    setText(outcome, '');
    clear(lineWins);
    clearHighlights();
    startSpinning();

    try {
      const result = await api.spinSlots(Number(bet.toFixed(2)), credits);
      await settle(result);
      onBalance(result.balance);
    } catch (error) {
      onError(error.message);
    } finally {
      stopSpinning();
      busy = false;
      spinButton.disabled = false;
      betInput.disabled = false;
      for (const button of qsa('.credit-btn', creditRow)) button.disabled = false;
    }
  }

  function startSpinning() {
    for (const column of cells) {
      for (const cell of column) cell.classList.add('spinning');
    }
  }

  function stopSpinning() {
    for (const column of cells) {
      for (const cell of column) cell.classList.remove('spinning');
    }
  }

  function clearHighlights() {
    for (const column of cells) {
      for (const cell of column) cell.classList.remove('win');
    }
  }

  /** Stops the reels left to right, then shows what each lit line did. */
  async function settle(result) {
    for (let reel = 0; reel < REELS; reel += 1) {
      await delay(280);
      result.window[reel].forEach((symbol, row) => {
        const cell = cells[reel][row];
        cell.classList.remove('spinning');
        clear(cell);
        cell.append(glyph(symbol));
        cell.setAttribute('aria-label', symbol);
      });
    }

    const winners = result.lines.filter((line) => line.win);
    for (const line of winners) {
      line.rows.forEach((row, reel) => cells[reel][row].classList.add('win'));
    }

    setText(outcome, result.win
      ? `${result.combination}: ${formatDelta(result.net)}`
      : `No win: ${formatDelta(result.net)}`);

    clear(lineWins);
    for (const line of winners) {
      lineWins.append(el('li', {
        children: [
          el('span', { text: `${line.name} · ${line.combination}` }),
          el('span', { className: 'line-payout', text: `+${formatMoney(line.payout)}` }),
        ],
      }));
    }
  }

  function delay(ms) {
    return new Promise((resolve) => { setTimeout(resolve, ms); });
  }

  /** Renders the paytable, the credit buttons and the machine's advertised return. */
  function describeMachine(cfg) {
    if (!cfg?.slots) return;
    const slots = cfg.slots;

    creditOptions = slots.creditOptions?.length ? slots.creditOptions : [1];
    if (!creditOptions.includes(credits)) {
      credits = creditOptions[0];
    }
    renderCreditButtons();
    renderPaylineKey();
    showStake();

    setText(qs('#slots-rtp'),
      `Three reels, ${slots.paylines?.length ?? 0} paylines · rows and diagonals · `
      + `return to player ${slots.rtp}% on every line · no minimum bet`);

    const body = qs('#slots-paytable tbody');
    clear(body);
    const rows = Object.entries(slots.paytable).sort((a, b) => b[1] - a[1]);
    for (const [name, multiplier] of rows) {
      body.append(el('tr', {
        children: [
          el('td', { text: name }),
          el('td', { text: `${multiplier}×` }),
        ],
      }));
    }
  }

  return { describeMachine };
}
