// The browser engine the regressions and budgets run in: HISTMERSION_TEST_BROWSER=chromium (the default) or
// firefox. The map is drawn by WebGL; Chromium is launched on the GPU on macOS (Metal) unless
// HISTMERSION_TEST_GPU=0 (its software rasteriser, the map's no-GPU path); with HISTMERSION_TEST_GPU=1 on a machine
// without a GPU the regressions' pages are told a GPU renderer (standInGpu), so they take the GPU path, drawn by the
// CPU; Firefox draws WebGL as it would for a person (on macOS: the GPU). What only Chromium can measure (CDP: the
// heap, listener counts, forced garbage collection) is answered as `null` in Firefox, and the budgets name those figures.
import { chromium, firefox } from 'playwright';

export const engineName = (process.env.HISTMERSION_TEST_BROWSER || 'chromium').toLowerCase();
if (!['chromium', 'firefox'].includes(engineName)) throw new Error(`HISTMERSION_TEST_BROWSER=${engineName}: chromium or firefox`);
export const isChromium = engineName === 'chromium';
export const isFirefox = engineName === 'firefox';

/** Chromium's GPU flags (none when HISTMERSION_TEST_GPU=0, or off macOS unless asked). */
export function gpuArgs() {
  if (!isChromium) return [];
  const want = process.env.HISTMERSION_TEST_GPU ?? (process.platform === 'darwin' ? '1' : '0');
  return want === '0' ? [] : ['--enable-gpu', '--ignore-gpu-blocklist', ...(process.platform === 'darwin' ? ['--use-angle=metal'] : [])];
}

/** Whether the run asked for the GPU path: Chromium with the GPU flags, or Firefox (its own default). */
export const wantsGpu = () => isFirefox || gpuArgs().length > 0;

export const softwareRenderer = renderer => /swiftshader|llvmpipe|softpipe|software|basic render|mesa offscreen|lavapipe/i.test(renderer ?? '');

export const GPU_STAND_IN = 'ANGLE (Histmersion test, the GPU path on a CPU rasteriser)';

export async function rendererOf(browser) {
  const probe = await browser.newPage();
  try {
    return await probe.evaluate(() => {
      const gl = document.createElement('canvas').getContext('webgl2') ?? document.createElement('canvas').getContext('webgl');
      const ext = gl?.getExtension('WEBGL_debug_renderer_info');
      return (ext && gl.getParameter(ext.UNMASKED_RENDERER_WEBGL)) || gl?.getParameter(gl.RENDERER) || '';
    });
  } finally { await probe.close(); }
}

export async function renderMode(browser, what) {
  const renderer = await rendererOf(browser);
  const standIn = isChromium && wantsGpu() && softwareRenderer(renderer);
  console.log(`# ${what}: every figure held (renderer "${renderer || 'unknown'}"` +
    `${standIn ? `; the GPU path asked for without a GPU: the pages are told their renderer is "${GPU_STAND_IN}"` : ''})`);
  return standIn;
}

export function standInGpu(context) {
  return context.addInitScript(name => {
    for (const gl of [WebGLRenderingContext, WebGL2RenderingContext]) {
      const read = gl.prototype.getParameter;
      gl.prototype.getParameter = function getParameter(p) { return p === 0x9246 ? name : read.call(this, p); };
    }
  }, GPU_STAND_IN);
}

/** The engine's browser, launched headless. */
export function launch(extra = {}) {
  if (isFirefox) return firefox.launch({ headless: true, executablePath: process.env.PLAYWRIGHT_FIREFOX_EXECUTABLE || undefined, ...extra });
  return chromium.launch({ headless: true, executablePath: process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE || undefined, args: gpuArgs(), ...extra });
}

/** Chromium launched with its software rasteriser, whatever the run (the no-GPU test). */
export function launchSoftwareChromium() {
  return chromium.launch({ headless: true, executablePath: process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE || undefined });
}

/** Chromium launched with these switches taken off (`--disable-webgl2`, `--disable-webgl`): the map's
  * no-WebGL path. */
export function launchChromiumWithout(...switches) {
  return chromium.launch({ headless: true, executablePath: process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE || undefined, args: switches });
}

/** `/api/config` as the app reads it, with `over` laid over the server's answer — by default the first-run
  * setup NOT due, so a test server with an empty settings file opens on the page a test asks for, not on the
  * setup. `target`: a page or a context (register it after a catch-all route: the newest route answers first). */
export async function configOverride(target, over = {}) {
  // and, unless a test asks for the setup, this tab "left the setup" (the app's own sessionStorage mark): a route a
  // test adds later for /api/config answers with the server's setupDue, and still no page is sent to the setup
  if (over.setupDue !== true)
    await target.addInitScript(() => { try { sessionStorage.setItem('histmersion.setupLeft', '1'); } catch { /* none */ } });
  await target.route(url => new URL(url).pathname === '/api/config', async route => {
    const res = await route.fetch();
    await route.fulfill({ response: res, json: { ...(await res.json()), setupDue: false, ...over } });
  });
}
