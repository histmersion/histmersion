export const marks = (page, spec = {}) => page.evaluate(s => window.__testMap.markers(s), spec);

export async function waitMarker(page, spec = {}, opts = {}) {
  await page.waitForFunction(s => window.__testMap?.markers(s).length > 0, spec, opts);
  return (await marks(page, spec))[0];
}

export async function waitCount(page, spec, n, opts = {}) {
  await page.waitForFunction(([s, n]) => window.__testMap?.markers(s).length === n, [spec, n], opts);
}

export async function waitGone(page, spec, opts = {}) {
  await page.waitForFunction(s => window.__testMap?.markers(s).length === 0, spec, opts);
}

export async function waitBubble(page, n, opts = {}) {
  await page.waitForFunction(n => window.__testMap?.markers({ flag: 'hd-cluster' })[0]?.count === n, n, opts);
}

export async function pixelDiff(page, a, b) {
  return page.evaluate(async ([a, b]) => {
    const load = src => new Promise(r => { const i = new Image(); i.onload = () => r(i); i.src = `data:image/png;base64,${src}`; });
    const px = img => {
      const c = document.createElement('canvas'); c.width = img.width; c.height = img.height;
      const g = c.getContext('2d'); g.drawImage(img, 0, 0); return g.getImageData(0, 0, c.width, c.height).data;
    };
    const [da, db] = (await Promise.all([load(a), load(b)])).map(px);
    let big = 0, sum = 0;
    for (let i = 0; i < da.length; i += 4) {
      const d = Math.max(Math.abs(da[i] - db[i]), Math.abs(da[i + 1] - db[i + 1]), Math.abs(da[i + 2] - db[i + 2]));
      sum += d; if (d > 48) big++;
    }
    const n = da.length / 4;
    return { bigPct: 100 * big / n, mean: sum / n };
  }, [a.toString('base64'), b.toString('base64')]);
}

export async function hoverMarker(page, spec) {
  const m = await waitMarker(page, spec);
  await page.mouse.move(m.clientX, m.clientY);
  return m;
}

export async function clickMarker(page, spec, opts = {}) {
  const m = await waitMarker(page, spec);
  await page.mouse.click(m.clientX, m.clientY, opts);
  return m;
}
