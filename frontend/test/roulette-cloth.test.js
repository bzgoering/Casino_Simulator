import { describe, it, expect, beforeEach, vi } from 'vitest';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { createRouletteView } from '../src/games/roulette.js';

/**
 * The rendered cloth, not just the layout maths.
 *
 * The "2 to 1" bug lived entirely in how the cloth was built: every box resolved to the same
 * column, so the layout functions were correct while the screen was not. Testing the DOM is the
 * only place that distinction shows up. The chip piles are here for the same reason: what is
 * drawn on a space has to match what was actually staked on it.
 */

// import.meta.url is an http URL under the jsdom environment, so resolve from the project root.
const html = readFileSync(resolve(process.cwd(), 'index.html'), 'utf8');

function mountRoulette({ spinRoulette } = {}) {
  document.body.innerHTML = html.slice(html.indexOf('<body>') + 6, html.indexOf('</body>'));
  return createRouletteView({
    api: { spinRoulette: spinRoulette ?? vi.fn() },
    onBalance: vi.fn(),
    onError: vi.fn(),
    // Deliberately deep: several tests stack a pile worth thousands, and running out of money
    // is not what any of them are about. No bet-count limit is passed at all any more, because
    // what a player can afford is the only rule left.
    config: () => ({ minBet: 1, maxBet: 5000, balance: 100000 }),
  });
}

/** A real contextmenu event, so the handler's preventDefault can be observed. */
function rightClick(node) {
  const event = new window.MouseEvent('contextmenu', { bubbles: true, cancelable: true });
  node.dispatchEvent(event);
  return event;
}

const chipsOn = (node) => [...node.querySelectorAll('.chip-token')];
const valuesOn = (node) => chipsOn(node).map((chip) => chip.dataset.chip);
const straight = (n) => document.querySelector(`#roulette-cloth [data-selection="${n}"]`);
const selectChip = (value) => document.querySelector(`.chip-btn[data-chip="${value}"]`).click();

describe('the roulette cloth', () => {
  beforeEach(() => mountRoulette());

  it('draws exactly three column boxes, one per column', () => {
    const boxes = [...document.querySelectorAll('#roulette-cloth [data-type="COLUMN"]')];

    expect(boxes).toHaveLength(3);
    expect(boxes.map((b) => b.dataset.selection)).toEqual(['1', '2', '3']);
  });

  it('puts a chip only on the column box that was clicked', () => {
    const boxes = [...document.querySelectorAll('#roulette-cloth [data-type="COLUMN"]')];

    boxes[1].click();

    expect(chipsOn(boxes[0])).toHaveLength(0);
    expect(chipsOn(boxes[1])).toHaveLength(1);
    expect(chipsOn(boxes[2])).toHaveLength(0);

    const listed = [...document.querySelectorAll('#roulette-bets li')].map((li) => li.textContent);
    expect(listed).toHaveLength(1);
    expect(listed[0]).toContain('Column 2');
  });

  it('still draws every number and both greens', () => {
    const straights = [...document.querySelectorAll('#roulette-cloth [data-type="STRAIGHT"]')];

    expect(straights).toHaveLength(38);
    expect(straights[0].dataset.selection).toBe('0');
    expect(straights[1].dataset.selection).toBe('00');
  });

  it('prints the five-number bet with the price that makes it a bad one', () => {
    const topLine = document.querySelector('#roulette-cloth [data-type="TOP_LINE"]');

    expect(topLine).not.toBeNull();
    expect(topLine.dataset.selection).toBe('0,00,1,2,3');
    expect(topLine.textContent).toContain('6:1');
  });

  it('lets a player cover as many spaces as they like', () => {
    // There used to be a cap of 20. Covering every space on the cloth must now just work.
    const spaces = [...document.querySelectorAll('#roulette-cloth .cell')];
    for (const space of spaces) space.click();

    expect(document.querySelectorAll('#roulette-bets li')).toHaveLength(spaces.length);
    expect(spaces.length).toBeGreaterThan(20);
    for (const space of spaces) {
      expect(space.querySelectorAll('.chip-token').length, space.dataset.selection).toBe(1);
    }
  });

  it('puts the bets list under the wheel and the actions beside the chips', () => {
    expect(document.querySelector('.wheel-area .placed #roulette-bets')).not.toBeNull();
    expect(document.querySelector('.chip-row .bet-actions #roulette-spin')).not.toBeNull();
    expect(document.querySelector('.chip-row .bet-actions #roulette-clear')).not.toBeNull();
  });

  it('keeps the number readable under its own pile', () => {
    const cell = straight(17);
    cell.click();

    // The label is its own node, so redrawing the pile cannot wipe the number with it.
    expect(cell.querySelector('.cell-label').textContent).toBe('17');
    expect(chipsOn(cell)).toHaveLength(1);
  });
});

