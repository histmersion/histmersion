// chip-input component (tags/authors/places autocomplete with removable chips) — plan §1.5.
//
//   Chips.mount(el, {
//     field,               // facet field name (/api/facets?field=…), also used for element ids
//     placeholder, label,  // presentation
//     values,              // initial chips
//     debounceMs,          // suggestion debounce (from /api/config — no literals in JS)
//     getSuggestions(prefix) -> Promise<[{value, count}]>,
//     onChange(values)     // called on every add/remove
//   }) -> { setValues, values, focus }
//
// Keyboard: ↑/↓ move the suggestion, Enter picks it (or adds the typed text verbatim), Esc closes,
// Backspace on an empty input removes the last chip. A failing facet request just closes the list —
// free-text entry keeps working, which is what makes the bar usable when the server is degraded.
window.Chips = (() => {
  let seq = 0;

  function mount(el, opts) {
    const id = `chips-${opts.field}-${++seq}`;
    let values = (opts.values || []).slice();
    let items = [], active = -1, timer = null, reqId = 0;

    el.classList.add('chips');
    el.innerHTML =
      `<span class="chips-list"></span>` +
      `<input class="chip-input" type="text" autocomplete="off" spellcheck="false" role="combobox"` +
      ` aria-autocomplete="list" aria-expanded="false" aria-controls="${id}-list"` +
      ` aria-label="${Fmt.esc(opts.label || opts.field)}"` +
      ` placeholder="${Fmt.esc(opts.placeholder || '')}" />` +
      `<ul class="chip-suggest" id="${id}-list" role="listbox" hidden></ul>`;

    const list = el.querySelector('.chips-list');
    const input = el.querySelector('.chip-input');
    const menu = el.querySelector('.chip-suggest');

    const renderChips = () => {
      list.innerHTML = values.map((v, i) =>
        `<span class="chip">${Fmt.esc(v)}` +
        `<button type="button" class="chip-x" data-i="${i}" aria-label="remove ${Fmt.esc(v)}">✕</button></span>`
      ).join('');
    };

    const close = () => {
      menu.hidden = true; menu.innerHTML = ''; items = []; active = -1;
      input.setAttribute('aria-expanded', 'false');
      input.removeAttribute('aria-activedescendant');
    };

    const paint = () => {
      menu.innerHTML = items.map((it, i) =>
        `<li role="option" id="${id}-o${i}" data-i="${i}" class="${i === active ? 'active' : ''}"` +
        ` aria-selected="${i === active}">` +
        `<span class="v">${Fmt.esc(it.value)}</span> <span class="c">(${Fmt.esc(it.count)})</span></li>`
      ).join('');
      if (active >= 0) {
        input.setAttribute('aria-activedescendant', `${id}-o${active}`);
        const li = menu.children[active];
        if (li && li.scrollIntoView) li.scrollIntoView({ block: 'nearest' });
      } else input.removeAttribute('aria-activedescendant');
    };

    const open = rows => {
      items = (rows || []).filter(r => r && r.value != null && !values.includes(String(r.value)));
      if (!items.length) return close();
      active = -1; menu.hidden = false;
      input.setAttribute('aria-expanded', 'true');
      paint();
    };

    const suggest = () => {
      const prefix = input.value.trim();
      if (!prefix) return close();
      const mine = ++reqId;
      Promise.resolve(opts.getSuggestions(prefix)).then(
        rows => { if (mine === reqId && document.activeElement === input) open(rows); },
        () => { if (mine === reqId) close(); } // facets unavailable → silent; free text still works
      );
    };
    const schedule = () => { clearTimeout(timer); timer = setTimeout(suggest, opts.debounceMs); };

    const emit = () => { renderChips(); opts.onChange(values.slice()); };
    const add = raw => {
      const v = String(raw == null ? '' : raw).trim();
      input.value = ''; close();
      if (!v || values.includes(v)) return;
      values = values.concat([v]);
      emit();
    };
    const removeAt = i => { values = values.filter((_, j) => j !== i); emit(); };

    input.addEventListener('input', schedule);
    input.addEventListener('focus', () => { if (input.value.trim()) schedule(); });
    input.addEventListener('blur', () => close());
    input.addEventListener('keydown', e => {
      if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
        if (menu.hidden) { if (input.value.trim()) suggest(); return; }
        e.preventDefault();
        const d = e.key === 'ArrowDown' ? 1 : -1;
        active = active < 0 ? (d > 0 ? 0 : items.length - 1) : (active + d + items.length) % items.length;
        paint();
      } else if (e.key === 'Enter') {
        e.preventDefault();
        add(active >= 0 ? items[active].value : input.value);
      } else if (e.key === 'Escape') {
        if (!menu.hidden) { e.preventDefault(); e.stopPropagation(); close(); }
      } else if (e.key === 'Backspace' && input.value === '' && values.length) {
        e.preventDefault(); removeAt(values.length - 1);
      }
    });

    // mousedown (not click) + preventDefault: pick without the input ever blurring first.
    menu.addEventListener('mousedown', e => {
      const li = e.target.closest('li[data-i]'); if (!li) return;
      e.preventDefault(); add(items[+li.dataset.i].value);
    });
    menu.addEventListener('mouseover', e => {
      const li = e.target.closest('li[data-i]'); if (!li) return;
      active = +li.dataset.i; paint();
    });
    list.addEventListener('click', e => {
      const b = e.target.closest('.chip-x'); if (!b) return;
      removeAt(+b.dataset.i); input.focus();
    });
    el.addEventListener('mousedown', e => {
      if (e.target === el || e.target === list) { e.preventDefault(); input.focus(); }
    });

    renderChips();
    return {
      setValues(v) { values = (v || []).map(String); renderChips(); },
      values: () => values.slice(),
      focus: () => input.focus(),
    };
  }

  return { mount };
})();
