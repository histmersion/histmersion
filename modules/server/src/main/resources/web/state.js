// Single UI state + URL-hash persistence. Modules read State.get() and call State.set({...});
// every change notifies subscribers (app.js schedules a reload). Keys mirror the Filter contract.
window.State = (() => {
  let s = { region: '', from: null, to: null };
  const subs = [];
  const fromHash = () => {
    const h = new URLSearchParams(location.hash.slice(1)); const o = {};
    for (const [k, v] of h) o[k] = (k === 'from' || k === 'to') ? Number(v) : v;
    return o;
  };
  const toHash = () => {
    const h = new URLSearchParams();
    for (const [k, v] of Object.entries(s)) if (v != null && v !== '') h.set(k, v);
    history.replaceState(null, '', '#' + h.toString());
  };
  return {
    init: defaults => { s = { ...s, ...defaults, ...fromHash() }; },
    get: () => ({ ...s }),
    set: patch => { s = { ...s, ...patch }; toHash(); subs.forEach(f => f(s)); },
    subscribe: f => subs.push(f),
  };
})();
