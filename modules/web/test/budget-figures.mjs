export const FRAME_BOUND = new Set(['droppedPct', 'inputP95', 'stillFramesPct', 'releaseToListMs', 'shownMax', 'filledMax',
  'warmMax', 'keyP95', 'keyMax', 'suggestMs', 'previewToListMs', 'windowMs', 'contentMs', 'clickMs', 'keyMs', 'pageMs']);

export const leaf = key => key.split('.').at(-1).replace(/#\d+$/, '');

export const frameBound = key => FRAME_BOUND.has(leaf(key));

export function floorOf(key, baseMedian) {
  const k = leaf(key);
  if (k === 'heapMB' || k === 'heapTotalMB') return 3;
  if (k === 'nodes' || k === 'listeners' || k === 'layoutObjects') return Math.max(20, 0.05 * baseMedian);
  if (k.endsWith('PerSecond')) return 0.5;
  if (k === 'notAnimated' || k === 'rows' || k === 'points' || k === 'shapes' || k === 'markers') return 0;
  return 20;
}

export function median(xs) {
  const s = [...xs].sort((a, b) => a - b);
  const m = s.length >> 1;
  return s.length % 2 ? s[m] : (s[m - 1] + s[m]) / 2;
}

const spread = xs => Math.max(...xs) - Math.min(...xs);

export function compareFigure(key, base, pr) {
  const baseMedian = median(base), prMedian = median(pr);
  const noise = Math.max(spread(base), spread(pr));
  const floor = floorOf(key, baseMedian);
  const worse = prMedian - baseMedian;
  const separated = Math.min(...pr) > Math.max(...base);
  const regression = !frameBound(key) && separated && worse > Math.max(noise, floor);
  return { key, base, pr, baseMedian, prMedian, worse, noise, floor, frameBound: frameBound(key), regression };
}

export function compareRuns(baseRuns, prRuns) {
  const keys = [...new Set([...baseRuns, ...prRuns].flatMap(r => Object.keys(r)))].sort();
  return keys.map(key => {
    const base = baseRuns.map(r => r[key]).filter(v => typeof v === 'number');
    const pr = prRuns.map(r => r[key]).filter(v => typeof v === 'number');
    if (base.length !== baseRuns.length || pr.length !== prRuns.length)
      return { key, base, pr, missing: true, frameBound: frameBound(key), regression: false };
    return compareFigure(key, base, pr);
  });
}
