import { describe, it, expect, beforeEach } from 'vitest';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

/**
 * The account page's shape.
 *
 * The behaviour lives in main.js, which boots the whole app on import, so this covers the markup
 * contract that behaviour relies on: the username is a real control, the cashier buttons are
 * inert, and deleting is not styled as an ordinary action.
 */

// import.meta.url is an http URL under the jsdom environment, so resolve from the project root.
const html = readFileSync(resolve(process.cwd(), 'index.html'), 'utf8');

describe('the account page', () => {
  beforeEach(() => {
    document.body.innerHTML = html.slice(html.indexOf('<body>') + 6, html.indexOf('</body>'));
  });

  it('makes the username a button that opens the account view', () => {
    const name = document.querySelector('#account-name');

    expect(name.tagName).toBe('BUTTON');
    expect(name.dataset.view).toBe('account');
    expect(document.querySelector('#view-account')).not.toBeNull();
  });

  it('shows an identifier, a username, a type and a balance', () => {
    for (const id of ['#account-uid', '#account-username', '#account-kind',
      '#account-balance-detail']) {
      expect(document.querySelector(id), id).not.toBeNull();
    }
  });

  it('offers a password change that asks for the current password too', () => {
    const form = document.querySelector('#password-form');

    expect(form).not.toBeNull();
    expect(form.querySelector('#current-password').type).toBe('password');
    expect(form.querySelector('#new-password').type).toBe('password');
  });

  it('shows cashier buttons that cannot be used', () => {
    const deposit = document.querySelector('#deposit');
    const withdraw = document.querySelector('#withdraw');

    // Present, so the page shows where a cashier would live...
    expect(deposit).not.toBeNull();
    expect(withdraw).not.toBeNull();
    // ...but inert, and announced as such rather than silently dead on click.
    expect(deposit.disabled).toBe(true);
    expect(withdraw.disabled).toBe(true);
    expect(deposit.getAttribute('aria-disabled')).toBe('true');
    expect(withdraw.getAttribute('aria-disabled')).toBe('true');
  });

  it('keeps the cashier in a section of its own, so it can be shown to members only', () => {
    // A guest has no account to move money to or from, and an admin credits from the console;
    // main.js hides this whole section for both.
    const cashier = document.querySelector('#cashier-section');

    expect(cashier).not.toBeNull();
    expect(cashier.contains(document.querySelector('#deposit'))).toBe(true);
    expect(cashier.contains(document.querySelector('#withdraw'))).toBe(true);
  });

  it('states the password rule once, for the server to fill in', () => {
    // No hardcoded length in the markup: it comes from /api/config so sign-up and this form
    // cannot disagree with the policy or with each other.
    expect(document.querySelector('#new-password').getAttribute('minlength')).toBeNull();
    expect(document.querySelector('#signup-password').getAttribute('minlength')).toBeNull();
    expect(document.querySelectorAll('.pw-rule').length).toBe(2);
  });

  it('says plainly that no real money is involved', () => {
    const cashier = document.querySelector('#deposit').closest('.account-section');

    expect(cashier.textContent).toMatch(/play-money simulation/i);
    expect(cashier.textContent).toMatch(/do nothing/i);
  });

  it('does not dress deleting an account up as an ordinary button', () => {
    const submit = document.querySelector('#delete-submit');

    expect(submit).not.toBeNull();
    expect(submit.classList.contains('danger-btn')).toBe(true);
    expect(submit.classList.contains('primary')).toBe(false);
    // A password field is there for a registered account; main.js hides it for a guest.
    expect(document.querySelector('#delete-password')).not.toBeNull();
  });
});
