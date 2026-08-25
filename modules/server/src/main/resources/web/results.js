// Sidebar results list.
window.Results = (() => {
  const $ = id => document.getElementById(id);
  function render(points, from, to, max) {
    const list = $('list'); list.innerHTML = '';
    $('count').textContent = `${points.length} finding(s) · ${Fmt.yr(from)} → ${Fmt.yr(to)}`;
    points.slice(0, max).forEach(pt => {
      const d = document.createElement('div'); d.className = 'hit';
      d.innerHTML = `<div class="ck">${Fmt.esc(pt.citekey)}</div><div class="claim">${Fmt.esc(pt.claim.slice(0, 160))}</div>`
        + `<div class="meta">${Fmt.periodStr(pt.periodStart, pt.periodEnd)}</div>`;
      d.onclick = () => Article.open(pt.citekey);
      list.appendChild(d);
    });
  }
  /** Sidebar as SOURCES (plan §1.0: the source is the unit of result) — `/api/sources` response:
    * { total, hits: [{ source, matched: [{claim, period…, why}], why: [reason] }] }. */
  function renderSources(result) {
    const list = $('list'); list.innerHTML = '';
    const hits = (result && result.hits) || [];
    const total = (result && result.total != null) ? result.total : hits.length;
    $('count').textContent = `${total} source${total === 1 ? '' : 's'}`;

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
              (m.region ? ` <span class="rg">${Fmt.esc(m.region)}</span>` : '') +
              ((m.why && m.why.length) ? ` <span class="mwhy">${Fmt.esc(m.why.join(', '))}</span>` : '') +
              `</li>`).join('') + `</ul></div>`
          : '');
      const open = () => Article.open(src.citekey);
      d.onclick = open;
      d.onkeydown = e => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); open(); } };
      list.appendChild(d);
    }
  }

  return { render, renderSources };
})();
