// Pure formatting helpers (no DOM, no state) — shared by every UI module.
window.Fmt = (() => {
  // Signed astronomical years have a year 0; the era convention does not — 1 BCE is followed by
  // 1 CE. Printing "0 CE" invents a year that no source ever dates anything to, so the boundary is
  // named for what it is (item 20). Everything else is the plain era spelling.
  const yr = y => y == null ? "?" : (y < 0 ? `${-y} BCE` : y === 0 ? "1 BCE/1 CE" : `${y} CE`);
  const periodStr = (a, b) => (a == null || b == null) ? "" : (a === b ? yr(a) : `${yr(a)}–${yr(b)}`);
  // viridis-ish ramp for a 0..1 value (older = purple, newer = yellow)
  const stops = [[68,1,84],[59,82,139],[33,145,140],[94,201,98],[253,231,37]];
  const color = t => {
    t = Math.max(0, Math.min(1, t)); const f = t * (stops.length - 1); const i = Math.floor(f); const g = f - i;
    const a = stops[i], b = stops[Math.min(i + 1, stops.length - 1)];
    return `rgb(${Math.round(a[0]+(b[0]-a[0])*g)},${Math.round(a[1]+(b[1]-a[1])*g)},${Math.round(a[2]+(b[2]-a[2])*g)})`;
  };
  // Parse a year the way a historian types it (plan §1.7): "3300 BCE", "-3300", "1200 CE", "1200",
  // "c. 1500 BC", "1500 bc", "AD 1200", "1,200 CE". Pure: returns a signed year, or null if
  // unparseable. No era = the number as written, so "-3300" is 3300 BCE and "1200" is 1200 CE.
  const ERA = /^(bce|bc|ce|ad)$/;
  const parseYear = raw => {
    if (raw == null) return null;
    let t = String(raw).trim().toLowerCase().replace(/,/g, '').replace(/\./g, ' ');
    t = t.replace(/^(c|ca|circa)\s+/, '').trim();          // "c. 1500 bc" → "1500 bc"
    const parts = t.split(/\s+/).filter(Boolean);
    if (parts.length === 0 || parts.length > 2) return null;
    let era = null, num = null;
    for (const p of parts) {
      if (ERA.test(p)) { if (era) return null; era = p; }
      else if (/^[+-]?\d+$/.test(p)) { if (num !== null) return null; num = Number(p); }
      else if (/^\d+(bce|bc|ce|ad)$/.test(p)) { era = p.replace(/^\d+/, ''); num = Number(p.replace(/\D+$/, '')); }
      else return null;
    }
    if (num === null || !Number.isFinite(num)) return null;
    if (era === 'bce' || era === 'bc') return -Math.abs(num);
    if (era === 'ce' || era === 'ad') return Math.abs(num);
    return num;
  };
  // VSUP-style desaturation: uncertain findings look less vivid, so a reader does not give them
  // the same weight as high-confidence ones (Correll, Moritz & Heer, CHI 2018).
  const confSat = { high: 1.0, medium: 0.7, low: 0.4, contested: 0.3 };
  const desaturate = (rgbStr, confidence) => {
    const s = confSat[confidence] ?? 1.0;
    if (s >= 1.0) return rgbStr;
    const m = rgbStr.match(/\d+/g);
    if (!m || m.length < 3) return rgbStr;
    const [r, g, b] = m.map(Number);
    const grey = Math.round(0.299 * r + 0.587 * g + 0.114 * b);
    return `rgb(${Math.round(grey + (r - grey) * s)},${Math.round(grey + (g - grey) * s)},${Math.round(grey + (b - grey) * s)})`;
  };
  // The confidence ordering for clusters: use the lowest (most conservative).
  const confOrder = { contested: 0, low: 1, medium: 2, high: 3 };
  const worstConf = items => {
    let worst = 'high';
    for (const it of items) {
      const c = it.confidence || 'high';
      if ((confOrder[c] ?? 3) < (confOrder[worst] ?? 3)) worst = c;
    }
    return worst;
  };
  const displayTag = s => String(s ?? '').replace(/_/g, ' ');
  const esc = s => String(s ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  return { yr, parseYear, periodStr, color, desaturate, worstConf, confSat, displayTag, esc };
})();
