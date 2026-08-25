// Sidebar results list + the cluster picker (plan §1.2).
//
// The list is the SOURCES that matched (plan §1.0), one page at a time with a "show N more" button
// underneath: `total` comes back with every response, so the count line can say "showing 1–20 of
// 198" instead of leaving the reader to guess whether 20 is the answer or the page size.
window.Results = (() => {
  const $ = id => document.getElementById(id);
  let last = null;      // args of the last render(), so the picker can go "back to results"
  let page = null;      // { params, hits, total, opts } — the paging cursor of the current list

  const sourceCount = points => new Set(points.map(p => p.citekey)).size;
  const plural = (n, word) => `${n} ${word}${n === 1 ? '' : 's'}`;

  /** Truncate on a WORD boundary and say so. A hard `.slice(160)` cuts mid-word and reads like an
    * OCR error, which in a corpus of OCR'd papers is exactly the wrong signal. */
  function clip(text, max) {
    const s = String(text == null ? '' : text).trim();
    if (s.length <= max) return s;
    const cut = s.slice(0, max);
    const sp = cut.lastIndexOf(' ');
    return (sp > max * 0.6 ? cut.slice(0, sp) : cut).replace(/[\s,;:.—-]+$/, '') + '…';
  }

  /** The FTS snippet arrives with `ts_headline`'s `**term**` markers. Render those as <mark> — the
    * point of a snippet is showing WHERE the query hit, and `**collapse**` as literal asterisks in
    * the middle of a sentence is noise rather than emphasis. */
  function snippetHtml(text) {
    return Fmt.esc(String(text).replace(/\s+/g, ' ').trim())
      .replace(/\*\*(.+?)\*\*/g, (_, t) => `<mark>${t}</mark>`);
  }

  // ── loading / error feedback (item 10) ────────────────────────────────────────────────────────
  /** Dim the list and announce it busy, rather than leaving a stale list under a frozen count. */
  function busy(on) {
    const list = $('list'), c = $('count');
    if (list) { list.setAttribute('aria-busy', on ? 'true' : 'false'); list.classList.toggle('is-busy', !!on); }
    if (c) { c.classList.toggle('is-loading', !!on); if (on) c.classList.remove('is-error'); }
  }

  /** A failed load says so IN the count line — the number above the list is where the reader looks
    * to find out what they are seeing, so that is where "this is not it" belongs. */
  function failed(msg) {
    const c = $('count');
    if (c) { c.classList.remove('is-loading'); c.classList.add('is-error'); c.textContent = msg || 'failed to load — retrying on the next change'; }
    busy(false);
  }

  function row(pt) {
    const d = document.createElement('div'); d.className = 'hit';
    d.setAttribute('role', 'button'); d.tabIndex = 0;
    d.innerHTML = `<div class="ck">${Fmt.esc(pt.citekey)}</div><div class="claim">${Fmt.esc(clip(pt.claim, 160))}</div>`
      + `<div class="meta">${Fmt.periodStr(pt.periodStart, pt.periodEnd)}</div>`;
    // the search term rides along, so the modal opens ON the passage that matched (plan §1.4)
    const open = () => Article.open(pt.citekey, { q: State.get().q });
    d.onclick = open;
    d.onkeydown = e => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); open(); } };
    return d;
  }

  /** `opts` (optional): { total, inView } — `total` = findings ignoring the viewport filter. */
  function render(points, from, to, max, opts) {
    last = ['findings', points, from, to, max, opts];
    page = null;
    const o = opts || {};
    const list = $('list'); list.innerHTML = '';
    const span = `${Fmt.yr(from)} → ${Fmt.yr(to)}`;
    $('count').textContent = o.inView
      ? `${plural(sourceCount(points), 'source')} · ${points.length} findings in view / ${o.total ?? points.length} total · ${span}`
      : `${plural(sourceCount(points), 'source')} · ${plural(points.length, 'finding')} · ${span}`;
    points.slice(0, max).forEach(pt => list.appendChild(row(pt)));
    busy(false);
  }

  /** Cluster / coincident-dot picker: choose which of the co-located findings to open (item 12).
    * Grouped BY SOURCE, because "23 findings here" is 23 rows of one paper repeating itself — what
    * the reader is choosing between is the papers. */
  function pick(items, opts) {
    const o = opts || {};
    const list = $('list'); list.innerHTML = '';
    const head = document.createElement('div'); head.className = 'pick-head';
    head.innerHTML = `<a href="#" class="back">◀ back to results</a>`
      + `<div class="pick-title">${Fmt.esc(o.title || '')}</div>`
      + (o.onZoom ? `<button type="button" class="pick-zoom">zoom to cluster</button>` : '');
    head.querySelector('.back').onclick = e => {
      e.preventDefault();
      if (!last) list.innerHTML = '';
      else if (last[0] === 'sources') renderSources(last[1], last[2]);
      else render(...last.slice(1));
    };
    const zoom = head.querySelector('.pick-zoom');
    if (zoom) zoom.onclick = () => o.onZoom();
    list.appendChild(head);

    const bySource = new Map();
    for (const pt of items) {
      if (!bySource.has(pt.citekey)) bySource.set(pt.citekey, { citekey: pt.citekey, title: pt.title, items: [] });
      bySource.get(pt.citekey).items.push(pt);
    }
    [...bySource.values()]
      .sort((a, b) => b.items.length - a.items.length || a.citekey.localeCompare(b.citekey))
      .forEach(g => {
        const d = document.createElement('div');
        d.className = 'hit hit-source';
        d.setAttribute('role', 'button'); d.tabIndex = 0;
        const span = Fmt.periodStr(
          Math.min(...g.items.map(x => x.periodStart ?? Infinity)),
          Math.max(...g.items.map(x => x.periodEnd ?? -Infinity)));
        d.innerHTML = `<div class="ck">${Fmt.esc(g.citekey)}</div>`
          + `<div class="title">${Fmt.esc(g.title || '')}</div>`
          + `<div class="meta">${plural(g.items.length, 'finding')}${span ? ' · ' + Fmt.esc(span) : ''}</div>`
          + `<div class="claim">${Fmt.esc(clip(g.items[0].claim, 140))}</div>`;
        const open = () => Article.open(g.citekey, { q: State.get().q });
        d.onclick = open;
        d.onkeydown = e => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); open(); } };
        list.appendChild(d);
      });
    $('list').scrollTop = 0;
    const aside = document.querySelector('aside'); if (aside) aside.scrollTop = 0;
  }

  /** The empty state names the REAL cause (item 19), and every chip in it can be taken off here —
    * "remove a chip" is useless advice when the chips are in a bar the reader has scrolled past. */
  function emptyHtml(s) {
    const active = (window.State && State.activeChips) ? State.activeChips() : [];
    const causes = [];
    if (s.inView && s.bbox) causes.push('the map viewport ("only sources in view" is on)');
    causes.push(`the span ${Fmt.yr(s.from)} → ${Fmt.yr(s.to)}`);
    if (active.length) causes.push(`${plural(active.length, 'filter')} below`);
    return `<div class="empty"><p>No sources match ${causes.join(', ')}.</p>`
      + (active.length
          ? `<ul class="empty-chips">${active.map((c, i) =>
              `<li><span class="k">${Fmt.esc(c.label)}</span> ${Fmt.esc(c.value)}` +
              `<button type="button" class="chip-x" data-i="${i}" aria-label="remove ${Fmt.esc(c.label)} ${Fmt.esc(c.value)}">✕</button></li>`).join('')}</ul>`
          : `<p class="muted">No filters are active — widen the span, or pan the map.</p>`)
      + `</div>`;
  }

  /** Sidebar as SOURCES (plan §1.0: the source is the unit of result) — `/api/sources` response:
    * { total, hits: [{ source, matched: [{claim, period…, why}], why: [reason] }] }.
    * `opts.params` is the query that produced it, which is what "show N more" re-issues at an
    * offset; without it the list simply has no more button. */
  function renderSources(result, opts) {
    last = ['sources', result, opts];
    const o = opts || {};
    const hits = (result && result.hits) || [];
    page = { params: o.params || null, hits: hits.slice(), total: (result && result.total != null) ? result.total : hits.length, opts: o };
    const list = $('list'); list.innerHTML = '';
    busy(false);
    if (!hits.length) { $('count').textContent = countLine(); list.innerHTML = emptyHtml(State.get()); wireEmpty(list); return; }
    hits.forEach(h => list.appendChild(hitRow(h)));
    $('count').textContent = countLine();
    renderMore();
  }

  function wireEmpty(list) {
    const chips = (window.State && State.activeChips) ? State.activeChips() : [];
    list.querySelectorAll('.empty-chips .chip-x').forEach(b => {
      b.onclick = () => { const c = chips[+b.dataset.i]; if (c && State.removeChip) State.removeChip(c.key, c.value); };
    });
  }

  /** "showing 1–20 of 198 · N findings · span" — the same phrasing as the Markdown paging footer. */
  function countLine() {
    const o = (page && page.opts) || {};
    const shown = page ? page.hits.length : 0;
    const total = page ? page.total : 0;
    const s = State.get();
    const span = `${Fmt.yr(s.from)} → ${Fmt.yr(s.to)}`;
    const head = total === 0 ? 'no sources'
      : shown < total ? `showing 1–${shown} of ${plural(total, 'source')}`
      : plural(total, 'source');
    return head
      + (o.findings != null ? (o.inView ? ` · ${o.findings} findings in view / ${o.total ?? o.findings} total` : ` · ${plural(o.findings, 'finding')}`) : '')
      + ` · ${span}`;
  }

  function renderMore() {
    const list = $('list');
    const old = list.querySelector('.more'); if (old) old.remove();
    if (!page || !page.params || page.hits.length >= page.total) return;
    const left = page.total - page.hits.length;
    const size = Math.min(left, (page.params.limit || page.hits.length) || 20);
    const b = document.createElement('button');
    b.type = 'button'; b.className = 'more';
    b.textContent = `show ${size} more (${left} left)`;
    b.onclick = async () => {
      b.disabled = true; b.textContent = 'loading…';
      try {
        const r = await Api.sources({ ...page.params, offset: page.hits.length });
        const more = (r && r.hits) || [];
        page.total = (r && r.total != null) ? r.total : page.total;
        page.hits = page.hits.concat(more);
        b.remove();
        more.forEach(h => list.appendChild(hitRow(h)));
        $('count').textContent = countLine();
        renderMore();
      } catch (e) { b.disabled = false; b.textContent = 'could not load more — try again'; }
    };
    list.appendChild(b);
  }

  /** A match's reasons, minus the one already printed beside it. "…2500 BCE–2200 BCE period" reads
    * as if "period" were part of the date; the period IS the reason, shown as the date (item 17). */
  const mwhy = m => (m.why || []).filter(w => !/^period\b/i.test(String(w)));

  function hitRow(h) {
    const src = h.source || {};
    const period = Fmt.periodStr(src.periodStart, src.periodEnd);
    const meta = [src.year, src.status, period].filter(x => x != null && x !== '').map(Fmt.esc).join(' · ');
    // the period is already ON the row: repeating it as a "why" bullet says nothing (item 17)
    const why = (h.why || []).filter(w => !/^period\b/i.test(String(w)));
    const matched = h.matched || [];
    const shownMatches = matched.slice(0, 2);
    const d = document.createElement('div');
    d.className = 'hit hit-source';
    d.setAttribute('role', 'button');
    d.tabIndex = 0;
    d.innerHTML =
      `<div class="ck">${Fmt.esc(src.citekey)}</div>` +
      `<div class="title">${Fmt.esc(src.title || '')}</div>` +
      `<div class="meta">${meta}</div>` +
      (src.snippet ? `<div class="snip">${snippetHtml(src.snippet)}</div>` : '') +
      (why.length ? `<ul class="why">${why.map(w => `<li>${Fmt.esc(w)}</li>`).join('')}</ul>` : '') +
      (matched.length
        ? `<div class="matches"><span class="lbl">matches:</span><ul>` + shownMatches.map(m =>
            `<li><span class="claim">${Fmt.esc(clip(m.claim, 160))}</span>` +
            (Fmt.periodStr(m.periodStart, m.periodEnd)
              ? ` <span class="p">${Fmt.esc(Fmt.periodStr(m.periodStart, m.periodEnd))}</span>` : '') +
            (m.region ? ` <span class="rg">${Fmt.esc(m.region)}</span>` : '') +
            (mwhy(m).length ? ` <span class="mwhy">${Fmt.esc(mwhy(m).join(', '))}</span>` : '') +
            `</li>`).join('')
          + (matched.length > shownMatches.length
              ? `<li class="more-matches">+${matched.length - shownMatches.length} more</li>` : '')
          + `</ul></div>`
        : '');
    const open = () => Article.open(src.citekey, { q: State.get().q });
    d.onclick = open;
    d.onkeydown = e => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); open(); } };
    return d;
  }

  // ↑/↓ walk the result rows (item 13) — a keyboard reader should not have to Tab past every link
  // inside a hit to reach the next one.
  const listEl = $('list');
  if (listEl) listEl.addEventListener('keydown', e => {
    if (e.key !== 'ArrowDown' && e.key !== 'ArrowUp') return;
    const rows = Array.from(listEl.querySelectorAll('.hit'));
    const i = rows.indexOf(e.target.closest('.hit'));
    if (i < 0) return;
    e.preventDefault();
    const next = rows[i + (e.key === 'ArrowDown' ? 1 : -1)];
    if (next) next.focus();
  });

  return { render, renderSources, pick, busy, failed };
})();
