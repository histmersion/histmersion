// Thin fetch wrappers over the REST contract (see core/Api.scala). Nothing else talks to the server.
window.Api = (() => {
  const get = async (path, params) => {
    const qs = params ? '?' + new URLSearchParams(Object.entries(params).filter(([, v]) => v != null && v !== '')) : '';
    const res = await fetch(path + qs);
    if (!res.ok) throw new Error(`${path}: ${res.status}`);
    return res.json();
  };
  return {
    config: () => get('/api/config'),
    findings: p => get('/api/findings', p),
    search: p => get('/api/search', p),
    aggregate: p => get('/api/aggregate', p),
    articleHtml: async (ck, full) => (await fetch(`/source/${ck}?fragment=true${full ? '&full=true' : ''}`)).text(),
  };
})();
