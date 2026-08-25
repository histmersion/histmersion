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
  return { render };
})();
