import { CasinoApi } from './api/client.js';
import { session } from './state/store.js';
import { el, clear, setText, show, qs, qsa } from './lib/dom.js';
import { formatMoney, formatDelta } from './lib/money.js';
import { createBlackjackView } from './games/blackjack.js';
import { createSlotsView } from './games/slots.js';
import { createRouletteView } from './games/roulette.js';

const api = new CasinoApi();

const VIEWS = ['lobby', 'floor', 'blackjack', 'slots', 'roulette', 'history', 'admin'];

let tableConfig = null;

/** Limits and the live balance, handed to each game view so it can pre-validate a bet. */
function currentConfig() {
  const { balance } = session.get();
  return {
    minBet: tableConfig ? Number(tableConfig.minBet) : undefined,
    maxBet: tableConfig ? Number(tableConfig.maxBet) : undefined,
    maxRouletteBets: tableConfig?.maxRouletteBets,
    balance: balance === null || balance === undefined ? undefined : Number(balance),
    blackjack: tableConfig?.blackjack,
    slots: tableConfig?.slots,
    roulette: tableConfig?.roulette,
  };
}

const shared = {
  api,
  config: currentConfig,
  onBalance: (balance) => session.set({ balance }),
  onError: (message) => toast(message, 'error'),
};

const blackjack = createBlackjackView(shared);
const slots = createSlotsView(shared);
const roulette = createRouletteView(shared);

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

async function loadConfig() {
  try {
    tableConfig = await api.config();
    blackjack.describeRules(tableConfig);
    slots.describeMachine(tableConfig);
    roulette.describeTable(tableConfig);
    renderFloor();
  } catch {
    // The floor still works without it; the server enforces every limit regardless.
  }
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
    text: `Table limits: ${formatMoney(tableConfig.minBet)} to ${formatMoney(tableConfig.maxBet)} `
      + `\u00b7 up to ${tableConfig.maxRouletteBets} bets per roulette spin.`,
  }));
  panel.append(el('p', {
    text: 'Every outcome is decided on the server using a cryptographic random source. '
      + 'These are the real published odds.',
  }));
}

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

/** Fills the limits form with what is currently in force. */
function renderLimitsForm() {
  if (!tableConfig) return;
  qs('#limits-min').value = Number(tableConfig.minBet).toFixed(2);
  qs('#limits-max').value = Number(tableConfig.maxBet).toFixed(2);
  qs('#limits-max').max = String(tableConfig.maxConfigurableBet);
  setText(qs('#limits-ceiling'),
    `The maximum cannot be raised past ${formatMoney(tableConfig.maxConfigurableBet)}, `
    + 'which is fixed in configuration.');
}

qs('#admin-limits-form').addEventListener('submit', async (event) => {
  event.preventDefault();
  setText(qs('#limits-error'), '');
  setText(qs('#limits-success'), '');

  const minBet = Number.parseFloat(qs('#limits-min').value);
  const maxBet = Number.parseFloat(qs('#limits-max').value);

  try {
    const result = await api.setLimits(minBet, maxBet);
    setText(qs('#limits-success'),
      `Table limits are now ${formatMoney(result.minBet)} to ${formatMoney(result.maxBet)}.`);

    // Every bet form validates against these, so refresh what the rest of the UI believes.
    await loadConfig();
    renderLimitsForm();
    await loadAudit();
  } catch (error) {
    setText(qs('#limits-error'), error.message);
  }
});

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
