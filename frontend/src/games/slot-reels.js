import { el, clear } from '../lib/dom.js';

/**
 * The reels of the slot machine, drawn as physical strips that turn.
 *
 * Each reel is a window onto an endless strip of symbols. Only the rows on the glass plus one
 * either side are real elements; as the strip moves they are shuffled along and redrawn, so a
 * reel can turn any distance at any speed without building a tall column of nodes.
 *
 * What slides past is the machine's own reel strip, in strip order, not a random blur. When the
 * server's answer arrives each reel is re-threaded, above the glass where it cannot be seen, so
 * that the last few symbols to roll in are exactly the ones that sit above its stop, the way a
 * real reel decelerates into its stop. The window the server reports is then pinned onto the
 * landing rows, so what the player is shown is always what they were paid, even if the strip
 * the browser holds is stale.
 *
 * Positions are measured in symbols. A reel's position is the strip index on its top row, and a
 * falling reel is one whose position is decreasing: new symbols come in from the top.
 */

const MAX_SPEED = 22;          // symbols a second at full spin
const KICK = 0.22;             // how far a reel is pulled back before it lets go
const KICK_MS = 130;
const SPIN_UP_MS = 300;        // from the top of the kick to full speed
const START_GAP_MS = 90;       // reels let go left to right
const MIN_SPIN_MS = 900;       // the first reel will not stop sooner than this
const STOP_GAP_MS = 380;       // between one reel stopping and the next
const TEASE_MS = 900;          // extra spin on a reel that could complete a big line
const STOP_MS = 650;           // deceleration from full speed onto the stop
const OVERSHOOT = 0.16;        // the reel runs a little past its stop and settles back
const SETTLE_MS = 200;
const BLUR_SPEED = 9;          // above this the symbols smear
const MAX_FRAME_MS = 100;      // a stalled tab resumes where it was rather than leaping ahead

/**
 * @param {HTMLElement} container the window the reels are drawn into
 * @param {{ count: number, rows: number, glyph: (symbol: string) => Node, strip: string[] }} options
 */
