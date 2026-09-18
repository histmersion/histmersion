// Wikipedia-style citation footnotes for compiled articles. Runs after article HTML loads in both
// the atlas modal and the standalone /article page. Each unique source gets [N] in order of first
// appearance; the number scrolls to the "Sources" footer; hovering shows a tooltip with the source
// info extracted from that same footer.  Source links are restyled with human-readable labels
// ("Title, Author, Year") and a distinct colour; article links get their own class and title too.
window.Citations = (() => {
  function process(root) {
    if (!root) return;

    const footerStart = findFooterStart(root);
    const sourcesList = findSourcesList(root);

    // ── source citations ─────────────────────────────────────────────────────────────────────
    if (sourcesList) processSourceCitations(root, footerStart, sourcesList);

    // ── article links: replace slugs with titles ─────────────────────────────────────────────
    processArticleLinks(root);
  }

  function processSourceCitations(root, footerStart, sourcesList) {
    const citeMap = new Map();

    // Parse each <li> in the Sources list.
    for (const li of sourcesList.querySelectorAll('li')) {
      const a = li.querySelector('a[href^="/source/"]');
      if (!a) continue;
      const ck = ckFrom(a.getAttribute('href'));
      if (!ck) continue;

      // Full text of the li (for tooltip), minus the citekey prefix.
      const fullText = liTextWithoutCk(li, ck);
      // Short label: "Author Year · Title" — first part before description.
      const label = buildLabel(fullText);

      citeMap.set(ck, { n: 0, occ: 0, label, fullText, anchor: null, firstRef: null });
    }

    // Number body links in first-appearance order.
    const bodyLinks = [];
    let num = 0;
    for (const a of root.querySelectorAll('a[href^="/source/"]')) {
      if (footerStart && footerStart.compareDocumentPosition(a) & Node.DOCUMENT_POSITION_FOLLOWING) continue;
      bodyLinks.push(a);
      const ck = ckFrom(a.getAttribute('href'));
      if (ck && citeMap.has(ck) && citeMap.get(ck).n === 0) citeMap.get(ck).n = ++num;
    }
    if (!num) return;

    // Annotate footer: add anchors.
    for (const [ck, entry] of citeMap) {
      if (!entry.n) continue;
      const li = findLiForCk(sourcesList, ck);
      if (!li) continue;
      li.id = 'cite-' + entry.n;
      entry.anchor = li.id;
    }

    // Process body links: replace text, add [N], add tooltip.
    for (const a of bodyLinks) {
      const ck = ckFrom(a.getAttribute('href'));
      if (!ck) continue;
      const entry = citeMap.get(ck);
      if (!entry || !entry.anchor) continue;
      if (a.nextElementSibling && a.nextElementSibling.classList.contains('cite-num')) continue;

      entry.occ++;
      const refId = 'cite-ref-' + entry.n + '-' + entry.occ;
      a.id = refId;
      if (!entry.firstRef) entry.firstRef = refId;

      // Replace citekey with human-readable label.
      if (entry.label) a.textContent = entry.label;
      a.classList.add('cite-source');

      // [N] superscript → jumps to footer.
      const sup = document.createElement('sup');
      sup.className = 'cite-num';
      const link = document.createElement('a');
      link.href = '#' + entry.anchor;
      link.textContent = '[' + entry.n + ']';
      link.className = 'cite-jump';
      link.addEventListener('click', jumpTo(root, entry.anchor));
      sup.appendChild(link);
      a.after(sup);

      // Rich tooltip on hover — fetch full source metadata on first hover.
      a.dataset.ck = ck;
      a.dataset.citeFallback = entry.fullText || '';
      a.addEventListener('mouseenter', showSourceTip);
      a.addEventListener('mouseleave', hideTip);
    }

    // Footer back-links.
    for (const [, entry] of citeMap) {
      if (!entry.anchor || !entry.firstRef) continue;
      const li = root.querySelector('#' + entry.anchor);
      if (!li) continue;
      const back = document.createElement('a');
      back.href = '#' + entry.firstRef;
      back.className = 'cite-back';
      back.textContent = '↑';
      back.title = 'Jump back to citation';
      back.addEventListener('click', jumpTo(root, entry.firstRef));
      li.insertBefore(back, li.firstChild);
    }
  }

  // ── article links ──────────────────────────────────────────────────────────────────────────

  function processArticleLinks(root) {
    const links = root.querySelectorAll('a[href^="/article/"]');
    if (!links.length) return;

    // Collect unique slugs.
    const slugSet = new Set();
    for (const a of links) {
      const slug = slugFrom(a.getAttribute('href'));
      if (slug && slug !== 'index') slugSet.add(slug);
    }
    if (!slugSet.size) return;

    // Fetch article titles in one batch.
    fetchArticleTitles([...slugSet]).then(titles => {
      for (const a of links) {
        const slug = slugFrom(a.getAttribute('href'));
        if (!slug || !titles[slug]) continue;
        const info = titles[slug];
        // Only replace if the link text IS the slug (not a custom label).
        if (a.textContent.trim() === slug) {
          a.textContent = info.title;
        }
        a.classList.add('cite-article');
        // Tooltip on hover for articles.
        a.dataset.articleInfo = info.summary || info.title;
        a.addEventListener('mouseenter', showArticleTip);
        a.addEventListener('mouseleave', hideTip);
      }
    });
  }

  async function fetchArticleTitles(slugs) {
    const result = {};
    // Fetch articles list filtered by slug — use the existing API.
    try {
      const res = await fetch('/api/articles?limit=200');
      if (!res.ok) return result;
      const data = await res.json();
      for (const a of (data.articles || [])) {
        if (slugs.includes(a.slug)) {
          result[a.slug] = { title: a.title, summary: a.summary || '' };
        }
      }
    } catch (e) { /* ignore */ }
    return result;
  }

  // ── helpers ────────────────────────────────────────────────────────────────────────────────────

  function ckFrom(href) {
    if (!href) return null;
    const m = href.match(/^\/source\/([a-z0-9_.-]+)/);
    return m ? decodeURIComponent(m[1]) : null;
  }

  function slugFrom(href) {
    if (!href) return null;
    const m = href.match(/^\/article\/([a-z0-9_.-]+)/);
    return m ? decodeURIComponent(m[1]) : null;
  }

  function findFooterStart(root) {
    const pat = /^(sources|backlinks|not used)/i;
    for (const h of root.querySelectorAll('h2')) {
      if (pat.test(h.textContent.trim())) return h;
    }
    return null;
  }

  function findSourcesList(root) {
    for (const h of root.querySelectorAll('h2')) {
      if (/^sources/i.test(h.textContent.trim())) {
        let el = h.nextElementSibling;
        while (el && el.tagName !== 'UL' && el.tagName !== 'H2') el = el.nextElementSibling;
        return el && el.tagName === 'UL' ? el : null;
      }
    }
    return null;
  }

  function findLiForCk(ul, ck) {
    for (const li of ul.querySelectorAll('li')) {
      const a = li.querySelector('a[href^="/source/"]');
      if (a && ckFrom(a.getAttribute('href')) === ck) return li;
    }
    return null;
  }

  /** Extract li text without the citekey prefix — the useful metadata. */
  function liTextWithoutCk(li, ck) {
    let raw = '';
    for (const n of li.childNodes) raw += n.textContent;
    return raw.replace(new RegExp('^' + ck.replace(/[.*+?^${}()|[\]\\]/g, '\\$&') + '\\s*—\\s*', 'i'), '').trim();
  }

  /** Short label from footer text: "Author Year · Title" portion, truncated. */
  function buildLabel(footerText) {
    // Footer format: "Author Year · Title · status"
    // Split on " · " and take first two parts (author+year, title).
    const parts = footerText.split(' · ');
    if (parts.length >= 2) {
      const authYr = parts[0].trim();
      let title = parts[1].trim();
      if (title.length > 50) title = title.substring(0, 47) + '…';
      return authYr + ', ' + title;
    }
    // Fallback: truncate the whole thing.
    const main = footerText.length > 70 ? footerText.substring(0, 67) + '…' : footerText;
    return main || null;
  }

  function jumpTo(root, anchorId) {
    return e => {
      e.preventDefault();
      e.stopPropagation();
      const target = root.querySelector('#' + anchorId);
      if (target) target.scrollIntoView({ behavior: 'smooth', block: 'center' });
    };
  }

  // ── tooltips ───────────────────────────────────────────────────────────────────────────────────

  let tip = null;
  const sourceCache = {};

  function showSourceTip(e) {
    hideTip();
    const a = e.currentTarget;
    const ck = a.dataset.ck;
    if (!ck) return;

    // Show fallback immediately, then upgrade with API data.
    const fallback = a.dataset.citeFallback || '';
    renderTip(a, formatSourceFallback(fallback));

    // Fetch full source metadata if not cached.
    if (sourceCache[ck]) {
      renderTip(a, formatSourceFull(sourceCache[ck]));
    } else {
      fetch('/api/sources/' + encodeURIComponent(ck))
        .then(r => r.ok ? r.json() : null)
        .then(data => {
          if (!data) return;
          sourceCache[ck] = data;
          // Only update if the tooltip is still showing for this element.
          if (tip && a.matches(':hover')) renderTip(a, formatSourceFull(data));
        })
        .catch(() => {});
    }
  }

  function showArticleTip(e) {
    hideTip();
    const info = e.currentTarget.dataset.articleInfo;
    if (!info) return;
    renderTip(e.currentTarget,
      '<div class="cite-tip-desc">' + esc(info) + '</div>');
  }

  function renderTip(anchor, html) {
    if (tip) tip.remove();
    tip = document.createElement('div');
    tip.className = 'cite-tooltip';
    tip.innerHTML = html;
    document.body.appendChild(tip);

    const rect = anchor.getBoundingClientRect();
    const maxW = Math.min(440, window.innerWidth - 20);
    tip.style.maxWidth = maxW + 'px';

    let left = rect.left + rect.width / 2 - maxW / 2;
    left = Math.max(10, Math.min(left, window.innerWidth - maxW - 10));
    tip.style.left = left + 'px';
    tip.style.top = (rect.bottom + 6) + 'px';

    requestAnimationFrame(() => {
      if (!tip) return;
      const tr = tip.getBoundingClientRect();
      if (tr.bottom > window.innerHeight - 10)
        tip.style.top = (rect.top - tr.height - 6) + 'px';
    });
  }

  /** Tooltip from footer text (immediate, before API responds). */
  function formatSourceFallback(text) {
    if (!text) return '';
    const parts = text.split(' · ');
    let html = '<div class="cite-tip-meta">' + esc(parts[0] || '') + '</div>';
    if (parts[1]) html += '<div class="cite-tip-title">' + esc(parts[1]) + '</div>';
    if (parts[2]) html += '<div class="cite-tip-status ' + esc(parts[2].toLowerCase()) + '">' + esc(parts[2]) + '</div>';
    return html;
  }

  /** Normalise a field that may be a string, an array, or absent into a list. */
  function toList(v) {
    if (!v) return [];
    if (Array.isArray(v)) return v.filter(Boolean);
    return String(v).split(/,\s*/).map(s => s.trim()).filter(Boolean);
  }

  /** Rich tooltip from full API source data. */
  function formatSourceFull(s) {
    const src = s.source || s;
    let html = '';

    // Title
    const title = src.title || '';
    html += '<div class="cite-tip-title">' + esc(title) + '</div>';

    // Authors · Year · Venue
    const meta = [
      toList(src.authors).join(', '),
      src.year ? String(src.year) : '',
      src.venue || ''
    ].filter(Boolean).join(' · ');
    if (meta) html += '<div class="cite-tip-meta">' + esc(meta) + '</div>';

    // Topics + Tags
    const chips = [...toList(src.topics), ...toList(src.tags)];
    if (chips.length) {
      html += '<div class="cite-tip-chips">' +
        chips.map(c => '<span class="cite-chip">' + esc(c.replace(/_/g, ' ')) + '</span>').join(' ') +
        '</div>';
    }

    // Regions · Period
    const geo = [
      toList(src.regions).join(', '),
      src.periodStart != null && src.periodEnd != null
        ? periodStr(src.periodStart, src.periodEnd)
        : ''
    ].filter(Boolean).join(' · ');
    if (geo) html += '<div class="cite-tip-geo">' + esc(geo) + '</div>';

    // Status
    if (src.status) {
      html += '<div class="cite-tip-status ' + esc(src.status) + '">' + esc(src.status) + '</div>';
    }

    // Summary
    const summary = src.summary || '';
    if (summary) {
      const short = summary.length > 300 ? summary.substring(0, 297) + '…' : summary;
      html += '<div class="cite-tip-summary">' + esc(short) + '</div>';
    }

    return html;
  }

  function periodStr(a, b) {
    const yr = y => y < 0 ? (-y) + ' BCE' : y + ' CE';
    return a === b ? yr(a) : yr(a) + '–' + yr(b);
  }

  function esc(s) {
    return String(s || '').replace(/[&<>"']/g, c =>
      ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  }

  function hideTip() {
    if (tip) { tip.remove(); tip = null; }
  }

  return { process };
})();
