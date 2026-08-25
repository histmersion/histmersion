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
    last = [points, from, to, max, opts];
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
      if (last) render(...last); else list.innerHTML = '';
    };
    list.appendChild(head);
    [...items]
      .sort((x, y) => x.citekey.localeCompare(y.citekey) || (x.periodStart ?? 0) - (y.periodStart ?? 0))
      .forEach(pt => list.appendChild(row(pt)));
    $('list').scrollTop = 0;
    const aside = document.querySelector('aside'); if (aside) aside.scrollTop = 0;
  }

  return { render, pick };
})();
