/**
 * Small DOM helpers.
 *
 * Every one of these sets text through textContent, never innerHTML. Usernames and server
 * messages are rendered all over this UI; assigning them as HTML would turn any one of them
 * into a script injection point.
 */

/** Creates an element with optional class names, text and attributes. */
export function el(tag, { className, text, attrs, children } = {}) {
  const node = document.createElement(tag);
  if (className) node.className = className;
  if (text !== undefined) node.textContent = String(text);
  if (attrs) {
    for (const [key, value] of Object.entries(attrs)) {
      if (value !== null && value !== undefined && value !== false) {
        node.setAttribute(key, String(value));
      }
    }
  }
  if (children) {
    for (const child of children.filter(Boolean)) {
      node.append(child);
    }
  }
  return node;
}

export function clear(node) {
  while (node.firstChild) node.removeChild(node.firstChild);
}

export function setText(node, value) {
  if (node) node.textContent = value === null || value === undefined ? '' : String(value);
}

export function show(node, visible = true) {
  if (node) node.hidden = !visible;
}

export function qs(selector, root = document) {
  return root.querySelector(selector);
}

export function qsa(selector, root = document) {
  return [...root.querySelectorAll(selector)];
}

/** Enables or disables a set of buttons in one call. */
export function setDisabled(nodes, disabled) {
  for (const node of nodes) {
    if (node) node.disabled = Boolean(disabled);
  }
}
