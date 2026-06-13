// Small shared front-end utilities.

/** Trailing debounce: returns a wrapper that runs `fn` only after `ms` of quiet. */
export function debounce(fn, ms) {
  let timer;
  return function (...args) {
    clearTimeout(timer);
    timer = setTimeout(() => fn.apply(this, args), ms);
  };
}
