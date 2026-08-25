// Article modal: opens /source/{ck}?fragment=true (and /article/{slug}?fragment=true for the compiled
// wiki, plan §5.2 stream 2.7), keeping in-article /source and /article links inside the modal.
// Also drives search-term highlighting (?q=…, server-rendered <mark id="hl…">): a "N matches · next ▼"
// control plus the `n` shortcut step through the marks, in the modal AND on the standalone page —
// this file is loaded by both (index.html and Article.page), so every DOM lookup is optional.
window.Article = (() => {
  const $ = id => document.getElementById(id);
  const modal = $('modal'), content = $('modal-content'), newtab = $('modal-newtab');

  // ── match navigation ──────────────────────────────────────────────────────────────────────────
  let active = null; // { marks: [el], nav: el, i: index of the current mark }

  function initMatches(root) {
    if (!root) return null;
    const marks = Array.from(root.querySelectorAll('mark[id^="hl"]'));
    const nav = root.querySelector('.match-nav');
    active = marks.length ? { marks, nav, i: -1 } : null;
    if (nav) nav.addEventListener('click', e => {
      const a = e.target.closest('a.next-match');
      if (a) { e.preventDefault(); nextMatch(); }
    });
    return active;
  }

  function nextMatch() {
    if (!active || !active.marks.length) return;
    const prev = active.marks[active.i];
    // inline outline as well as the class: style.css is owned elsewhere, so the current match has to
    // be visible without a stylesheet rule existing for .hl-current
    if (prev) { prev.classList.remove('hl-current'); prev.style.outline = ''; }
    active.i = (active.i + 1) % active.marks.length;
    const el = active.marks[active.i];
    el.classList.add('hl-current');
    el.style.outline = '2px solid currentColor';
    el.scrollIntoView({ block: 'center', behavior: 'smooth' });
  }

  // ── URLs ──────────────────────────────────────────────────────────────────────────────────────
  // opts: { full: bool, q: string }. A bare boolean is still accepted (legacy call sites).
  const norm = opts => (typeof opts === 'boolean' ? { full: opts } : (opts || {}));

  // Two things are rendered the same way and open in the same modal: a SOURCE (/source/{citekey})
  // and an ARTICLE (/article/{slug}) — `kind` picks which, and defaults to the source.
  const PATH = { source: '/source/', article: '/article/' };

  function url(id, opts, extra, kind) {
    const p = new URLSearchParams(extra || {});
    if (opts.full) p.set('full', 'true');
    if (opts.q) p.set('q', opts.q);
    const qs = p.toString();
    return (PATH[kind] || PATH.source) + encodeURIComponent(id) + (qs ? '?' + qs : '');
  }

  async function open(ck, opts, kind) {
    const o = norm(opts);
    if (!modal || !content) { location.href = url(ck, o, null, kind); return; } // no modal: navigate
    if (newtab) newtab.href = url(ck, o, null, kind);
    content.innerHTML = '<p class="muted">loading…</p>';
    modal.hidden = false;
    try {
      // fetched here rather than through Api.articleHtml so the ?q= highlight travels with it
      const res = await fetch(url(ck, o, { fragment: 'true' }, kind));
      content.innerHTML = await res.text();
      modal.querySelector('.modal').scrollTop = 0; modal.scrollTop = 0;
      initMatches(content);
    } catch (e) { content.innerHTML = '<p>failed to load</p>'; }
  }

  function close() {
    if (!modal) return;
    modal.hidden = true; content.innerHTML = ''; active = null;
  }

  // ── wiring ────────────────────────────────────────────────────────────────────────────────────
  if (modal && content) {
    const closeBtn = $('modal-close');
    if (closeBtn) closeBtn.onclick = close;
    modal.addEventListener('click', e => { if (e.target === modal) close(); });
    content.addEventListener('click', e => {
      const a = e.target.closest('a'); if (!a) return;
      const m = (a.getAttribute('href') || '').match(/^\/(source|article)\/([a-z0-9_.-]+)(?:\?(.*))?$/);
      if (!m) return;
      e.preventDefault();
      const p = new URLSearchParams(m[3] || '');
      open(m[2], { full: p.get('full') === 'true', q: p.get('q') || '' }, m[1]);
    });
  }

  document.addEventListener('keydown', e => {
    if (e.metaKey || e.ctrlKey || e.altKey) return;
    const tag = ((e.target && e.target.tagName) || '').toLowerCase();
    if (tag === 'input' || tag === 'textarea' || (e.target && e.target.isContentEditable)) return;
    if (e.key === 'Escape' && box) closeLightbox();
    else if (e.key === 'Escape' && modal && !modal.hidden) close();
    else if (e.key === 'n' && active) { e.preventDefault(); nextMatch(); }
  });

  // standalone article page (no modal): the marks are already in the document
  if (!modal) {
    if (document.readyState === 'loading')
      document.addEventListener('DOMContentLoaded', () => initMatches(document.body));
    else initMatches(document.body);
  }

  // ── figure lightbox (plan §1.8) ───────────────────────────────────────────────────────────────
  // Figures are served from /source/{ck}/assets/<name> and rendered small inside the article; a
  // click opens the full-size image over the page. Styling is inline on purpose: style.css belongs
  // to another stream, and this must work whether or not a rule for it exists there.
  let box = null;

  function closeLightbox() {
    if (box) { box.remove(); box = null; }
  }

  function openLightbox(src, alt) {
    closeLightbox();
    box = document.createElement('div');
    box.className = 'lightbox';
    box.setAttribute('role', 'dialog');
    box.setAttribute('aria-label', alt || 'figure');
    box.style.cssText = 'position:fixed;inset:0;z-index:9999;display:flex;align-items:center;' +
      'justify-content:center;background:rgba(0,0,0,.82);cursor:zoom-out;padding:2rem';
    const img = document.createElement('img');
    img.src = src;
    img.alt = alt || '';
    img.style.cssText = 'max-width:100%;max-height:100%;box-shadow:0 2px 24px rgba(0,0,0,.6)';
    box.appendChild(img);
    box.addEventListener('click', closeLightbox);
    document.body.appendChild(box);
  }

  // one delegated listener covers the modal AND the standalone page, now and after any re-render
  document.addEventListener('click', e => {
    const img = e.target.closest && e.target.closest('img');
    if (!img || !img.src || img.closest('.lightbox')) return;
    if (!/\/assets\//.test(img.getAttribute('src') || '')) return; // only article figures
    e.preventDefault();
    openLightbox(img.src, img.alt);
  });

  window.openArticle = open; // used by Leaflet popup links
  /** Open a compiled wiki article by slug (the articles tab, and /article links in the text). */
  const openSlug = (slug, opts) => open(slug, opts, 'article');
  return { open, openSlug, close, initMatches, nextMatch, openLightbox, closeLightbox };
})();
