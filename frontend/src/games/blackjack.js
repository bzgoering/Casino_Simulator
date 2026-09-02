import { el, clear, setText, show, qs, qsa } from '../lib/dom.js';
import { parseCard, describeHand, formatTotal } from '../lib/cards.js';
import { formatMoney, formatDelta, validateBet } from '../lib/money.js';

/**
 * Blackjack view.
 *
 * The browser holds no game state beyond what the last response said. Every legal action comes
 * from `legalActions` in that response, so the buttons can never offer a move the server would
 * reject, and a player editing the DOM to re-enable one just gets a 400 back.
 */
export function createBlackjackView({ api, onBalance, onError, config }) {
  const dealerCards = qs('#dealer-cards');
  const dealerTotal = qs('#dealer-total');
  const playerHands = qs('#player-hands');
  const outcome = qs('#blackjack-outcome');
  const betInput = qs('#blackjack-bet');
  const handsInput = qs('#blackjack-hands');
  const stakeNote = qs('#blackjack-stake');
  const dealButton = qs('#blackjack-deal');
  const actionRow = qs('#blackjack-actions');
  const actionButtons = qsa('#blackjack-actions button');
  const shoeInfo = qs('#shoe-info');

  let round = null;
  let busy = false;

  dealButton.addEventListener('click', deal);
  betInput.addEventListener('input', showStake);
  handsInput.addEventListener('input', showStake);
  for (const button of actionButtons) {
    button.addEventListener('click', () => act(button.dataset.action));
  }

  /** Boxes requested, clamped to what the table allows. */
  function handCount() {
    const max = config()?.blackjack?.maxHands ?? 4;
    const value = Math.trunc(Number(handsInput.value));
    if (!Number.isFinite(value)) return 1;
    return Math.min(Math.max(value, 1), max);
  }

  /** Spells out the whole commitment, since every box is charged the same bet. */
  function showStake() {
    const hands = handCount();
    const bet = Number.parseFloat(betInput.value);
    if (hands < 2 || !Number.isFinite(bet) || bet <= 0) {
      setText(stakeNote, '');
      return;
    }
    setText(stakeNote, `${hands} × ${formatMoney(bet)} = ${formatMoney(bet * hands)}`);
  }

  async function deal() {
    const hands = handCount();
    handsInput.value = String(hands);

    // Each box is charged the bet, so the balance has to cover all of them, not just one.
    const check = validateBet(betInput.value, {
      min: config()?.minBet, max: config()?.maxBet,
    });
    if (!check.valid) {
      onError(check.reason);
      return;
    }
    const balance = config()?.balance;
    if (balance !== undefined && check.amount * hands > balance) {
      onError('Not enough money.');
      return;
    }
    await run(() => api.dealBlackjack(check.amount, hands));
  }

  async function act(action) {
    if (!round) return;
    await run(() => api.blackjackAction(round.roundId, action));
  }

  async function run(operation) {
    if (busy) return;
    busy = true;
    setBusy(true);
    try {
      round = await operation();
      render();
      onBalance(round.balance);
    } catch (error) {
      onError(error.message);
    } finally {
      busy = false;
      setBusy(false);
    }
  }

  /**
   * Disables everything while a request is in flight, and keeps the deal controls disabled for
   * as long as a hand is live. The row stays on screen rather than being hidden, so the stake
   * that bought the hand is still visible while it is played.
   */
  function setBusy(value) {
    const live = Boolean(round) && !round.settled;
    dealButton.disabled = value || live;
    betInput.disabled = value || live;
    handsInput.disabled = value || live;
    for (const button of actionButtons) button.disabled = value;
  }

  function render() {
    if (!round) return;

    renderDealer();
    renderPlayerHands();
    renderOutcome();

    const settled = round.settled;
    show(actionRow, !settled);
    setBusy(false);
    setText(shoeInfo, `${round.cardsRemaining} cards left in the shoe`);

    if (!settled) {
      const legal = new Set(round.legalActions ?? []);
      for (const button of actionButtons) {
        // Only what the server says is legal right now.
        button.disabled = !legal.has(button.dataset.action);
      }
    }
  }

  function renderDealer() {
    clear(dealerCards);
    for (const code of round.dealer.cards) {
      dealerCards.append(cardNode(code));
    }
    if (!round.dealer.revealed) {
      dealerCards.append(el('div', { className: 'card facedown', attrs: { 'aria-label': 'face down card' } }));
    }
    setText(dealerTotal, round.dealer.revealed
      ? formatTotal(round.dealer.total, round.dealer.soft)
      : `${round.dealer.total} + ?`);
    dealerCards.setAttribute('aria-label', `Dealer has ${describeHand(round.dealer.cards)}`);
  }

  function renderPlayerHands() {
    clear(playerHands);
    round.hands.forEach((hand, index) => {
      const isActive = !round.settled && index === round.activeHandIndex;
      const cards = el('div', { className: 'cards' });
      for (const code of hand.cards) cards.append(cardNode(code));

      const meta = el('div', { className: 'hand-meta' });
      meta.append(el('span', { text: `${formatTotal(hand.total, hand.soft)} \u00b7 ${formatMoney(hand.bet)}` }));
      if (hand.doubled) meta.append(el('span', { text: ' \u00b7 doubled' }));
      if (hand.outcome) {
        meta.append(el('span', { text: ' \u00b7 ' }));
        meta.append(el('span', { className: `result ${hand.outcome}`, text: outcomeLabel(hand.outcome) }));
      }

      playerHands.append(el('div', {
        className: `hand${isActive ? ' active' : ''}`,
        children: [cards, meta],
        attrs: { 'aria-label': `Hand ${index + 1}: ${describeHand(hand.cards)}` },
      }));
    });
  }

  function renderOutcome() {
    if (!round.settled) {
      setText(outcome, '');
      return;
    }
    const net = Number.parseFloat(round.net ?? '0');
    const label = net > 0 ? 'You win' : net < 0 ? 'You lose' : 'Push';
    setText(outcome, `${label} \u2014 ${formatDelta(net)}`);
  }

  function cardNode(code) {
    const card = parseCard(code);
    if (!card) return el('div', { className: 'card facedown' });
    return el('div', {
      className: `card ${card.color}`,
      attrs: { 'aria-label': card.label },
      children: [
        el('span', { className: 'rank', text: card.rank }),
        el('span', { className: 'suit', text: card.suitSymbol }),
      ],
    });
  }

  function outcomeLabel(value) {
    return { BLACKJACK: 'Blackjack!', WIN: 'Win', LOSE: 'Lose', PUSH: 'Push' }[value] ?? value;
  }

  /** Reconnects to a hand left in progress, e.g. after a page refresh. */
  async function resume() {
    try {
      round = await api.currentBlackjack();
      render();
    } catch {
      // No hand in progress is the normal case, not an error worth surfacing.
      round = null;
      clear(dealerCards);
      clear(playerHands);
      setText(outcome, '');
      setText(dealerTotal, '');
      show(actionRow, false);
      setBusy(false);
    }
  }

  function describeRules(cfg) {
    if (!cfg?.blackjack) return;
    const bj = cfg.blackjack;
    setText(qs('#blackjack-rules'),
      `${bj.decks} decks \u00b7 blackjack pays ${bj.blackjackPays} \u00b7 `
      + `dealer ${bj.dealerHitsSoft17 ? 'hits' : 'stands on'} soft 17 \u00b7 `
      + `split up to ${bj.maxSplits} times, up to ${bj.maxHands} hands`);
    // Follow the table rather than the value hard-coded in the markup.
    handsInput.max = String(bj.maxHands);
    showStake();
  }

  return { resume, describeRules };
}
