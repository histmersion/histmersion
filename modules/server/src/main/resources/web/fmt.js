// Pure formatting helpers (no DOM, no state) — shared by every UI module.
window.Fmt = (() => {
  const yr = y => y == null ? "?" : (y < 0 ? `${-y} BCE` : `${y} CE`);
  const periodStr = (a, b) => (a == null || b == null) ? "" : (a === b ? yr(a) : `${yr(a)}–${yr(b)}`);
  // viridis-ish ramp for a 0..1 value (older = purple, newer = yellow)
  const stops = [[68,1,84],[59,82,139],[33,145,140],[94,201,98],[253,231,37]];
  const color = t => {
    t = Math.max(0, Math.min(1, t)); const f = t * (stops.length - 1); const i = Math.floor(f); const g = f - i;
    const a = stops[i], b = stops[Math.min(i + 1, stops.length - 1)];
    return `rgb(${Math.round(a[0]+(b[0]-a[0])*g)},${Math.round(a[1]+(b[1]-a[1])*g)},${Math.round(a[2]+(b[2]-a[2])*g)})`;
  };
  const esc = s => String(s ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  return { yr, periodStr, color, esc };
})();
