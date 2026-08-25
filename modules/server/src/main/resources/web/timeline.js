// Time selector. Currently two range inputs; becomes the histogram strip (plan §1.7).
window.Timeline = (() => {
  function init(container, time) {
    const s = State.get();
    container.innerHTML =
      `<label>from</label><input type="range" id="from" min="${time.min}" max="${time.max}" step="25" value="${s.from}"><span class="yv" id="fromv"></span>`
      + `<label>to</label><input type="range" id="to" min="${time.min}" max="${time.max}" step="25" value="${s.to}"><span class="yv" id="tov"></span>`;
    const fromEl = container.querySelector('#from'), toEl = container.querySelector('#to');
    const labels = () => { container.querySelector('#fromv').textContent = Fmt.yr(+fromEl.value); container.querySelector('#tov').textContent = Fmt.yr(+toEl.value); };
    const push = () => { labels(); State.set({ from: +fromEl.value, to: +toEl.value }); };
    fromEl.addEventListener('input', push); toEl.addEventListener('input', push);
    labels();
  }
  return { init };
})();
