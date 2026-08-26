import { describe, it, expect, vi } from 'vitest';
import { createStore } from '../src/state/store.js';

describe('createStore', () => {
  it('exposes the initial state', () => {
    const store = createStore({ balance: 100 });
    expect(store.get()).toEqual({ balance: 100 });
  });

  it('merges a patch rather than replacing the state', () => {
    const store = createStore({ balance: 100, username: 'ann' });
    store.set({ balance: 250 });
    expect(store.get()).toEqual({ balance: 250, username: 'ann' });
  });

  it('notifies subscribers with the new and previous state', () => {
    const store = createStore({ balance: 100 });
    const listener = vi.fn();
    store.subscribe(listener);

    store.set({ balance: 250 });

    expect(listener).toHaveBeenCalledTimes(1);
    expect(listener).toHaveBeenCalledWith({ balance: 250 }, { balance: 100 });
  });

  it('does not notify when nothing actually changed', () => {
    const store = createStore({ balance: 100 });
    const listener = vi.fn();
    store.subscribe(listener);

    store.set({ balance: 100 });

    // Re-rendering the whole UI on every identical server response would be wasteful.
    expect(listener).not.toHaveBeenCalled();
  });

  it('unsubscribes cleanly', () => {
    const store = createStore({ balance: 100 });
    const listener = vi.fn();
    const unsubscribe = store.subscribe(listener);

    unsubscribe();
    store.set({ balance: 250 });

    expect(listener).not.toHaveBeenCalled();
    expect(store.listenerCount).toBe(0);
  });

  it('supports several independent subscribers', () => {
    const store = createStore({ balance: 100 });
    const first = vi.fn();
    const second = vi.fn();
    store.subscribe(first);
    store.subscribe(second);

    store.set({ balance: 250 });

    expect(first).toHaveBeenCalledTimes(1);
    expect(second).toHaveBeenCalledTimes(1);
  });
});