describe('the chip picker', () => {
  beforeEach(() => mountRoulette());

  it('offers every denomination, smallest first', () => {
    const buttons = [...document.querySelectorAll('#chip-picker .chip-btn')];

    expect(buttons.map((b) => b.dataset.chip)).toEqual(['1', '5', '10', '25', '50', '100']);
  });

  it('carries the value on each button, so it is coloured as that chip', () => {
    // The colours are one [data-chip] block in the stylesheet, shared with the cloth: carrying
    // the attribute is what makes a picker button and its chip on the number look the same.
    for (const button of document.querySelectorAll('#chip-picker .chip-btn')) {
      expect(button.dataset.chip, button.textContent).toBeTruthy();
      expect(button.textContent).toBe(`$${button.dataset.chip}`);
    }
  });

  it('starts on the smallest chip and marks it selected', () => {
    const active = document.querySelectorAll('#chip-picker .chip-btn.active');

    expect(active).toHaveLength(1);
    expect(active[0].dataset.chip).toBe('1');
    expect(active[0].getAttribute('aria-pressed')).toBe('true');
  });

  it('moves the selection, and says so, when another chip is picked', () => {
    selectChip(50);

    const active = [...document.querySelectorAll('#chip-picker .chip-btn.active')];
    expect(active.map((b) => b.dataset.chip)).toEqual(['50']);
    expect(document.querySelector('.chip-btn[data-chip="1"]').getAttribute('aria-pressed'))
      .toBe('false');
  });

  it('places the chip that is selected', () => {
    selectChip(25);
    straight(17).click();

    expect(valuesOn(straight(17))).toEqual(['25']);
  });
});

describe('colouring a pile up', () => {
  beforeEach(() => mountRoulette());

  it('shows five singles as one $5 chip', () => {
    const cell = straight(17);
    for (let i = 0; i < 5; i += 1) cell.click();

    expect(valuesOn(cell)).toEqual(['5']);
    // The stake is untouched by how it is drawn.
    expect(document.querySelector('#roulette-bets li').textContent).toContain('$5.00');
  });

  it('leaves a stake that genuinely needs several chips alone', () => {
    const cell = straight(17);
    for (let i = 0; i < 3; i += 1) cell.click();

    expect(valuesOn(cell)).toEqual(['1', '1', '1']);
  });

  it('draws the fewest chips that make the stake, largest at the bottom', () => {
    const cell = straight(17);
    selectChip(100);
    cell.click();
    selectChip(25);
    cell.click();
    selectChip(10);
    cell.click();
    selectChip(1);
    cell.click();

    // $136, and the order is the pile from the bottom up, not the order they were clicked.
    expect(valuesOn(cell)).toEqual(['100', '25', '10', '1']);
  });

  it('re-colours as the stake grows past a denomination', () => {
    const cell = straight(17);
    selectChip(5);
    cell.click();
    expect(valuesOn(cell)).toEqual(['5']);

    cell.click();
    expect(valuesOn(cell)).toEqual(['10']);

    cell.click();
    expect(valuesOn(cell)).toEqual(['10', '5']);
  });

  it('stacks the chips up the space rather than piling them on one spot', () => {
    const cell = straight(17);
    cell.click();
    cell.click();

    // Parsed, not compared as a string: the browser is free to write 0.00px back as 0px.
    const [first, second] = chipsOn(cell);
    expect(Number.parseFloat(first.style.bottom)).toBe(0);
    expect(Number.parseFloat(second.style.bottom)).toBeGreaterThan(0);
  });

  it('keeps a tall pile inside its own cell', () => {
    const cell = straight(17);
    selectChip(100);
    for (let i = 0; i < 40; i += 1) cell.click();

    const chips = chipsOn(cell);
    expect(chips).toHaveLength(40);
    // The step shrinks as the pile grows, so forty chips do not climb over the numbers above.
    const top = Number.parseFloat(chips[chips.length - 1].style.bottom);
    expect(top).toBeLessThanOrEqual(20);
  });

  it('totals the pile in the bets list', () => {
    const cell = straight(17);
    cell.click();
    selectChip(25);
    cell.click();

    expect(document.querySelector('#roulette-bets li').textContent).toContain('$26.00');
    expect(document.querySelector('#roulette-staked').textContent).toContain('$26.00');
  });

  it('says the stake, since a pile of coloured discs tells a screen reader nothing', () => {
    const cell = straight(17);
    cell.click();
    cell.click();

    expect(cell.getAttribute('aria-label')).toBe('17: $2.00 staked');
  });
});

