import { CasinoApi } from './api/client.js';
import { session } from './state/store.js';
import { el, clear, setText, show, qs, qsa } from './lib/dom.js';
import { formatMoney, formatDelta } from './lib/money.js';
import { createBlackjackView } from './games/blackjack.js';
import { createSlotsView } from './games/slots.js';
import { createRouletteView } from './games/roulette.js';

const api = new CasinoApi();

const VIEWS = ['lobby', 'floor', 'blackjack', 'slots', 'roulette', 'history', 'account', 'admin'];

let tableConfig = null;

// Slots are absent: a machine is not a table game and carries no admin-managed limits.
const GAME_KEYS = { BLACKJACK: 'blackjack', ROULETTE: 'roulette' };

/**
 * Limits and the live balance, handed to each game view so it can pre-validate a bet.
 *
 * Limits are per game, so a view is given its own game's bounds rather than one house-wide
 * pair. `game` is the view asking; without it every view would have to know where in the
 * config its own limits live.
 */
function currentConfig(game) {
  const { balance } = session.get();
  const limits = game ? tableConfig?.[GAME_KEYS[game]] : undefined;
  return {
    game,
    minBet: limits ? Number(limits.minBet) : undefined,
    maxBet: limits ? Number(limits.maxBet) : undefined,
    maxRouletteBets: tableConfig?.maxRouletteBets,
    balance: balance === null || balance === undefined ? undefined : Number(balance),
    blackjack: tableConfig?.blackjack,
    slots: tableConfig?.slots,
    roulette: tableConfig?.roulette,
  };
}

const shared = {
  api,
  onBalance: (balance) => session.set({ balance }),
  onError: (message) => toast(message, 'error'),
};

const blackjack = createBlackjackView({ ...shared, config: () => currentConfig('BLACKJACK') });
const slots = createSlotsView({ ...shared, config: () => currentConfig('SLOTS') });
const roulette = createRouletteView({ ...shared, config: () => currentConfig('ROULETTE') });

// ---------------------------------------------------------------- navigation

function showView(name) {
  for (const view of VIEWS) {
    show(qs(`#view-${view}`), view === name);
  }
  for (const button of qsa('.topnav button')) {
    button.classList.toggle('active', button.dataset.view === name);
  }
  show(qs('#topbar'), name !== 'lobby');

  if (name === 'blackjack') blackjack.resume();
  if (name === 'history') loadHistory();
  if (name === 'account') renderAccount();
  if (name === 'admin') {
    renderLimitsForm();
    loadAudit();
  }
  window.location.hash = name === 'lobby' ? '' : name;
}

for (const button of qsa('[data-view]')) {
  button.addEventListener('click', () => showView(button.dataset.view));
}
qs('#nav-lobby').addEventListener('click', () => showView('floor'));

// ---------------------------------------------------------------- lobby

for (const tab of qsa('.tabs button')) {
  tab.addEventListener('click', () => {
    for (const other of qsa('.tabs button')) other.classList.toggle('active', other === tab);
    for (const panel of ['guest', 'login', 'signup']) {
      show(qs(`#tab-${panel}`), panel === tab.dataset.tab);
    }
    setText(qs('#lobby-error'), '');
  });
}

qs('#play-as-guest').addEventListener('click', async () => {
  await enterCasino(() => api.playAsGuest());
});

qs('#tab-login').addEventListener('submit', async (event) => {
  event.preventDefault();
  await enterCasino(() => api.signIn(
    qs('#login-username').value.trim(),
    qs('#login-password').value,
  ));
});

qs('#tab-signup').addEventListener('submit', async (event) => {
  event.preventDefault();
  await enterCasino(() => api.signUp(
    qs('#signup-username').value.trim(),
    qs('#signup-password').value,
  ));
});

async function enterCasino(signIn) {
  setText(qs('#lobby-error'), '');
  try {
    const identity = await signIn();
    session.set({ identity, status: 'signed-in' });
    await refreshBalance();
    await loadConfig();
    showView('floor');
    // Clear the password fields so they are not left sitting in the DOM.
    qs('#login-password').value = '';
    qs('#signup-password').value = '';
  } catch (error) {
    setText(qs('#lobby-error'), error.message);
  }
}

qs('#sign-out').addEventListener('click', () => {
  api.clearSession();
  session.set({ identity: null, balance: null, status: 'signed-out' });
  showView('lobby');
});

// ---------------------------------------------------------------- account

async function refreshBalance() {
  try {
    const me = await api.me();
    session.set({
      balance: me.balance,
      identity: { uid: me.uid, username: me.username, role: me.role },
    });
  } catch (error) {
    if (error.isAuthFailure) returnToLobby();
  }
}

