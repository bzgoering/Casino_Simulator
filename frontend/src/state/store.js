/**
 * A minimal observable store.
 *
 * Balance and identity live here so every view reads the same value, and so a balance from a
 * game response updates the header without the views knowing about each other. Only the server
 * ever produces these numbers; the store just holds the latest one it was told.
 */
export function createStore(initialState = {}) {
  let state = { ...initialState };
  const listeners = new Set();

  return {
    get() {
      return state;
    },

    /** Merges a patch into the state and notifies listeners if anything actually changed. */
    set(patch) {
      const next = { ...state, ...patch };
      const changed = Object.keys(patch).some((key) => next[key] !== state[key]);
      if (!changed) return state;

      const previous = state;
      state = next;
      for (const listener of listeners) {
        listener(state, previous);
      }
      return state;
    },

    /** Subscribes to changes. Returns an unsubscribe function. */
    subscribe(listener) {
      listeners.add(listener);
      return () => listeners.delete(listener);
    },

    /** Number of active subscribers, used by tests to assert cleanup. */
    get listenerCount() {
      return listeners.size;
    },
  };
}

/** The application store: who is playing and what they hold. */
export const session = createStore({
  identity: null,
  balance: null,
  config: null,
  status: 'signed-out',
});