export function createReels(container, { count, rows, glyph, strip: initialStrip }) {
  let strip = [...initialStrip];
  let spinStart = 0;
  let frame = null;
  let lastFrame = 0;

  const reels = [];
  for (let index = 0; index < count; index += 1) reels.push(buildReel(index));
  for (const reel of reels) place(reel, randomStop());

  function buildReel(index) {
    const node = el('div', { className: 'reel', attrs: { 'data-reel': String(index) } });
    const cells = [];
    const stops = [];
    // One cell above the glass, the rows on it, one below. The rows keep their identity at
    // rest, so a win highlight goes on the element that is showing the winning symbol.
    for (let slot = 0; slot < rows + 2; slot += 1) {
      const onGlass = slot >= 1 && slot <= rows;
      const cell = el('div', {
        className: onGlass ? 'reel-cell stop' : 'reel-cell',
        attrs: onGlass ? { 'data-row': String(slot - 1) } : { 'aria-hidden': 'true' },
      });
      cells.push(cell);
      if (onGlass) stops.push(cell);
      node.append(cell);
    }
    container.append(node);
    return {
      node, cells, stops,
      pos: 0, speed: 0, shift: 0,
      symbols: new Map(), drawn: [],
      phase: 'idle', phaseStart: 0, startAt: 0,
      from: 0, target: 0, distance: 0, duration: 0,
      plan: null, restWindow: [],
    };
  }

  function mod(n, m) {
    return ((n % m) + m) % m;
  }

  function randomStop() {
    return Math.floor(Math.random() * strip.length);
  }

  function windowAt(stop) {
    return Array.from({ length: rows }, (_, row) => strip[mod(stop - 1 + row, strip.length)]);
  }

  /** The symbol at a strip position, fixed the first time it is asked for. */
  function symbolAt(reel, index) {
    if (!reel.symbols.has(index)) {
      reel.symbols.set(index, strip[mod(index + reel.shift, strip.length)]);
    }
    return reel.symbols.get(index);
  }

  function visibleWindow(reel) {
    const top = Math.round(reel.pos);
    return Array.from({ length: rows }, (_, row) => symbolAt(reel, top + row));
  }

  function draw(reel) {
    const base = Math.floor(reel.pos);
    const frac = reel.pos - base;
    reel.cells.forEach((cell, slot) => {
      const index = base - 1 + slot;
      if (reel.drawn[slot] !== index) {
        reel.drawn[slot] = index;
        clear(cell);
        cell.append(glyph(symbolAt(reel, index)));
      }
      const offset = (slot - 1 - frac).toFixed(4);
      cell.style.transform = `translateY(calc(${offset} * var(--pitch)))`;
    });
  }

  function label(reel) {
    reel.stops.forEach((cell, row) => {
      cell.setAttribute('aria-label', symbolAt(reel, Math.round(reel.pos) + row));
    });
  }

  /** Sets a reel straight down on a stop, with no motion. */
  function place(reel, stop, window = windowAt(stop)) {
    reel.pos = Math.round(reel.pos);
    reel.symbols.clear();
    if (Number.isInteger(stop)) reel.shift = stop - 1 - reel.pos;
    window.forEach((symbol, row) => reel.symbols.set(reel.pos + row, symbol));
    reel.drawn = [];
    reel.speed = 0;
    reel.phase = 'idle';
    reel.node.classList.remove('blur', 'tease');
    draw(reel);
    label(reel);
  }

  /** Forgets every symbol that has scrolled away, so a long session does not pile them up. */
  function prune(reel) {
    const base = Math.floor(reel.pos);
    for (const index of [...reel.symbols.keys()]) {
      if (index < base - 1 || index > base + rows) reel.symbols.delete(index);
    }
  }

  function reduceMotion() {
    return Boolean(globalThis.matchMedia?.('(prefers-reduced-motion: reduce)')?.matches);
  }

  function requestFrame(callback) {
    return globalThis.requestAnimationFrame
      ? globalThis.requestAnimationFrame(callback)
      : setTimeout(() => callback(performance.now()), 16);
  }

  function run() {
    if (frame !== null) return;
    lastFrame = performance.now();
    frame = requestFrame(tick);
  }

  // The frame's own timestamp is ignored: every schedule here is on performance.now(), and a
  // frame clock with a different origin (jsdom has one) would leave reels waiting forever.
  function tick() {
    const now = performance.now();
    const dt = Math.min(Math.max(now - lastFrame, 0), MAX_FRAME_MS);
    lastFrame = now;
    let moving = false;
    for (const reel of reels) {
      if (reel.phase === 'idle') continue;
      advance(reel, now, dt);
      if (reel.phase === 'idle') continue;
      draw(reel);
      reel.node.classList.toggle('blur', reel.speed > BLUR_SPEED);
      moving = true;
    }
    frame = moving ? requestFrame(tick) : null;
  }

  function enter(reel, phase, now) {
    reel.phase = phase;
    reel.phaseStart = now;
  }

  function advance(reel, now, dt) {
    const elapsed = now - reel.phaseStart;
    switch (reel.phase) {
      case 'waiting':
        if (now >= reel.startAt) enter(reel, 'kick', now);
        break;

      case 'kick': {
        // Pulled up against the spring, then let go.
        const u = Math.min(elapsed / KICK_MS, 1);
        reel.pos = reel.from + KICK * Math.sin(u * Math.PI / 2);
        if (u >= 1) enter(reel, 'spinning', now);
        break;
      }

      case 'spinning':
        reel.speed = Math.min(MAX_SPEED, MAX_SPEED * (elapsed / SPIN_UP_MS));
        reel.pos -= reel.speed * (dt / 1000);
        if (reel.plan && now >= reel.plan.at) beginStop(reel, now);
        break;

      case 'stopping': {
        // Cubic ease-out, timed so it leaves at the speed the reel was already turning.
        const u = Math.min(elapsed / reel.duration, 1);
        reel.pos = reel.from - reel.distance * (1 - (1 - u) ** 3);
        reel.speed = (3000 * reel.distance / reel.duration) * (1 - u) ** 2;
        if (u >= 1) enter(reel, 'settling', now);
        break;
      }

      case 'settling': {
        const u = Math.min(elapsed / SETTLE_MS, 1);
        reel.pos = reel.target - OVERSHOOT * (1 + Math.cos(Math.PI * u)) / 2;
        reel.speed = 0;
        if (u >= 1) land(reel);
        break;
      }

      default:
        break;
    }
  }

  /**
   * Commits a spinning reel to its stop.
   *
   * The landing rows are far enough down the strip that they have not been drawn yet, and every
   * position above the glass is re-threaded from the real strip, so the symbols that roll in as
   * it slows are the stop's true neighbours. The one seam this leaves is off the glass and goes
   * past at full speed.
   */
  function beginStop(reel, now) {
    const { window, stop } = reel.plan;
    const speed = Math.max(reel.speed, MAX_SPEED / 2);
    const reach = Math.max(speed * STOP_MS / 3000, rows + 1);
    const target = Math.floor(reel.pos - reach);

    const onGlass = Math.floor(reel.pos) - 1;
    for (const index of [...reel.symbols.keys()]) {
      if (index < onGlass) reel.symbols.delete(index);
    }
    if (Number.isInteger(stop)) reel.shift = stop - 1 - target;
    window.forEach((symbol, row) => reel.symbols.set(target + row, symbol));

    reel.target = target;
    reel.from = reel.pos;
    reel.distance = reel.pos - (target - OVERSHOOT);
    reel.duration = 3000 * reel.distance / speed;
    enter(reel, 'stopping', now);
  }

  function land(reel) {
    reel.pos = reel.target;
    reel.speed = 0;
    reel.phase = 'idle';
    reel.node.classList.remove('blur', 'tease');
    draw(reel);
    label(reel);

    // The next reel starts its tease once this one is down and the line is visibly on.
    const next = reels[reels.indexOf(reel) + 1];
    if (next?.plan?.tease && next.phase !== 'idle') next.node.classList.add('tease');

    const { resolve } = reel.plan;
    reel.plan = null;
    resolve();
  }

  /** Lets every reel go, left to right. */
  function start() {
    const now = performance.now();
    spinStart = now;
    const still = reduceMotion();
    reels.forEach((reel, index) => {
      prune(reel);
      reel.restWindow = visibleWindow(reel);
      reel.plan = null;
      reel.node.classList.remove('tease');
      if (still) return;
      reel.from = reel.pos;
      reel.speed = 0;
      reel.startAt = now + index * START_GAP_MS;
      enter(reel, 'waiting', now);
    });
    container.setAttribute('aria-busy', 'true');
    if (!still) run();
  }

  function schedule(windows, { stops = [], tease = [], minSpinMs = MIN_SPIN_MS, only = null }) {
    let at = Math.max(performance.now(), spinStart + minSpinMs);
    const landings = reels.map((reel, index) => {
      if (only && !only(reel)) return Promise.resolve();
      if (index > 0) at += STOP_GAP_MS;
      const teased = tease.includes(index);
      if (teased) at += TEASE_MS;
      if (reel.phase === 'idle' || reduceMotion()) {
        place(reel, stops[index], windows[index]);
        return Promise.resolve();
      }
      return new Promise((resolve) => {
        reel.plan = { window: windows[index], stop: stops[index], at, tease: teased, resolve };
      });
    });
    return Promise.all(landings).then(() => { container.removeAttribute('aria-busy'); });
  }

  /**
   * Brings the reels to rest on the server's result, left to right.
   *
   * @param {string[][]} windows reel -> row -> symbol, exactly as the server reports it
   * @param {{ stops?: number[], tease?: number[] }} options the strip index on each reel's
   *   centre row, and which reels to hold on a little longer for a line that is still live
   */
  function stopOn(windows, options = {}) {
    return schedule(windows, options);
  }

  /** Puts any still-turning reel back where it was, when the spin never happened. */
  function halt() {
    return schedule(reels.map((reel) => reel.restWindow), {
      minSpinMs: 0,
      only: (reel) => reel.phase !== 'idle',
    });
  }

  /** Takes the machine's real strip. A reel at rest keeps its place unless the strip changed. */
  function setStrip(next) {
    if (!next?.length || next.join() === strip.join()) return;
    strip = [...next];
    for (const reel of reels) {
      if (reel.phase === 'idle') place(reel, randomStop());
    }
  }

  return {
    /** reel -> row -> the cell on the glass */
    cells: reels.map((reel) => reel.stops),
    start,
    stopOn,
    halt,
    setStrip,
  };
}
