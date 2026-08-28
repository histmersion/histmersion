// Filter bar (plan §1.3/§1.5): full-text box, chip inputs for tags/authors/topics/status,
// publication-year range, sort, "clear all". Every control writes to State, which owns the URL hash;
// nothing here talks to the map or the results list.
//
// Layout (item 16): row one is what a reader needs to start — search, place, span (the timeline owns
// the span) — and everything else lives behind "more filters ▾", so the header still fits a 768 px
// tall window instead of eating a third of it. Places are ONE box now (item 15): the gazetteer box
// offers a "text:" fallback row for a name that is not in the gazetteer, so there is no second
// free-text place input to confuse it with.
window.Filters = (() => {
  // `key` is the State key, `field` the /api/facets field name (plan §1.0 — they match by design).
  const CHIP_FIELDS = [
    { key: 'tags',    field: 'tags',    label: 'tags',    placeholder: 'e.g. sea peoples' },
    { key: 'authors', field: 'authors', label: 'authors', placeholder: 'e.g. Reich' },
    { key: 'topics',  field: 'topics',  label: 'topics',  placeholder: 'e.g. bronze_age_collapse' },
    { key: 'status',  field: 'status',  label: 'status',  placeholder: 'e.g. foundational' },
  ];
  const SORTS = ['relevance', 'cited', 'period', 'year', 'title'];
  const MORE_KEY = 'histdb.moreFilters';

  const chips = {};
  let els = {};

  function init(container, ui) {
    const s = State.get();

    container.innerHTML =
      `<div class="fbar">` +
      `<div class="frow frow-main">` +
        `<div class="f f-q"><label for="f-q">search</label>` +
        `<input type="search" id="f-q" placeholder="full text…" autofocus /></div>` +
        `<div class="f f-gazetteer"><label for="f-place-search">place</label>` +
        `<div class="chips-host" id="f-gazetteer"></div></div>` +
        `<button type="button" class="f-more" id="f-more-toggle" aria-expanded="false" aria-controls="f-more">more filters ▾</button>` +
        `<button type="button" class="f-clear" id="f-clear">clear all</button>` +
      `</div>` +
      `<div class="frow frow-more" id="f-more" hidden>` +
        CHIP_FIELDS.map(c =>
          `<div class="f"><label>${Fmt.esc(c.label)}</label>` +
          `<div class="chips-host" id="f-chips-${c.key}"></div></div>`).join('') +
        `<div class="f f-years"><label for="f-year-from">published</label>` +
        `<input type="number" id="f-year-from" class="year" placeholder="from" aria-label="published from" />` +
        `<span class="dash">–</span>` +
        `<input type="number" id="f-year-to" class="year" placeholder="to" aria-label="published to" /></div>` +
        `<div class="f"><label for="f-sort">sort</label><select id="f-sort">` +
        SORTS.map(v => `<option value="${v}">${v}</option>`).join('') + `</select></div>` +
      `</div>` +
      `</div>`;

    els = {
      q: container.querySelector('#f-q'),
      yearFrom: container.querySelector('#f-year-from'),
      yearTo: container.querySelector('#f-year-to'),
      sort: container.querySelector('#f-sort'),
      more: container.querySelector('#f-more'),
      moreBtn: container.querySelector('#f-more-toggle'),
    };

    for (const c of CHIP_FIELDS) {
      chips[c.key] = Chips.mount(container.querySelector(`#f-chips-${c.key}`), {
        field: c.field,
        label: c.label,
        placeholder: c.placeholder,
        values: s[c.key] || [],
        debounceMs: ui.debounce.suggestMs,
        // Counts must reflect the OTHER active filters, so this field is excluded from its own query.
        getSuggestions: prefix =>
          Api.facets(c.field, prefix, State.toApiParams({ except: c.key }))
             .then(rows => (rows || []).slice(0, ui.facetSuggestions)),
        onChange: values => State.set({ [c.key]: values }),
      });
    }

    // the one place box (plan §1.6a, item 15) owns its own chips, suggestions and map overlay
    if (window.Places) Places.init(container.querySelector('#f-gazetteer'), ui);

    // "more filters" stays open across reloads if the reader opened it — it is their choice about
    // how much header they want, not something to reset on every visit.
    const openMore = on => {
      els.more.hidden = !on;
      els.moreBtn.setAttribute('aria-expanded', on ? 'true' : 'false');
      els.moreBtn.textContent = on ? 'fewer filters ▴' : 'more filters ▾';
      try { localStorage.setItem(MORE_KEY, on ? '1' : '0'); } catch (e) { /* private window */ }
    };
    let wantMore = false;
    try { wantMore = localStorage.getItem(MORE_KEY) === '1'; } catch (e) { /* private window */ }
    // a filter that IS set must never be hidden behind a fold — the reader would not know it is on
    const someMoreSet = CHIP_FIELDS.some(c => (s[c.key] || []).length) || s.yearFrom != null || s.yearTo != null;
    openMore(wantMore || someMoreSet);
    els.moreBtn.addEventListener('click', () => openMore(els.more.hidden));

    // Free-text query: ONE debounce, and it lives in app.js's reload (item 10). Writing State on
    // every keystroke only rewrites the hash, which is free.
    const pushQ = () => State.set({ q: els.q.value.trim() });
    els.q.addEventListener('input', pushQ);
    els.q.addEventListener('keydown', e => { if (e.key === 'Enter') { e.preventDefault(); pushQ(); } });

    // Publication years: flag a value that is not a year rather than silently ignoring it (item 14).
    const hint = document.createElement('span');
    hint.className = 'f-hint muted';
    hint.id = 'f-year-hint';
    container.querySelector('.f-years').appendChild(hint);
    const pushYears = () => {
      const bad = [els.yearFrom, els.yearTo].filter(el => el.value.trim() !== '' && !/^-?\d{1,4}$/.test(el.value.trim()));
      [els.yearFrom, els.yearTo].forEach(el =>
        el.setAttribute('aria-invalid', bad.includes(el) ? 'true' : 'false'));
      hint.textContent = bad.length ? 'a publication year, e.g. 1998 (deep time goes in the span above)' : '';
      if (bad.length) return;
      State.set({ yearFrom: els.yearFrom.value, yearTo: els.yearTo.value });
    };
    for (const el of [els.yearFrom, els.yearTo]) {
      el.addEventListener('input', pushYears);
      el.addEventListener('change', pushYears);
      el.addEventListener('keydown', e => { if (e.key === 'Enter') { e.preventDefault(); pushYears(); } });
    }

    els.sort.addEventListener('change', () => State.set({ sort: els.sort.value }));

    container.querySelector('#f-clear').addEventListener('click', () => {
      State.clearFilters();
      sync();
      els.q.focus();
    });

    sync();
    els.q.focus();
  }

  /** Push State back into the controls (initial paint, and after "clear all"). */
  function sync() {
    const s = State.get();
    els.q.value = s.q || '';
    els.yearFrom.value = s.yearFrom == null ? '' : s.yearFrom;
    els.yearTo.value = s.yearTo == null ? '' : s.yearTo;
    els.sort.value = SORTS.includes(s.sort) ? s.sort : SORTS[0];
    for (const c of CHIP_FIELDS) chips[c.key].setValues(s[c.key] || []);
    if (window.Places) Places.sync();
  }

  return { init, sync };
})();
