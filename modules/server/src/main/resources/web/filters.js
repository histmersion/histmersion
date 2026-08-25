// Filter bar (region text for now; chips for tags/authors/places arrive with chips.js).
window.Filters = (() => {
  function init(container) {
    container.innerHTML = `<label>region</label><input type="text" id="region" placeholder="e.g. Anatolia" />`;
    const el = container.querySelector('#region');
    el.value = State.get().region || '';
    el.addEventListener('input', () => State.set({ region: el.value.trim() }));
  }
  return { init };
})();