function returnToLobby() {
  session.set({ identity: null, balance: null, status: 'signed-out' });
  showView('lobby');
  toast('Your session ended. Sign in again to keep playing.', 'error');
}

session.subscribe(({ identity, balance }) => {
  setText(qs('#account-name'), identity?.username ?? '');
  setText(qs('#account-role'), identity?.role ?? '');
  setText(qs('#account-balance'), formatMoney(balance ?? 0));

  show(qs('#nav-admin'), identity?.role === 'ADMIN');
  // A guest has no stored history, so the tab would only ever be empty.
  show(qs('#nav-history'), identity?.role !== 'GUEST');
});

/** States the password rule wherever a password is chosen, from the one server-side figure. */
function applyPasswordPolicy() {
  const min = tableConfig?.passwordMinLength;
  if (!min) return;
  for (const field of qsa('#signup-password, #new-password')) {
    field.minLength = min;
  }
  for (const node of qsa('.pw-rule')) {
    setText(node, `At least ${min} characters.`);
  }
}

async function loadConfig() {
  try {
    tableConfig = await api.config();
    applyPasswordPolicy();
    blackjack.describeRules(tableConfig);
    slots.describeMachine(tableConfig);
    roulette.describeTable(tableConfig);
    renderFloor();
  } catch {
    // The floor still works without it; the server enforces every limit regardless.
  }
}

/** Each game's own range, since the limits are no longer house-wide. */
function gameLimitsSummary() {
  const tables = [['Blackjack', tableConfig?.blackjack], ['Roulette', tableConfig?.roulette]]
    .filter(([, cfg]) => cfg)
    .map(([name, cfg]) => `${name} ${formatMoney(cfg.minBet)}-${formatMoney(cfg.maxBet)}`);
  // The machine has no minimum, so quoting it a range would misdescribe it.
  if (tableConfig?.slots) {
    tables.push(`Slots any stake up to ${formatMoney(tableConfig.slots.maxTotalBet)} a spin`);
  }
  return tables.join(', ');
}

function renderFloor() {
  const { identity } = session.get();
  setText(qs('#floor-greeting'), identity?.role === 'GUEST'
    ? 'Playing as a guest. Nothing is saved, and your chips reset with your session.'
    : `Signed in as ${identity?.username ?? ''}.`);

  if (!tableConfig) return;
  setText(qs('#floor-blackjack-detail'),
    `${tableConfig.blackjack.decks} decks \u00b7 pays ${tableConfig.blackjack.blackjackPays}`);
  setText(qs('#floor-slots-detail'), `3 reels \u00b7 ${tableConfig.slots.rtp}% RTP`);
  setText(qs('#floor-roulette-detail'), 'Single zero \u00b7 2.70% edge');

  const panel = qs('#odds-panel');
  clear(panel);
  panel.append(el('p', {
    text: `Table limits ${gameLimitsSummary()} `
      + `\u00b7 up to ${tableConfig.maxRouletteBets} bets per roulette spin.`,
  }));
  panel.append(el('p', {
    text: 'Every outcome is decided on the server using a cryptographic random source. '
      + 'These are the real published odds.',
  }));
}

// ---------------------------------------------------------------- account

/** The account page, reached by clicking your own name in the top bar. */
function renderAccount() {
  const { identity, balance } = session.get();
  const guest = identity?.role === 'GUEST';

  setText(qs('#account-subtitle'), guest
    ? 'You are playing as a guest. Nothing about this session is stored on the server.'
    : 'Your account details and settings.');

  // A guest has no UID of their own; the session id is what identifies them, and it is what an
  // admin would credit against, so it is the useful thing to show.
  setText(qs('#account-id-label'), guest ? 'Session ID' : 'UID');
  setText(qs('#account-uid'), identity?.uid ?? '');
  setText(qs('#account-username'), guest ? '—' : (identity?.username ?? ''));
  setText(qs('#account-kind'), guest ? 'Guest' : (identity?.role === 'ADMIN' ? 'Admin' : 'Player'));
  setText(qs('#account-balance-detail'), formatMoney(balance ?? 0));

  // Only a registered account has a password or a username worth showing.
  show(qs('#password-section'), !guest);
  // The cashier belongs to a member and nobody else. A guest has no account to move money to
  // or from, and an admin credits balances from the admin console instead, so offering either
  // of them a deposit button would be offering something that does not apply to them.
  show(qs('#cashier-section'), identity?.role === 'PLAYER');
  show(qs('#account-username-row-label'), !guest);
  show(qs('#account-username'), !guest);

  setText(qs('#delete-heading'), guest ? 'End this session' : 'Delete account');
  setText(qs('#delete-hint'), guest
    ? 'Ends your guest session and forgets your chips. Nothing was stored, so there is nothing '
      + 'left behind.'
    : 'Deletes your account and your entire history for good. This cannot be undone.');
  setText(qs('#delete-submit'), guest ? 'End my session' : 'Delete my account');
  show(qs('#delete-password-row'), !guest);

  setText(qs('#password-error'), '');
  setText(qs('#password-success'), '');
  setText(qs('#delete-error'), '');
  qs('#current-password').value = '';
  qs('#new-password').value = '';
  qs('#delete-password').value = '';
}

