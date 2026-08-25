// Single UI state + URL-hash persistence. Modules read State.get() and call State.set({...});
// every change notifies subscribers (app.js schedules a reload).
//
// SCHEMA is the one place the UI names a filter. `key` is the state key, `param` is BOTH the REST
// query-parameter name (plan §1.0) and the hash key — so the hash mirrors the API one-to-one and
// `toApiParams()` is a rename-free projection. Array fields serialise as REPEATED params
// (`#tag=steppe&tag=indo_european`), exactly as the server reads them.
window.State = (() => {
  const SCHEMA = [
    { key: 'q',        param: 'q',        kind: 'str', label: 'search' },
    { key: 'tags',     param: 'tag',      kind: 'arr', label: 'tag' },
    { key: 'authors',  param: 'author',   kind: 'arr', label: 'author' },
    { key: 'places',   param: 'place',    kind: 'arr', label: 'place' },
    { key: 'topics',   param: 'topic',    kind: 'arr', label: 'topic' },
    { key: 'status',   param: 'status',   kind: 'arr', label: 'status' },
    { key: 'yearFrom', param: 'yearFrom', kind: 'num', label: 'published from' },
    { key: 'yearTo',   param: 'yearTo',   kind: 'num', label: 'published to' },
    { key: 'from',     param: 'from',     kind: 'num', label: 'from', span: true },
    { key: 'to',       param: 'to',       kind: 'num', label: 'to', span: true },
    { key: 'minLon',   param: 'minLon',   kind: 'num', span: true },
    { key: 'minLat',   param: 'minLat',   kind: 'num', span: true },
    { key: 'maxLon',   param: 'maxLon',   kind: 'num', span: true },
    { key: 'maxLat',   param: 'maxLat',   kind: 'num', span: true },
    { key: 'sort',     param: 'sort',     kind: 'str', label: 'sort', nochip: true },
  ];
  // `span: true` = set by the timeline / viewport controls, not by the filter bar → "clear all" keeps it.
  const FILTERS = SCHEMA.filter(f => !f.span);

  const blank = f => (f.kind === 'arr' ? [] : null);
  const empty = () => Object.fromEntries(SCHEMA.map(f => [f.key, blank(f)]));
  const isSet = (f, v) => (f.kind === 'arr' ? Array.isArray(v) && v.length > 0 : v != null && v !== '');
  const coerce = (f, v) =>
    f.kind === 'arr' ? (Array.isArray(v) ? v.map(String).filter(x => x !== '') : blank(f))
    : f.kind === 'num' ? (v === '' || v == null || Number.isNaN(Number(v)) ? null : Number(v))
    : (v == null || v === '' ? null : String(v));

  let s = empty();
  const subs = [];

  const fromHash = () => {
    const h = new URLSearchParams(location.hash.replace(/^#/, ''));
    const o = {};
    for (const f of SCHEMA) {
      if (f.kind === 'arr') { const all = h.getAll(f.param); if (all.length) o[f.key] = all; }
      else if (h.has(f.param)) o[f.key] = coerce(f, h.get(f.param));
    }
    const legacyRegion = h.get('region'); // pre-chips links used a single free-text region
    if (legacyRegion && !o.places) o.places = [legacyRegion];
    return o;
  };

  const toHash = () => {
    const h = new URLSearchParams();
    for (const f of SCHEMA) {
      const v = s[f.key];
      if (!isSet(f, v)) continue;
      if (f.kind === 'arr') v.forEach(x => h.append(f.param, x));
      else h.set(f.param, String(v));
    }
    const qs = h.toString();
    history.replaceState(null, '', qs ? '#' + qs : location.pathname + location.search);
  };

  const notify = () => subs.forEach(f => f({ ...s }));

  return {
    /** Merge defaults (from /api/config), then whatever the URL hash carries — so reload restores. */
    init(defaults) {
      s = empty();
      for (const [k, v] of Object.entries({ ...(defaults || {}), ...fromHash() })) {
        const f = SCHEMA.find(x => x.key === k);
        if (f) s[k] = coerce(f, v);
      }
      return this.get();
    },
    get: () => ({ ...s }),
    set(patch) {
      for (const [k, v] of Object.entries(patch || {})) {
        const f = SCHEMA.find(x => x.key === k);
        if (f) s[k] = coerce(f, v);
      }
      toHash(); notify();
    },
    /** Reset every filter-bar key; the time span and the viewport bbox are other controls' state. */
    clearFilters() {
      FILTERS.forEach(f => { s[f.key] = blank(f); });
      toHash(); notify();
    },
    subscribe: f => subs.push(f),

    /** The exact REST params object (plan §1.0) for /api/sources, /api/findings and /api/facets.
      * `opts.except` = state key(s) to drop, so a chip's own field does not constrain its own
      * suggestion counts. Ranges are normalised (the two time sliders can cross). */
    toApiParams(opts) {
      const skip = new Set([].concat((opts && opts.except) || []));
      const p = {};
      for (const f of SCHEMA) {
        if (skip.has(f.key) || skip.has(f.param)) continue;
        const v = s[f.key];
        if (!isSet(f, v)) continue;
        p[f.param] = f.kind === 'arr' ? v.slice() : v;
      }
      const order = (a, b) => { if (p[a] != null && p[b] != null && p[a] > p[b]) { const t = p[a]; p[a] = p[b]; p[b] = t; } };
      order('from', 'to'); order('yearFrom', 'yearTo');
      return p;
    },

    /** Active chip-like filters, for the "no sources match — remove a chip" empty state. */
    activeChips() {
      const out = [];
      for (const f of FILTERS) {
        const v = s[f.key];
        if (f.nochip || !isSet(f, v)) continue;
        if (f.kind === 'arr') v.forEach(x => out.push({ key: f.key, label: f.label, value: x }));
        else out.push({ key: f.key, label: f.label, value: String(v) });
      }
      return out;
    },
  };
})();
