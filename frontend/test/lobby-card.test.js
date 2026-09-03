import { describe, it, expect, beforeEach } from 'vitest';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

/**
 * The lobby card's shape.
 *
 * Guest is not one of the ways you sign in, so it is not one of the tabs: the card offers the
 * two accounts routes and puts playing as a guest underneath them. main.js drives the tabs and
 * the guest button, so this covers the markup those handlers reach for.
 */

// import.meta.url is an http URL under the jsdom environment, so resolve from the project root.
const html = readFileSync(resolve(process.cwd(), 'index.html'), 'utf8');

describe('the lobby card', () => {
  beforeEach(() => {
    document.body.innerHTML = html.slice(html.indexOf('<body>') + 6, html.indexOf('</body>'));
  });

  it('offers exactly two tabs, signing in and signing up', () => {
    const tabs = [...document.querySelectorAll('.tabs button')];

    expect(tabs.map((tab) => tab.dataset.tab)).toEqual(['login', 'signup']);
  });

  it('opens on the sign-in form', () => {
    expect(document.querySelector('#tab-login').hidden).toBe(false);
    expect(document.querySelector('#tab-signup').hidden).toBe(true);
    expect(document.querySelector('.tabs button.active').dataset.tab).toBe('login');
  });

  it('says which tab is current, not only shows it', () => {
    // The buttons carry role="tab", so a screen reader needs aria-selected to follow them.
    const [login, signup] = document.querySelectorAll('.tabs button');

    expect(login.getAttribute('aria-selected')).toBe('true');
    expect(signup.getAttribute('aria-selected')).toBe('false');
  });

  it('keeps the guest button outside both forms, so one control serves either tab', () => {
    const guest = document.querySelector('#play-as-guest');

    expect(guest).not.toBeNull();
    expect(guest.closest('form')).toBeNull();
    // Not a submit button sitting loose in the card: it has its own click handler.
    expect(guest.type).toBe('button');
  });

  it('puts the guest button below whichever green button is showing', () => {
    const card = document.querySelector('.lobby-card');
    const nodes = [...card.querySelectorAll('.tabpanel, #play-as-guest')];

    // Both forms come first, so the guest button always renders under the visible one.
    expect(nodes[nodes.length - 1].id).toBe('play-as-guest');
  });

  it('does not dress the guest button up as the primary action', () => {
    const guest = document.querySelector('#play-as-guest');

    // Green is reserved for signing in and creating an account.
    expect(guest.classList.contains('primary')).toBe(false);
    expect(guest.classList.contains('guest-btn')).toBe(true);
    for (const submit of document.querySelectorAll('.tabpanel button[type="submit"]')) {
      expect(submit.classList.contains('primary')).toBe(true);
    }
  });

  it('states the age rule under the create account button', () => {
    const form = document.querySelector('#tab-signup');
    const note = form.querySelector('.age-rule-short');

    expect(note.textContent).toMatch(/21\+/);
    // Under the button, not above it: it is the last thing read before the click.
    expect(form.lastElementChild).toBe(note);
    expect(note.previousElementSibling.type).toBe('submit');
  });

  it('still says what a guest session costs you', () => {
    expect(document.querySelector('.guest-option').textContent).toMatch(/nothing is saved/i);
  });
});
