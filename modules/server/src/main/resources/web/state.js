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
    // gazetteer ids (plan §1.6a). Same `place` parameter as the free-text chips above — the server
    // decides per value whether it is an id (→ finding_places + hierarchy) or a name (→ ILIKE) —
    // so toApiParams CONCATENATES array fields that share a param instead of overwriting.
    { key: 'placeIds', param: 'place',    hash: 'placeId', kind: 'arr', label: 'place' },
    { key: 'topics',   param: 'topic',    kind: 'arr', label: 'topic' },
    { key: 'status',   param: 'status',   kind: 'arr', label: 'status' },
    { key: 'yearFrom', param: 'yearFrom', kind: 'num', label: 'published from' },
    { key: 'yearTo',   param: 'yearTo',   kind: 'num', label: 'published to' },
    { key: 'from',     param: 'from',     kind: 'num', label: 'from', span: true },
    { key: 'to',       param: 'to',       kind: 'num', label: 'to', span: true },
    // viewport (plan §1.1): the map stores its bounds as one string; toApiParams() expands it into
    // minLon..maxLat only while "only papers in view" is on. Neither key is itself an API param.
    { key: 'bbox',     param: 'bbox',     kind: 'str',  span: true, api: false },
    { key: 'inView',   param: 'inView',   kind: 'bool', span: true, api: false },
    { key: 'sort',     param: 'sort',     kind: 'str', label: 'sort', nochip: true },
    // What the reader currently has OPEN — `source:<citekey>` or `article:<slug>`. Not a filter and
    // not an API parameter: it lives here because the hash is the one place the whole view is
    // written down, and "I opened a paper" has to survive a reload, a Back and a shared link
    // exactly as a chip does (plan §1.4).
    { key: 'open',     param: 'open',     kind: 'str', span: true, api: false },
  ];
  // Two fields may share an API `param` (the gazetteer ids ride on `place` alongside the free-text
  // places); each then needs its OWN hash key, or a reload cannot tell them apart and they double up.
  const hashKey = f => f.hash || f.param;
  // `span: true` = set by the timeline / viewport controls, not by the filter bar → "clear all" keeps it.
  const FILTERS = SCHEMA.filter(f => !f.span);

  const blank = f => (f.kind === 'arr' ? [] : null);
  const empty = () => Object.fromEntries(SCHEMA.map(f => [f.key, blank(f)]));
  const isSet = (f, v) => (f.kind === 'arr' ? Array.isArray(v) && v.length > 0
    : f.kind === 'bool' ? v === true : v != null && v !== '');
  const coerce = (f, v) =>
    f.kind === 'arr' ? (Array.isArray(v) ? v.map(String).filter(x => x !== '') : blank(f))
    : f.kind === 'num' ? (v === '' || v == null || Number.isNaN(Number(v)) ? null : Number(v))
    : f.kind === 'bool' ? (v === true || v === 'true')
    : (v == null || v === '' ? null : String(v));

  let s = empty();
  let base = {};                     // the /api/config defaults, kept for the popstate rebuild
  const subs = [];

  const apply = o => {
    for (const [k, v] of Object.entries(o)) {
      const f = SCHEMA.find(x => x.key === k);
      if (f) s[k] = coerce(f, v);
    }
  };

  const fromHash = () => {
    const h = new URLSearchParams(location.hash.replace(/^#/, ''));
    const o = {};
    for (const f of SCHEMA) {
      if (f.kind === 'arr') { const all = h.getAll(hashKey(f)); if (all.length) o[f.key] = all; }
      else if (h.has(hashKey(f))) o[f.key] = coerce(f, h.get(hashKey(f)));
    }
    const legacyRegion = h.get('region'); // pre-chips links used a single free-text region
    if (legacyRegion && !o.places) o.places = [legacyRegion];
    return o;
  };

  // Every hash write carries a monotonic sequence number in `history.state`. A `popstate` therefore
  // says not only WHAT the state is but WHERE among our own entries it sits, which is what lets the
  // modal decide whether ◀ / ▶ have anywhere to go.
  let seq = 0, high = 0;

  const toHash = push => {
    const h = new URLSearchParams();
    for (const f of SCHEMA) {
      const v = s[f.key];
      if (!isSet(f, v)) continue;
      if (f.kind === 'arr') v.forEach(x => h.append(hashKey(f), x));
      else h.set(hashKey(f), String(v));
    }
    const qs = h.toString();
    const url = qs ? '#' + qs : location.pathname + location.search;
    if (push) { seq = ++high; history.pushState({ histdbSeq: seq }, '', url); }
    else history.replaceState({ histdbSeq: seq }, '', url);
  };

  const notify = () => subs.forEach(f => f({ ...s }));

  return {
    /** Merge defaults (from /api/config), then whatever the URL hash carries — so reload restores. */
    init(defaults) {
      base = { ...(defaults || {}) };
      apply({ ...base, ...fromHash() });
      // Back / Forward: the hash IS the whole view, so re-reading it is the whole restore. Rebuilt
      // from the defaults up rather than merged, or a key the reader cleared would come back.
      window.addEventListener('popstate', e => {
        seq = (e.state && e.state.histdbSeq) || 0;
        s = empty();
        apply({ ...base, ...fromHash() });
        notify();
      });
      return this.get();
    },
    get: () => ({ ...s }),
    /** `opts.push` adds a browser history entry instead of rewriting the current one — used when the
      * change is something the reader would expect Back to undo (opening an article), not while
      * they are still dragging a slider. */
    set(patch, opts) {
      for (const [k, v] of Object.entries(patch || {})) {
        const f = SCHEMA.find(x => x.key === k);
        if (f) s[k] = coerce(f, v);
      }
      toHash(opts && opts.push); notify();
    },

    /** Where in OUR history this entry sits, and how far forward it goes. */
    seq: () => seq,
    maxSeq: () => high,
    /** Merge the filter a URL hash carries (`tag=steppe&topic=migration`) into the current state.
      *
      * ADDS to array fields instead of replacing them, because this is what a `/#tag=…` link inside
      * a rendered article means: "and show me this tag as well". Scalars still replace. Unknown keys
      * are ignored — a hash is user input.
      */
    addHash(hash) {
      const h = new URLSearchParams(String(hash || '').replace(/^#/, ''));
      const patch = {};
      for (const f of SCHEMA) {
        const key = hashKey(f);
        if (!h.has(key)) continue;
        if (f.kind === 'arr') {
          const have = Array.isArray(s[f.key]) ? s[f.key] : [];
          patch[f.key] = have.concat(h.getAll(key).filter(v => v !== '' && !have.includes(v)));
        } else patch[f.key] = h.get(key);
      }
      if (Object.keys(patch).length) this.set(patch);
      return patch;
    },

    /** Reset every filter-bar key; the time span and the viewport bbox are other controls' state. */
    clearFilters() {
      FILTERS.forEach(f => { s[f.key] = blank(f); });
      toHash(false); notify();
    },
    subscribe: f => subs.push(f),

    /** The exact REST params object (plan §1.0) for /api/sources, /api/findings and /api/facets.
      * `opts.except` = state key(s) to drop, so a chip's own field does not constrain its own
      * suggestion counts. Ranges are normalised (the two time sliders can cross). */
    toApiParams(opts) {
      const skip = new Set([].concat((opts && opts.except) || []));
      const p = {};
      for (const f of SCHEMA) {
        if (f.api === false || skip.has(f.key) || skip.has(f.param)) continue;
        const v = s[f.key];
        if (!isSet(f, v)) continue;
        p[f.param] = f.kind === 'arr'
          ? (Array.isArray(p[f.param]) ? p[f.param].concat(v) : v.slice())
          : v;
      }
      // viewport → bbox params (plan §1.1); `except: 'bbox'` drops it (used for the "M total" count)
      if (s.inView && s.bbox && !skip.has('bbox')) {
        const bb = String(s.bbox).split(',').map(Number);
        if (bb.length === 4 && bb.every(Number.isFinite)) { p.minLon = bb[0]; p.minLat = bb[1]; p.maxLon = bb[2]; p.maxLat = bb[3]; }
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
