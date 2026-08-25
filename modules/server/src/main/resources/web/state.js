// Single UI state + URL-hash persistence. Modules read State.get() and call State.set({...});
// every change notifies subscribers (app.js schedules a reload). Keys mirror the Filter contract.
window.State = (() => {
  let s = { region: '', from: null, to: null, bbox: null, inView: false };
  const subs = [];
  // The hash hands every value back as a STRING — init() coerces the typed keys listed here.
  const NUMS = ['from', 'to'];
  const BOOLS = ['inView'];
  const fromHash = () => {
    const h = new URLSearchParams(location.hash.slice(1)); const o = {};
    for (const [k, v] of h) o[k] = v;
    return o;
  };
  const coerce = o => {
    for (const k of NUMS) if (o[k] != null && o[k] !== '') o[k] = Number(o[k]);
    for (const k of BOOLS) if (o[k] != null) o[k] = (o[k] === true || o[k] === 'true');
    return o;
  };
  const toHash = () => {
    const h = new URLSearchParams();
    for (const [k, v] of Object.entries(s)) if (v != null && v !== '' && v !== false) h.set(k, v);
    history.replaceState(null, '', '#' + h.toString());
  };
  return {
    init: defaults => { s = { ...s, ...defaults, ...coerce(fromHash()) }; },
    get: () => ({ ...s }),
    set: patch => { s = { ...s, ...patch }; toHash(); subs.forEach(f => f(s)); },
    subscribe: f => subs.push(f),
  };
})();
