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
  // The search term the reader is currently working with. It has to travel with EVERY open and every
  // in-modal hop (plan §1.4): a result you clicked BECAUSE it matched "collapse" is useless if it
  // opens at page 1 with no highlight and no "N matches · next ▼" control. Two sources, in order:
  // the live filter State (the atlas page), then the ?q= this document itself was opened with (a
  // standalone /source page, where there is no State at all).
  let lastQ = '';
  try { lastQ = new URLSearchParams(location.search).get('q') || ''; } catch (e) { /* no location */ }
  const currentQ = () => {
    if (window.State && State.get) { const q = State.get().q; if (q) return String(q); }
    return lastQ;
  };

  // opts: { full: bool, q: string }. A bare boolean is still accepted (legacy call sites).
  // `q` absent (not '') means "use the current one" — passing q:'' deliberately suppresses it.
  const norm = opts => {
    const o = (typeof opts === 'boolean' ? { full: opts } : (opts || {}));
    if (o.q == null) { const q = currentQ(); return q ? { ...o, q } : o; }
    return o;
  };

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

  // ── what is open, as URL state ────────────────────────────────────────────────────────────────
  // `State.open` is `source:<citekey>` / `article:<slug>`, so a reload, a shared link and the browser
  // Back button all agree with what the reader is looking at. `current` is the guard that keeps the
  // two in step without a loop: State drives the modal, the modal writes State, never both at once.
  const keyOf = (id, kind) => `${kind === 'article' ? 'article' : 'source'}:${id}`;
  const parseKey = k => {
    const m = /^(source|article):(.+)$/.exec(String(k || ''));
    return m ? { kind: m[1], id: m[2] } : null;
  };
  let current = null;      // the `State.open` value the modal is currently showing
  let opener = null;       // the element focus came from, to give it back on close (a11y, item 13)

  async function open(id, opts, kind, fromState) {
    const o = norm(opts);
    lastQ = o.q || '';                       // an in-modal hop from here inherits the same term
    if (!modal || !content) { location.href = url(id, o, null, kind); return; } // no modal: navigate
    const key = keyOf(id, kind);
    if (!fromState && window.State && State.set) {
      // one history entry per thing opened: Back goes to what you were reading before, and a
      // reload lands you back on this paper instead of on the bare atlas
      if (current !== key) { current = key; State.set({ open: key }, { push: true }); }
    } else current = key;

    if (modal.hidden) opener = document.activeElement;   // give focus back to this row on close
    if (newtab) newtab.href = url(id, o, null, kind) + (location.hash || '');
    content.innerHTML = '<p class="muted">loading…</p>';
    modal.hidden = false;
    syncNav();
    try {
      // fetched here rather than through Api.articleHtml so the ?q= highlight travels with it
      const res = await fetch(url(id, o, { fragment: 'true' }, kind));
      content.innerHTML = await res.text();
      const box = modal.querySelector('.modal');
      if (box) box.scrollTop = 0;
      modal.scrollTop = 0;
      setTitle();
      initMatches(content);
      focusModal();
    } catch (e) { content.innerHTML = '<p>failed to load</p>'; setTitle(); }
  }

  /** The modal's accessible name, taken from whatever heading the fragment brought with it. */
  function setTitle() {
    const el = $('modal-title');
    if (!el) return;
    const h = content.querySelector('h1, h2');
    el.textContent = h ? h.textContent.trim() : 'source';
  }

  /** ◀ / ▶ drive the BROWSER history, so the modal bar and the browser buttons cannot disagree.
    * `State.seq()` says where among our own pushed entries this one is; `State.maxSeq()` how far
    * forward they go. */
  function syncNav() {
    const back = $('modal-back'), fwd = $('modal-fwd');
    if (!back || !fwd || !window.State || !State.seq) return;
    back.disabled = State.seq() <= 1;
    fwd.disabled = State.seq() >= State.maxSeq();
  }

  function focusModal() {
    const box = modal.querySelector('.modal');
    if (box) box.focus();
  }

  function close(fromState) {
    if (!modal) return;
    modal.hidden = true; content.innerHTML = ''; active = null;
    const t = $('modal-title'); if (t) t.textContent = '';
    if (!fromState && current && window.State && State.set) State.set({ open: null }, { push: true });
    current = null;
    if (opener && opener.focus) { try { opener.focus(); } catch (e) { /* gone from the DOM */ } }
    opener = null;
  }

  // ── wiring ────────────────────────────────────────────────────────────────────────────────────
  if (modal && content) {
    const closeBtn = $('modal-close');
    if (closeBtn) closeBtn.onclick = () => close();
    const back = $('modal-back'), fwd = $('modal-fwd');
    if (back) back.onclick = () => history.back();
    if (fwd) fwd.onclick = () => history.forward();
    modal.addEventListener('click', e => { if (e.target === modal) close(); });

    // Focus trap (item 13): a dialog the reader can Tab out of, into a list they cannot see, is not
    // a dialog. Recomputed per keypress because the content is replaced on every hop.
    const FOCUSABLE = 'a[href], button:not([disabled]), input, select, textarea, [tabindex]:not([tabindex="-1"])';
    modal.addEventListener('keydown', e => {
      if (e.key !== 'Tab') return;
      const box = modal.querySelector('.modal');
      const items = Array.from(box.querySelectorAll(FOCUSABLE)).filter(el => el.offsetParent !== null);
      if (!items.length) { e.preventDefault(); box.focus(); return; }
      const first = items[0], last = items[items.length - 1];
      if (e.shiftKey && (document.activeElement === first || document.activeElement === box)) {
        e.preventDefault(); last.focus();
      } else if (!e.shiftKey && document.activeElement === last) {
        e.preventDefault(); first.focus();
      }
    });

    // State is the single source of truth for WHAT is open: the modal follows it, whether the change
    // came from a click here, from Back/Forward, or from the hash a shared link arrived with.
    if (window.State && State.subscribe) State.subscribe(st => {
      const want = st.open || null;
      syncNav();
      if (want === current) return;
      const parsed = parseKey(want);
      if (!parsed) { if (current) close(true); return; }
      open(parsed.id, undefined, parsed.kind, true);
    });
    content.addEventListener('click', e => {
      const a = e.target.closest('a'); if (!a) return;
      const raw = a.getAttribute('href') || '';
      // `/#tag=steppe` — a chip link rendered into the article's metadata line (Markdown.chipLinks).
      // Inside the modal it sets the chip in place; there is no reason to throw the reader back out
      // to a reloading atlas just to say "and this tag too".
      if (raw.startsWith('/#') || raw.startsWith('#')) {
        if (!window.State || !State.addHash) return;      // no atlas here: let the browser navigate
        e.preventDefault();
        State.addHash(raw.slice(raw.indexOf('#') + 1));
        close();
        return;
      }
      const m = raw.match(/^\/(source|article)\/([a-z0-9_.-]+)(?:\?(.*))?$/);
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

  /** Re-open whatever the URL hash says is open. Called once by app.js after `State.init`, since a
    * State that was merely INITIALISED has not notified anybody yet — this is what makes a reload
    * (and a pasted link) land back on the paper rather than on the bare atlas. */
  function restore() {
    if (!modal || !window.State || !State.get) return;
    const parsed = parseKey(State.get().open);
    if (parsed) open(parsed.id, undefined, parsed.kind, true);
  }

  window.openArticle = open; // used by Leaflet popup links
  /** Open a compiled wiki article by slug (the articles tab, and /article links in the text). */
  const openSlug = (slug, opts) => open(slug, opts, 'article');
  return { open, openSlug, close, restore, initMatches, nextMatch, openLightbox, closeLightbox };
})();