qs('#password-form').addEventListener('submit', async (event) => {
  event.preventDefault();
  setText(qs('#password-error'), '');
  setText(qs('#password-success'), '');

  try {
    const result = await api.changePassword(
      qs('#current-password').value,
      qs('#new-password').value,
    );
    setText(qs('#password-success'), result.message);
    // Do not leave either password sitting in the DOM.
    qs('#current-password').value = '';
    qs('#new-password').value = '';
  } catch (error) {
    setText(qs('#password-error'), error.message);
  }
});

qs('#delete-form').addEventListener('submit', async (event) => {
  event.preventDefault();
  setText(qs('#delete-error'), '');

  const { identity } = session.get();
  const guest = identity?.role === 'GUEST';
  const confirmed = window.confirm(guest
    ? 'End this guest session? Your chips will be gone.'
    : 'Delete your account and all of its history? This cannot be undone.');
  if (!confirmed) return;

  try {
    const result = await api.deleteAccount(qs('#delete-password').value);
    qs('#delete-password').value = '';
    // The account is gone, so the token that named it is worthless: drop it and start over.
    api.clearSession();
    session.set({ identity: null, balance: null, status: 'signed-out' });
    showView('lobby');
    toast(result.message, 'success');
  } catch (error) {
    setText(qs('#delete-error'), error.message);
  }
});

// ---------------------------------------------------------------- history

async function loadHistory() {
  const body = qs('#history-body');
  clear(body);
  show(qs('#history-totals'), false);

  const { identity } = session.get();
  if (identity?.role === 'GUEST') {
    setText(qs('#history-note'), 'Guests have no stored history. Nothing about a guest is saved.');
    return;
  }
  setText(qs('#history-note'), 'Every movement of money on your account.');

  try {
    const { entries, totals } = await api.history(50);
    renderHistoryTotals(totals);
    for (const entry of entries) {
      const amount = Number.parseFloat(entry.amount);
      body.append(el('tr', {
        children: [
          el('td', { text: new Date(entry.at).toLocaleString() }),
          el('td', { text: entry.type }),
          el('td', { text: entry.game }),
          el('td', {
            className: `amount ${amount >= 0 ? 'positive' : 'negative'}`,
            text: formatMoney(amount),
          }),
          el('td', { className: 'amount', text: formatMoney(entry.balanceAfter) }),
          el('td', { text: entry.detail ?? '' }),
        ],
      }));
    }
    if (entries.length === 0) {
      body.append(el('tr', {
        children: [el('td', { attrs: { colspan: '6' }, text: 'No activity yet.' })],
      }));
    }
  } catch (error) {
    toast(error.message, 'error');
  }
}

/**
 * Lifetime win/loss, served by the API rather than summed from the rows on screen: the table
 * shows the most recent 50 entries, so a total added up here would silently mean something
 * different from what its label claims.
 */
function renderHistoryTotals(totals) {
  const footer = qs('#history-totals');
  if (!totals) {
    show(footer, false);
    return;
  }
  const net = Number.parseFloat(totals.net ?? '0');

  setText(qs('#total-wagered'), formatMoney(totals.wagered));
  setText(qs('#total-returned'), formatMoney(totals.returned));

  const netCell = qs('#total-net');
  setText(netCell, formatDelta(net));
  netCell.className = `amount ${net >= 0 ? 'positive' : 'negative'}`;
  setText(qs('#total-note'), 'Across all play; sign-up and admin credits excluded.');

  show(footer, true);
}

// ---------------------------------------------------------------- admin

