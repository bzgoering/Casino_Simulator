import { describe, it, expect, beforeEach } from 'vitest';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

/**
 * The age gate's shape.
 *
 * The behaviour lives in main.js, which boots the whole app on import, so this covers the markup
 * contract that behaviour relies on: the dialog exists to be opened over the sign-up form, it
 * asks for a date and nothing else, and it is not a form that could post the date anywhere.
 */

// import.meta.url is an http URL under the jsdom environment, so resolve from the project root.
const html = readFileSync(resolve(process.cwd(), 'index.html'), 'utf8');

describe('the age gate', () => {
  beforeEach(() => {
    document.body.innerHTML = html.slice(html.indexOf('<body>') + 6, html.indexOf('</body>'));
  });

  it('is a dialog, so it can be shown modally over the lobby', () => {
    const gate = document.querySelector('#age-gate');

    expect(gate).not.toBeNull();
    expect(gate.tagName).toBe('DIALOG');
    // Closed until sign-up opens it: it must not be in the way of the lobby on load.
    expect(gate.hasAttribute('open')).toBe(false);
  });

  it('asks for a date of birth and marks it required', () => {
    const dob = document.querySelector('#age-gate-dob');

    expect(dob.type).toBe('date');
    expect(dob.required).toBe(true);
  });

  it('keeps the browser from filling or remembering the date', () => {
    // Autofill would leave the date in the browser's own store, which is exactly what the
    // casino has undertaken not to have happen.
    expect(document.querySelector('#age-gate-dob').getAttribute('autocomplete')).toBe('off');
    expect(document.querySelector('#age-gate-form').getAttribute('autocomplete')).toBe('off');
  });

  it('has no action, so the date cannot leave the page even without JavaScript', () => {
    const form = document.querySelector('#age-gate-form');

    expect(form.hasAttribute('action')).toBe(false);
    expect(form.hasAttribute('method')).toBe(false);
  });

  it('offers a way out as well as a way through', () => {
    expect(document.querySelector('#age-gate-confirm').type).toBe('submit');
    // A plain button: cancelling must not submit the date to anything.
    expect(document.querySelector('#age-gate-cancel').type).toBe('button');
  });

  it('says the date is not kept, and has somewhere to say why one was rejected', () => {
    const text = document.querySelector('#age-gate').textContent;

    expect(text).toMatch(/never sent/i);
    expect(text).toMatch(/never stored/i);
    expect(document.querySelector('#age-gate-error').getAttribute('role')).toBe('alert');
  });

  it('is announced by its own heading', () => {
    const gate = document.querySelector('#age-gate');
    const labelledBy = gate.getAttribute('aria-labelledby');

    expect(document.getElementById(labelledBy)).not.toBeNull();
  });
});
