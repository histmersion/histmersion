// Filter bar (plan §1.3/§1.5): full-text box, chip inputs for tags/authors/places/topics/status,
// publication-year range, sort, "clear all". Every control writes to State, which owns the URL hash;
// nothing here talks to the map or the results list.
window.Filters = (() => {
  // `key` is the State key, `field` the /api/facets field name (plan §1.0 — they match by design).
  const CHIP_FIELDS = [
    { key: 'tags',    field: 'tags',    label: 'tags',    placeholder: 'e.g. sea_peoples' },
    { key: 'authors', field: 'authors', label: 'authors', placeholder: 'e.g. Reich' },
    { key: 'places',  field: 'places',  label: 'places',  placeholder: 'e.g. Anatolia' },
    { key: 'topics',  field: 'topics',  label: 'topics',  placeholder: 'e.g. bronze_age_collapse' },
    { key: 'status',  field: 'status',  label: 'status',  placeholder: 'e.g. foundational' },
  ];
  const SORTS = ['relevance', 'cited', 'period', 'year', 'title'];

  const chips = {};
  let els = {};

  function init(container, ui) {
    const debounceMs = ui.debounce.searchMs;
    const s = State.get();

    container.innerHTML =
      `<div class="fbar">` +
      `<div class="f f-q"><label for="f-q">search</label>` +
      `<input type="search" id="f-q" placeholder="full text…" /></div>` +
      CHIP_FIELDS.map(c =>
        `<div class="f"><label>${Fmt.esc(c.label)}</label>` +
        `<div class="chips-host" id="f-chips-${c.key}"></div></div>`).join('') +
      `<div class="f f-gazetteer"><label for="f-place-search">gazetteer</label>` +
      `<div class="chips-host" id="f-gazetteer"></div></div>` +
      `<div class="f f-years"><label for="f-year-from">published</label>` +
      `<input type="number" id="f-year-from" class="year" placeholder="from" aria-label="published from" />` +
      `<span class="dash">–</span>` +
      `<input type="number" id="f-year-to" class="year" placeholder="to" aria-label="published to" /></div>` +
      `<div class="f"><label for="f-sort">sort</label><select id="f-sort">` +
      SORTS.map(v => `<option value="${v}">${v}</option>`).join('') + `</select></div>` +
      `<button type="button" class="f-clear" id="f-clear">clear all</button>` +
      `</div>`;

    els = {
      q: container.querySelector('#f-q'),
      yearFrom: container.querySelector('#f-year-from'),
      yearTo: container.querySelector('#f-year-to'),
      sort: container.querySelector('#f-sort'),
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

    // the gazetteer box (plan §1.6a) owns its own chips, suggestions and map overlay
    if (window.Places) Places.init(container.querySelector('#f-gazetteer'), ui);

    // Free-text query: Enter commits immediately, otherwise a debounce keeps typing responsive.
    let timer = null;
    const pushQ = () => { clearTimeout(timer); State.set({ q: els.q.value.trim() }); };
    els.q.addEventListener('input', () => { clearTimeout(timer); timer = setTimeout(pushQ, debounceMs); });
    els.q.addEventListener('keydown', e => { if (e.key === 'Enter') { e.preventDefault(); pushQ(); } });

    let yearTimer = null;
    const pushYears = () => {
      clearTimeout(yearTimer);
      State.set({ yearFrom: els.yearFrom.value, yearTo: els.yearTo.value });
    };
    for (const el of [els.yearFrom, els.yearTo]) {
      el.addEventListener('input', () => { clearTimeout(yearTimer); yearTimer = setTimeout(pushYears, debounceMs); });
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
