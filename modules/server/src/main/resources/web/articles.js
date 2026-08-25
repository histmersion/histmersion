// The "articles" tab of the sidebar (plan §5.2 stream 2.7): search the COMPILED wiki by title or
// tag, click a hit to read it in the same modal the sources use.
//
// Deliberately small and self-contained: it owns the tab bar and one pane, reads `/api/articles`
// through Api, and opens `/article/{slug}` through Article.openSlug. It never touches the map, the
// filter State or the sources list — switching tabs only shows/hides panes. The one thing it does
// put in State is which tab is open and what this pane's own box says (`#tab=`, `#aq=`, item 25),
// because a link to "the articles tab, searching X" is a thing a reader wants to send.
window.ArticlesTab = (() => {
  const $ = id => document.getElementById(id);
  const tabs = $('tabs'), pane = $('articles-pane'), list = $('articles-list'),
        input = $('articles-q'), count = $('articles-count');
  let timer = null, loaded = false, page = null;

  const plural = (n, w) => `${n} ${w}${n === 1 ? '' : 's'}`;

  /** A teaser is prose, not source. The summaries are lifted from article bodies, so they arrive
    * carrying `[[wikilinks]]`, `**bold**`, backticks and heading marks; printed raw they read as
    * markup rather than as a sentence (item 23). */
  function plain(md) {
    return String(md == null ? '' : md)
      .replace(/!\[[^\]]*\]\([^)]*\)/g, '')                 // images
      .replace(/\[([^\]]*)\]\([^)]*\)/g, '$1')              // links → their text
      .replace(/\[\[([^\]|]+)\|([^\]]+)\]\]/g, '$2')        // [[x|label]] → label
      .replace(/\[\[([^\]]+)\]\]/g, '$1')                   // [[x]] → x
      .replace(/`([^`]*)`/g, '$1')
      .replace(/(\*\*|__|\*|_)(.*?)\1/g, '$2')
      .replace(/^\s{0,3}#{1,6}\s*/gm, '')
      .replace(/^\s{0,3}>\s?/gm, '')
      .replace(/\s+/g, ' ')
      .trim();
  }

  function clip(s, max) {
    const t = plain(s);
    if (t.length <= max) return t;
    const cut = t.slice(0, max), sp = cut.lastIndexOf(' ');
    return (sp > max * 0.6 ? cut.slice(0, sp) : cut).replace(/[\s,;:.—-]+$/, '') + '…';
  }

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
    d.setAttribute('role', 'button');
    d.tabIndex = 0;
    const meta = [a.kind, Fmt.periodStr(a.periodStart, a.periodEnd), a.tags]
      .filter(x => x != null && x !== '').map(Fmt.esc).join(' · ');
    d.innerHTML = `<div class="ck">${Fmt.esc(a.slug)}</div>`
      + `<div class="title">${Fmt.esc(a.title || '')}</div>`
      + `<div class="meta">${meta}</div>`
      + (a.summary ? `<div class="claim">${Fmt.esc(clip(a.summary, 200))}</div>` : '');
    // an article opens on the ARTICLE search term (this pane's own box), not the atlas filter's
    const open = () => Article.openSlug(a.slug, { q: (input && input.value.trim()) || '' });
    d.onclick = open;
    d.onkeydown = e => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); open(); } };
    return d;
  }

  function countLine() {
    if (!page) return '…';
    const { shown, total } = page;
    return total === 0 ? 'no articles'
      : shown < total ? `showing 1–${shown} of ${plural(total, 'article')}`
      : plural(total, 'article');
  }

  function renderMore(q) {
    const old = list.querySelector('.more'); if (old) old.remove();
    if (!page || page.shown >= page.total) return;
    const left = page.total - page.shown;
    const b = document.createElement('button');
    b.type = 'button'; b.className = 'more';
    b.textContent = `show ${Math.min(left, page.size)} more (${left} left)`;
    b.onclick = async () => {
      b.disabled = true; b.textContent = 'loading…';
      try {
        const r = await Api.articles({ ...(q ? { q } : {}), offset: page.shown });
        const items = (r && r.articles) || [];
        page.total = (r && r.total) || page.total;
        page.shown += items.length;
        b.remove();
        items.forEach(a => list.appendChild(row(a)));
        if (count) count.textContent = countLine();
        renderMore(q);
      } catch (e) { b.disabled = false; b.textContent = 'could not load more — try again'; }
    };
    list.appendChild(b);
  }

  async function load() {
    if (!list) return;
    const q = input ? input.value.trim() : '';
    if (count) { count.textContent = 'loading…'; count.classList.remove('is-error'); }
    list.setAttribute('aria-busy', 'true');
    try {
      const r = await Api.articles(q ? { q } : {});
      loaded = true;
      list.innerHTML = '';
      const items = (r && r.articles) || [];
      page = { shown: items.length, total: (r && r.total) || items.length, size: items.length || 20 };
      if (count) count.textContent = countLine();
      if (!items.length) list.innerHTML = `<div class="empty"><p>No articles match “${Fmt.esc(q)}”.</p></div>`;
      else { items.forEach(a => list.appendChild(row(a))); renderMore(q); }
    } catch (e) {
      if (count) { count.textContent = 'failed to load'; count.classList.add('is-error'); }
    } finally {
      list.setAttribute('aria-busy', 'false');
    }
  }

  if (tabs) {
    tabs.addEventListener('click', e => {
      const b = e.target.closest('.tab');
      if (!b) return;
      show(b.dataset.tab);
      if (window.State && State.set) State.set({ tab: b.dataset.tab });
    });
  }
  if (input) {
    // ONE debounce, from /api/config like every other one — no timing literal here; until it
    // answers, a keystroke simply searches immediately.
    let ms = null;
    Api.config().then(ui => { ms = (ui.debounce && ui.debounce.searchMs) || null; }).catch(() => {});
    input.addEventListener('input', () => {
      clearTimeout(timer);
      if (window.State && State.set) State.set({ aq: input.value.trim() });
      timer = setTimeout(load, ms || 0);
    });
  }

  /** Restore this pane from the hash (`#tab=articles&aq=…`), called once by app.js after State.init. */
  function restore() {
    if (!window.State || !State.get) return;
    const s = State.get();
    if (input && s.aq) { input.value = s.aq; loaded = false; }
    if (s.tab && s.tab !== 'sources') {
      const b = tabs && tabs.querySelector(`.tab[data-tab="${s.tab}"]`);
      if (b) b.click();
    }
  }

  return { show, load, restore };
})();
