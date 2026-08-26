/**
 * The only place the browser talks to the API.
 *
 * The token is held in memory and mirrored into sessionStorage so a page refresh does not end
 * the session. sessionStorage rather than localStorage: the token dies with the tab, which
 * limits the window in which a token left on a shared machine is useful. It is still readable
 * by any script on the page, so this is a usability trade-off, not a defence against XSS; the
 * real mitigations are the strict CSP and never using innerHTML with server data.
 */

const TOKEN_KEY = 'casino.token';
const IDENTITY_KEY = 'casino.identity';

export class ApiError extends Error {
  constructor(status, code, message, fieldErrors) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.fieldErrors = fieldErrors ?? null;
  }

  /** True when the session is gone and the player needs to sign in again. */
  get isAuthFailure() {
    return this.status === 401;
  }
}

export class CasinoApi {
  constructor({ baseUrl = '', storage = globalThis.sessionStorage, fetchImpl } = {}) {
    this.baseUrl = baseUrl;
    this.storage = storage ?? null;
    this.fetch = fetchImpl ?? globalThis.fetch?.bind(globalThis);
    this.token = this.#read(TOKEN_KEY);
    this.identity = safeParse(this.#read(IDENTITY_KEY));
  }

  #read(key) {
    try {
      return this.storage?.getItem(key) ?? null;
    } catch {
      // Private browsing modes can throw on storage access.
      return null;
    }
  }

  #write(key, value) {
    try {
      if (value === null) this.storage?.removeItem(key);
      else this.storage?.setItem(key, value);
    } catch {
      /* storage unavailable; the in-memory copy still works for this page */
    }
  }

  get isSignedIn() {
    return Boolean(this.token);
  }

  setSession(authResponse) {
    this.token = authResponse.token;
    this.identity = {
      uid: authResponse.uid,
      username: authResponse.username,
      role: authResponse.role,
    };
    this.#write(TOKEN_KEY, this.token);
    this.#write(IDENTITY_KEY, JSON.stringify(this.identity));
    return this.identity;
  }

  clearSession() {
    this.token = null;
    this.identity = null;
    this.#write(TOKEN_KEY, null);
    this.#write(IDENTITY_KEY, null);
  }

  async request(path, { method = 'GET', body } = {}) {
    const headers = { Accept: 'application/json' };
    if (body !== undefined) headers['Content-Type'] = 'application/json';
    if (this.token) headers.Authorization = `Bearer ${this.token}`;

    let response;
    try {
      response = await this.fetch(`${this.baseUrl}${path}`, {
        method,
        headers,
        body: body === undefined ? undefined : JSON.stringify(body),
      });
    } catch (cause) {
      throw new ApiError(0, 'NETWORK_ERROR', 'Could not reach the casino. Check your connection.');
    }

    const payload = await readJson(response);

    if (!response.ok) {
      const error = new ApiError(
        response.status,
        payload?.error ?? 'ERROR',
        payload?.message ?? 'Something went wrong.',
        payload?.fieldErrors,
      );
      // An expired or rejected token is dead; drop it so the UI returns to the lobby.
      if (error.isAuthFailure) this.clearSession();
      throw error;
    }
    return payload;
  }

  // -- auth ---------------------------------------------------------------

  async signUp(username, password) {
    return this.setSession(
      await this.request('/api/auth/signup', { method: 'POST', body: { username, password } }),
    );
  }

  async signIn(username, password) {
    return this.setSession(
      await this.request('/api/auth/login', { method: 'POST', body: { username, password } }),
    );
  }

  async playAsGuest() {
    return this.setSession(await this.request('/api/auth/guest', { method: 'POST' }));
  }

  // -- account ------------------------------------------------------------

  me() {
    return this.request('/api/me');
  }

  history(limit = 25) {
    return this.request(`/api/me/history?limit=${encodeURIComponent(limit)}`);
  }

  config() {
    return this.request('/api/config');
  }

  // -- games --------------------------------------------------------------

  dealBlackjack(bet) {
    return this.request('/api/games/blackjack/deal', { method: 'POST', body: { bet } });
  }

  blackjackAction(roundId, action) {
    return this.request('/api/games/blackjack/action', { method: 'POST', body: { roundId, action } });
  }

  currentBlackjack() {
    return this.request('/api/games/blackjack/current');
  }

  spinSlots(bet) {
    return this.request('/api/games/slots/spin', { method: 'POST', body: { bet } });
  }

  spinRoulette(bets) {
    return this.request('/api/games/roulette/spin', { method: 'POST', body: { bets } });
  }

  // -- admin --------------------------------------------------------------

  creditSelf(amount) {
    return this.request('/api/admin/credit/self', { method: 'POST', body: { amount } });
  }

  credit(targetUid, amount) {
    return this.request('/api/admin/credit', { method: 'POST', body: { targetUid, amount } });
  }

  auditLog(limit = 50) {
    return this.request(`/api/admin/audit?limit=${encodeURIComponent(limit)}`);
  }
}

async function readJson(response) {
  const text = await response.text();
  if (!text) return null;
  try {
    return JSON.parse(text);
  } catch {
    return null;
  }
}

function safeParse(value) {
  if (!value) return null;
  try {
    return JSON.parse(value);
  } catch {
    return null;
  }
}
