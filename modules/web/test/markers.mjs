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
