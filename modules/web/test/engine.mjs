// The browser engine the regressions and budgets run in: HISTMERSION_TEST_BROWSER=chromium (the default) or
// firefox. The map is drawn by WebGL; Chromium is launched on the GPU on macOS (Metal) unless
// HISTMERSION_TEST_GPU=0 (its software rasteriser, the map's no-GPU path); Firefox draws WebGL as it would
// for a person (on macOS: the GPU). What only Chromium can measure (CDP: the heap, listener counts, forced
// garbage collection) is answered as `null` in Firefox, and the budgets name those figures.
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
