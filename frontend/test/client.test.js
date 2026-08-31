import { describe, it, expect, beforeEach, vi } from 'vitest';
import { CasinoApi, ApiError } from '../src/api/client.js';

/** An in-memory stand-in for sessionStorage. */
function memoryStorage() {
  const map = new Map();
  return {
    getItem: (k) => (map.has(k) ? map.get(k) : null),
    setItem: (k, v) => map.set(k, v),
    removeItem: (k) => map.delete(k),
  };
}

function jsonResponse(status, body) {
  return {
    ok: status >= 200 && status < 300,
    status,
    text: async () => (body === undefined ? '' : JSON.stringify(body)),
  };
}

const GUEST_SESSION = {
  token: 'tok-123', uid: 'guest-abc', username: 'guest', role: 'GUEST', balance: '10000.00',
};
const PLAYER_SESSION = {
  token: 'tok-123', uid: 'u', username: 'ann', role: 'PLAYER', balance: '100.00',
};

describe('CasinoApi', () => {
  let storage;
  let fetchMock;
  let api;

  beforeEach(() => {
    storage = memoryStorage();
    fetchMock = vi.fn();
    api = new CasinoApi({ storage, fetchImpl: fetchMock });
  });

  it('starts signed out', () => {
    expect(api.isSignedIn).toBe(false);
    expect(api.identity).toBeNull();
  });

  it('stores the session after a guest sign-in', async () => {
    fetchMock.mockResolvedValue(jsonResponse(200, GUEST_SESSION));

    const identity = await api.playAsGuest();

    expect(identity).toEqual({ uid: 'guest-abc', username: 'guest', role: 'GUEST' });
    expect(api.isSignedIn).toBe(true);
    expect(storage.getItem('casino.token')).toBe('tok-123');
  });

  it('restores a session from storage on construction', () => {
    storage.setItem('casino.token', 'tok-restored');
    storage.setItem('casino.identity', JSON.stringify({ uid: 'u1', username: 'ann', role: 'PLAYER' }));

    const restored = new CasinoApi({ storage, fetchImpl: fetchMock });

    expect(restored.isSignedIn).toBe(true);
    expect(restored.identity.username).toBe('ann');
  });

  it('sends the bearer token on an authenticated call', async () => {
    fetchMock.mockResolvedValue(jsonResponse(200, PLAYER_SESSION));
    await api.signIn('ann', 'correct-horse-9');

    fetchMock.mockResolvedValue(jsonResponse(200, { balance: '100.00' }));
    await api.me();

    const [, options] = fetchMock.mock.calls.at(-1);
    expect(options.headers.Authorization).toBe('Bearer tok-123');
  });

  it('does not send an Authorization header when signed out', async () => {
    fetchMock.mockResolvedValue(jsonResponse(200, {}));
    await api.config();

    const [, options] = fetchMock.mock.calls.at(-1);
    expect(options.headers.Authorization).toBeUndefined();
  });

  it('raises an ApiError carrying the server message', async () => {
    fetchMock.mockResolvedValue(jsonResponse(400, {
      status: 400, error: 'INVALID_REQUEST', message: 'Maximum bet is $5,000.00.',
    }));

    await expect(api.spinSlots(99999)).rejects.toMatchObject({
      name: 'ApiError',
      status: 400,
      message: 'Maximum bet is $5,000.00.',
    });
  });

  it('surfaces field errors from a validation failure', async () => {
    fetchMock.mockResolvedValue(jsonResponse(400, {
      status: 400,
      error: 'VALIDATION_FAILED',
      message: 'Some fields need attention.',
      fieldErrors: { password: 'Password must be at least 10 characters.' },
    }));

    await expect(api.signUp('bob', 'short')).rejects.toMatchObject({
      fieldErrors: { password: 'Password must be at least 10 characters.' },
    });
  });

  it('clears the session when the server rejects the token', async () => {
    fetchMock.mockResolvedValue(jsonResponse(200, PLAYER_SESSION));
    await api.signIn('ann', 'correct-horse-9');
    expect(api.isSignedIn).toBe(true);

    fetchMock.mockResolvedValue(jsonResponse(401, {
      status: 401, error: 'UNAUTHORIZED', message: 'Sign in to continue.',
    }));

    await expect(api.me()).rejects.toBeInstanceOf(ApiError);
    // A dead token must not linger, or every later call fails the same way.
    expect(api.isSignedIn).toBe(false);
    expect(storage.getItem('casino.token')).toBeNull();
  });

  it('reports a network failure as a friendly error rather than throwing raw', async () => {
    fetchMock.mockRejectedValue(new TypeError('Failed to fetch'));

    await expect(api.config()).rejects.toMatchObject({
      status: 0,
      code: 'NETWORK_ERROR',
    });
  });

  it('tolerates a non-JSON body without crashing', async () => {
    fetchMock.mockResolvedValue({
      ok: false, status: 502, text: async () => '<html>Bad Gateway</html>',
    });

    await expect(api.config()).rejects.toMatchObject({
      status: 502,
      message: 'Something went wrong.',
    });
  });

  it('signing out forgets the token', async () => {
    fetchMock.mockResolvedValue(jsonResponse(200, PLAYER_SESSION));
    await api.signIn('ann', 'correct-horse-9');

    api.clearSession();

    expect(api.isSignedIn).toBe(false);
    expect(storage.getItem('casino.identity')).toBeNull();
  });

  it('posts game actions to the right endpoints', async () => {
    fetchMock.mockResolvedValue(jsonResponse(200, {}));

    await api.dealBlackjack(25);
    expect(fetchMock.mock.calls.at(-1)[0]).toBe('/api/games/blackjack/deal');
    expect(JSON.parse(fetchMock.mock.calls.at(-1)[1].body)).toEqual({ bet: 25, hands: 1 });

    await api.dealBlackjack(25, 4);
    expect(JSON.parse(fetchMock.mock.calls.at(-1)[1].body)).toEqual({ bet: 25, hands: 4 });

    await api.spinRoulette([{ type: 'COLOR', selection: 'RED', amount: 5 }]);
    expect(fetchMock.mock.calls.at(-1)[0]).toBe('/api/games/roulette/spin');
  });

  it('survives a storage backend that throws', () => {
    const hostile = {
      getItem() { throw new Error('blocked'); },
      setItem() { throw new Error('blocked'); },
      removeItem() { throw new Error('blocked'); },
    };

    // Private browsing can throw on storage access; the client must still work in-memory.
    const resilient = new CasinoApi({ storage: hostile, fetchImpl: fetchMock });
    expect(resilient.isSignedIn).toBe(false);
    expect(() => resilient.setSession(PLAYER_SESSION)).not.toThrow();
    expect(resilient.isSignedIn).toBe(true);
  });
});
