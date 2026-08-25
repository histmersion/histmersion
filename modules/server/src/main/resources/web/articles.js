// The "articles" tab of the sidebar (plan §5.2 stream 2.7): search the COMPILED wiki by title or
// tag, click a hit to read it in the same modal the sources use.
//
// Deliberately small and self-contained: it owns the tab bar and one pane, reads `/api/articles`
// through Api, and opens `/article/{slug}` through Article.openSlug. It never touches the map, the
// filter State or the sources list — switching tabs only shows/hides panes.
window.ArticlesTab = (() => {
  const $ = id => document.getElementById(id);
  const tabs = $('tabs'), pane = $('articles-pane'), list = $('articles-list'),
        input = $('articles-q'), count = $('articles-count');
  let timer = null, loaded = false;

  function show(tab) {
    if (!tabs) return;
    const articles = tab === 'articles';
    tabs.querySelectorAll('.tab').forEach(b => b.classList.toggle('active', b.dataset.tab === tab));
    if (pane) pane.hidden = !articles;
    ['count', 'list'].forEach(id => { const el = $(id); if (el) el.hidden = articles; });
    if (articles && !loaded) load();
    if (articles && input) input.focus();
  }

  function row(a) {
    const d = document.createElement('div');
    d.className = 'hit hit-article';
    d.tabIndex = 0;
    const meta = [a.kind, Fmt.periodStr(a.periodStart, a.periodEnd), a.tags]
      .filter(x => x != null && x !== '').map(Fmt.esc).join(' · ');
    d.innerHTML = `<div class="ck">${Fmt.esc(a.slug)}</div>`
      + `<div class="title">${Fmt.esc(a.title || '')}</div>`
      + `<div class="meta">${meta}</div>`
      + (a.summary ? `<div class="claim">${Fmt.esc(String(a.summary).slice(0, 200))}</div>` : '');
    const open = () => Article.openSlug(a.slug);
    d.onclick = open;
    d.onkeydown = e => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); open(); } };
    return d;
  }

  async function load() {
    if (!list) return;
    const q = input ? input.value.trim() : '';
    if (count) count.textContent = 'loading…';
    try {
      const r = await Api.articles(q ? { q } : {});
      loaded = true;
      list.innerHTML = '';
      const items = (r && r.articles) || [];
      if (count) count.textContent = `${(r && r.total) || 0} article${((r && r.total) === 1) ? '' : 's'}`;
      if (!items.length) list.innerHTML = `<div class="empty"><p>no articles match</p></div>`;
      else items.forEach(a => list.appendChild(row(a)));
    } catch (e) {
      if (count) count.textContent = 'failed to load';
    }
  }

  if (tabs) {
    tabs.addEventListener('click', e => {
      const b = e.target.closest('.tab');
      if (b) show(b.dataset.tab);
    });
  }
  if (input) {
    // the debounce budget comes from /api/config like every other one — no timing literal here;
    // until it answers, a keystroke simply searches immediately.
    let ms = null;
    Api.config().then(ui => { ms = (ui.debounce && ui.debounce.searchMs) || null; }).catch(() => {});
    input.addEventListener('input', () => { clearTimeout(timer); timer = setTimeout(load, ms || 0); });
  }

  return { show, load };
})();
