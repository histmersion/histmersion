// Thin fetch wrappers over the REST contract (see core/Api.scala). Nothing else talks to the server.
window.Api = (() => {
  // Array values become REPEATED params (tag=a&tag=b), matching the Filter contract (plan §1.0).
  const qs = params => {
    const u = new URLSearchParams();
    for (const [k, v] of Object.entries(params || {})) {
      if (v == null || v === '') continue;
      if (Array.isArray(v)) v.forEach(x => { if (x != null && x !== '') u.append(k, x); });
      else u.append(k, v);
    }
    const s = u.toString();
    return s ? '?' + s : '';
  };
  const get = async (path, params) => {
    const res = await fetch(path + qs(params));
    if (!res.ok) throw new Error(`${path}: ${res.status}`);
    return res.json();
  };
  return {
    config: () => get('/api/config'),
    findings: p => get('/api/findings', p),
    /** { total, hits: [{ source, matched: [...], why: [...] }] } — the source is the unit of result. */
    sources: p => get('/api/sources', p),
    /** [{ value, count }] for one facet field, counted within the rest of the current filter. */
    facets: (field, prefix, p) => get('/api/facets', { ...(p || {}), field, prefix }),
    search: p => get('/api/search', p),
    /** Gazetteer (plan §1.6a): [{id,label,kind,findings,contested,…}] by name, valid at `asOf`. */
    places: (q, asOf, limit) => get('/api/places', { q, asOf, limit }),
    /** One place with its GeoJSON geometry (as TEXT) and its hierarchy as of `asOf`. */
    place: (id, asOf) => get('/api/places/' + encodeURIComponent(id), { asOf }),
    aggregate: p => get('/api/aggregate', p),
    articleHtml: async (ck, full) => (await fetch(`/source/${ck}?fragment=true${full ? '&full=true' : ''}`)).text(),
  };
})();
