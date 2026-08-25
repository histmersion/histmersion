// Article modal: opens /source/{ck}?fragment=true, keeps in-article /source links inside the modal.
window.Article = (() => {
  const $ = id => document.getElementById(id);
  const modal = $('modal'), content = $('modal-content'), newtab = $('modal-newtab');
  async function open(ck, full = false) {
    newtab.href = '/source/' + ck;
    content.innerHTML = '<p class="muted">loading…</p>';
    modal.hidden = false;
    try {
      content.innerHTML = await Api.articleHtml(ck, full);
      modal.querySelector('.modal').scrollTop = 0; modal.scrollTop = 0;
    } catch (e) { content.innerHTML = '<p>failed to load</p>'; }
  }
  function close() { modal.hidden = true; content.innerHTML = ''; }
  $('modal-close').onclick = close;
  modal.addEventListener('click', e => { if (e.target === modal) close(); });
  document.addEventListener('keydown', e => { if (e.key === 'Escape' && !modal.hidden) close(); });
  content.addEventListener('click', e => {
    const a = e.target.closest('a'); if (!a) return;
    const m = (a.getAttribute('href') || '').match(/^\/source\/([a-z0-9_]+)(\?full=true)?$/);
    if (m) { e.preventDefault(); open(m[1], !!m[2]); }
  });
  window.openArticle = open; // used by Leaflet popup links
  return { open, close };
})();
