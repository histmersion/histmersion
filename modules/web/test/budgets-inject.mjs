import { chromium } from 'playwright';

const ms = Number(process.env.HISTMERSION_BUDGETS_INJECT_ZOOM_MS || 0);
const launch = chromium.launch.bind(chromium);
chromium.launch = async (...a) => {
  const browser = await launch(...a);
  const newContext = browser.newContext.bind(browser);
  browser.newContext = async (...c) => {
    const context = await newContext(...c);
    if (ms > 0) await context.addInitScript(busy => {
      const hook = () => {
        const M = window.maplibregl?.Map;
        if (!M) return setTimeout(hook, 5);
        const fire = M.prototype.fire;
        let zooming = false;
        M.prototype.fire = function (e, ...r) {
          const type = typeof e === 'string' ? e : e?.type;
          if (type === 'zoomstart') zooming = true;
          else if (type === 'zoomend') zooming = false;
          else if (type === 'render' && zooming) { const t = performance.now(); while (performance.now() - t < busy); }
          return fire.call(this, e, ...r);
        };
      };
      hook();
    }, ms);
    return context;
  };
  return browser;
};
