// Sidebar results list + the cluster picker (plan §1.2).
window.Results = (() => {
  const $ = id => document.getElementById(id);
  let last = null; // args of the last render(), so the picker can go "back to results"

  const sourceCount = points => new Set(points.map(p => p.citekey)).size;

  function row(pt) {
    const d = document.createElement('div'); d.className = 'hit';
    d.innerHTML = `<div class="ck">${Fmt.esc(pt.citekey)}</div><div class="claim">${Fmt.esc(pt.claim.slice(0, 160))}</div>`
      + `<div class="meta">${Fmt.periodStr(pt.periodStart, pt.periodEnd)}</div>`;
    d.onclick = () => Article.open(pt.citekey);
    return d;
  }

  /** `opts` (optional): { total, inView } — `total` = findings ignoring the viewport filter. */
  function render(points, from, to, max, opts) {
    last = ['findings', points, from, to, max, opts];
    const o = opts || {};
    const list = $('list'); list.innerHTML = '';
    const span = `${Fmt.yr(from)} → ${Fmt.yr(to)}`;
    $('count').textContent = o.inView
      ? `${sourceCount(points)} sources · ${points.length} findings in view / ${o.total ?? points.length} total · ${span}`
      : `${sourceCount(points)} sources · ${points.length} findings · ${span}`;
    points.slice(0, max).forEach(pt => list.appendChild(row(pt)));
  }

  /** Cluster / coincident-dot picker: choose which of the co-located findings to open. */
  function pick(items, opts) {
    const list = $('list'); list.innerHTML = '';
    const head = document.createElement('div'); head.className = 'pick-head';
    head.innerHTML = `<a href="#" class="back">◀ back to results</a>`
      + `<div class="pick-title">${Fmt.esc((opts && opts.title) || '')}</div>`;
    head.querySelector('.back').onclick = e => {
      e.preventDefault();
      if (!last) list.innerHTML = '';
      else if (last[0] === 'sources') renderSources(last[1], last[2]);
      else render(...last.slice(1));
    };
    list.appendChild(head);
    [...items]
      .sort((x, y) => x.citekey.localeCompare(y.citekey) || (x.periodStart ?? 0) - (y.periodStart ?? 0))
      .forEach(pt => list.appendChild(row(pt)));
    $('list').scrollTop = 0;
    const aside = document.querySelector('aside'); if (aside) aside.scrollTop = 0;
  }

  /** Sidebar as SOURCES (plan §1.0: the source is the unit of result) — `/api/sources` response:
    * { total, hits: [{ source, matched: [{claim, period…, why}], why: [reason] }] }. */
  function renderSources(result, opts) {
    last = ['sources', result, opts];
    const list = $('list'); list.innerHTML = '';
    const hits = (result && result.hits) || [];
    const total = (result && result.total != null) ? result.total : hits.length;
    const o = opts || {};
    const s = State.get(); const span = `${Fmt.yr(s.from)} → ${Fmt.yr(s.to)}`;
    $('count').textContent = `${total} source${total === 1 ? '' : 's'}`
      + (o.findings != null ? (o.inView ? ` · ${o.findings} findings in view / ${o.total ?? o.findings} total` : ` · ${o.findings} findings`) : '')
      + ` · ${span}`;

    if (!hits.length) {
      const active = (window.State && State.activeChips) ? State.activeChips() : [];
      list.innerHTML = `<div class="empty"><p>no sources match — remove a chip</p>`
        + (active.length
            ? `<ul class="empty-chips">${active.map(c =>
                `<li><span class="k">${Fmt.esc(c.label)}</span> ${Fmt.esc(c.value)}</li>`).join('')}</ul>`
            : `<p class="muted">no filters are active — the corpus is empty for this time span.</p>`)
        + `</div>`;
      return;
    }

    for (const h of hits) {
      const src = h.source || {};
      const period = Fmt.periodStr(src.periodStart, src.periodEnd);
      const meta = [src.year, src.status, period].filter(x => x != null && x !== '').map(Fmt.esc).join(' · ');
      const d = document.createElement('div');
      d.className = 'hit hit-source';
      d.tabIndex = 0;
      d.innerHTML =
        `<div class="ck">${Fmt.esc(src.citekey)}</div>` +
        `<div class="title">${Fmt.esc(src.title || '')}</div>` +
        `<div class="meta">${meta}</div>` +
        ((h.why && h.why.length)
          ? `<ul class="why">${h.why.map(w => `<li>${Fmt.esc(w)}</li>`).join('')}</ul>` : '') +
        ((h.matched && h.matched.length)
          ? `<div class="matches"><span class="lbl">matches:</span><ul>` + h.matched.map(m =>
              `<li><span class="claim">${Fmt.esc(String(m.claim || '').slice(0, 160))}</span>` +
              (Fmt.periodStr(m.periodStart, m.periodEnd)
                ? ` <span class="p">${Fmt.esc(Fmt.periodStr(m.periodStart, m.periodEnd))}</span>` : '') +
              (m.regionName ? ` <span class="rg">${Fmt.esc(m.regionName)}</span>` : '') +
              ((m.why && m.why.length) ? ` <span class="mwhy">${Fmt.esc(m.why.join(', '))}</span>` : '') +
              `</li>`).join('') + `</ul></div>`
          : '');
      const open = () => Article.open(src.citekey);
      d.onclick = open;
      d.onkeydown = e => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); open(); } };
      list.appendChild(d);
    }
  }

  return { render, renderSources, pick };
})();