describe('taking a chip back with the right button', () => {
  beforeEach(() => mountRoulette());

  it('lifts one chip off, leaving the rest of the pile', () => {
    const cell = straight(17);
    cell.click();
    cell.click();
    cell.click();

    rightClick(cell);

    expect(chipsOn(cell)).toHaveLength(2);
    expect(document.querySelector('#roulette-bets li').textContent).toContain('$2.00');
  });

  it('takes the chip off the top of the pile, which is the smallest one on it', () => {
    const cell = straight(17);
    selectChip(100);
    cell.click();
    selectChip(1);
    cell.click();

    // $101 is drawn as a $100 with a $1 on top, so the $1 is what comes off.
    expect(valuesOn(cell)).toEqual(['100', '1']);
    rightClick(cell);

    expect(valuesOn(cell)).toEqual(['100']);
    expect(document.querySelector('#roulette-bets li').textContent).toContain('$100.00');
  });

  it('takes a coloured-up chip off whole, since that is the chip on the cloth', () => {
    const cell = straight(17);
    for (let i = 0; i < 5; i += 1) cell.click();

    // Five singles are one $5 chip now, so lifting it takes the whole $5.
    rightClick(cell);

    expect(chipsOn(cell)).toHaveLength(0);
    expect(document.querySelectorAll('#roulette-bets li')).toHaveLength(0);
  });

  it('drops the bet entirely once the last chip is lifted', () => {
    const cell = straight(17);
    cell.click();

    rightClick(cell);

    expect(chipsOn(cell)).toHaveLength(0);
    expect(document.querySelectorAll('#roulette-bets li')).toHaveLength(0);
    expect(document.querySelector('#roulette-spin').disabled).toBe(true);
    // The tooltip goes back to naming the space rather than a stake that is no longer there.
    expect(cell.getAttribute('title')).toBe('17');
    expect(cell.getAttribute('aria-label')).toBeNull();
  });

  it('suppresses the browser menu, which would otherwise open on every removal', () => {
    const cell = straight(17);
    cell.click();

    expect(rightClick(cell).defaultPrevented).toBe(true);
  });

  it('does nothing on a space with no chips on it', () => {
    const cell = straight(17);

    expect(() => rightClick(cell)).not.toThrow();
    expect(document.querySelectorAll('#roulette-bets li')).toHaveLength(0);
  });

  it('leaves the left button adding, so the two buttons do opposite things', () => {
    const cell = straight(17);

    cell.click();
    cell.click();
    rightClick(cell);
    cell.click();

    expect(chipsOn(cell)).toHaveLength(2);
  });
});

describe('sending the bets to the server', () => {
  it('sends one summed amount per space, not one entry per chip', () => {
    // Never settles: the call is all this asserts, and the wheel animation is not the subject.
    const spinRoulette = vi.fn(() => new Promise(() => {}));
    mountRoulette({ spinRoulette });

    const cell = straight(17);
    cell.click();
    selectChip(25);
    cell.click();
    straight(20).click();

    document.querySelector('#roulette-spin').click();

    // Colouring up is presentation only: the server still gets one amount per space.
    expect(spinRoulette).toHaveBeenCalledWith([
      { type: 'STRAIGHT', selection: '17', amount: 26 },
      { type: 'STRAIGHT', selection: '20', amount: 25 },
    ]);
  });
});