qs('#admin-credit-form').addEventListener('submit', async (event) => {
  event.preventDefault();
  setText(qs('#admin-error'), '');
  setText(qs('#admin-success'), '');

  const target = qs('#admin-target').value.trim();
  const amount = Number.parseFloat(qs('#admin-amount').value);

  try {
    const result = target
      ? await api.credit(target, amount)
      : await api.creditSelf(amount);

    setText(qs('#admin-success'),
      `Credited ${formatMoney(result.amountCredited)} to ${result.targetUsername} `
      + `(${result.targetKind}). New balance ${formatMoney(result.newBalance)}.`);

    // Crediting yourself changes the balance shown in the header.
    if (!target || target === session.get().identity?.uid) {
      session.set({ balance: result.newBalance });
    }
    await loadAudit();
  } catch (error) {
    setText(qs('#admin-error'), error.message);
  }
});

/** The table games, the only ones whose limits an admin sets. */
const LIMIT_GAMES = [
  ['BLACKJACK', 'Blackjack'],
  ['ROULETTE', 'Roulette'],
];

/**
 * One row per game, each saved on its own.
 *
 * Saving per row rather than all at once keeps a rejected value from silently discarding the
 * other games' edits: the row that failed reports why and the rest are untouched.
 */
function renderLimitsForm() {
  if (!tableConfig) return;
  const body = qs('#limits-body');
  clear(body);

  for (const [game, label] of LIMIT_GAMES) {
    const cfg = tableConfig[GAME_KEYS[game]];
    if (!cfg) continue;

    const min = el('input', {
      attrs: {
        type: 'number', min: '0.01', step: '0.01', required: 'required',
        value: Number(cfg.minBet).toFixed(2), 'aria-label': `${label} minimum bet`,
      },
    });
    const max = el('input', {
      attrs: {
        type: 'number', min: '0.01', step: '0.01', required: 'required',
        max: String(tableConfig.maxConfigurableBet),
        value: Number(cfg.maxBet).toFixed(2), 'aria-label': `${label} maximum bet`,
      },
    });
    const save = el('button', {
      className: 'primary',
      text: 'Save',
      attrs: { type: 'button', 'data-game': game },
    });
    save.addEventListener('click', () => saveLimits(game, label, min.value, max.value));

    body.append(el('tr', {
      children: [
        el('th', { text: label, attrs: { scope: 'row' } }),
        el('td', { children: [min] }),
        el('td', { children: [max] }),
        el('td', { children: [save] }),
      ],
    }));
  }

  setText(qs('#limits-ceiling'),
    `No maximum may be raised past ${formatMoney(tableConfig.maxConfigurableBet)}, `
    + 'which is fixed in configuration.');
}

async function saveLimits(game, label, minValue, maxValue) {
  setText(qs('#limits-error'), '');
  setText(qs('#limits-success'), '');

  try {
    const result = await api.setLimits(game, Number.parseFloat(minValue), Number.parseFloat(maxValue));
    const saved = result.games[game];
    setText(qs('#limits-success'),
      `${label} limits are now ${formatMoney(saved.minBet)} to ${formatMoney(saved.maxBet)}.`);

    // Every bet form validates against these, so refresh what the rest of the UI believes.
    await loadConfig();
    renderLimitsForm();
    await loadAudit();
  } catch (error) {
    setText(qs('#limits-error'), `${label}: ${error.message}`);
  }
}

async function loadAudit() {
  const body = qs('#audit-body');
  clear(body);
  try {
    const entries = await api.auditLog(50);
    for (const entry of entries) {
      body.append(el('tr', {
        children: [
          el('td', { text: new Date(entry.at).toLocaleString() }),
          el('td', { text: entry.actorUsername }),
          el('td', { text: entry.action }),
          el('td', { className: 'uid', text: entry.targetRef }),
          el('td', { text: entry.targetKind }),
          el('td', { className: 'amount', text: formatMoney(entry.amount) }),
          el('td', { text: entry.sourceIp ?? '' }),
        ],
      }));
    }
  } catch (error) {
    toast(error.message, 'error');
  }
}

// ---------------------------------------------------------------- toast

let toastTimer;
function toast(message, kind = '') {
  const node = qs('#toast');
  setText(node, message);
  node.className = `toast ${kind}`;
  node.hidden = false;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => { node.hidden = true; }, 4000);
}

// ---------------------------------------------------------------- startup

async function start() {
  // Public, and the lobby's sign-up form needs it to state the password rule.
  await loadConfig();

  if (api.isSignedIn) {
    session.set({ identity: api.identity, status: 'signed-in' });
    await refreshBalance();
    await loadConfig();

    if (session.get().identity) {
      const requested = window.location.hash.replace('#', '');
      showView(VIEWS.includes(requested) && requested !== 'lobby' ? requested : 'floor');
      return;
    }
  }
  showView('lobby');
}

start();
