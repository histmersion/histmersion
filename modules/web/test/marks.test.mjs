// Phase 8 of the web UI: bookmarks, notes and read marks; My library's shelves; "Save this view"; the one context
// menu and its keyboard path; the map's and the timeline's lock and start; the results column's width (#7780) and
// its rows' own actions (#7781). These tests WRITE the person's bookmarks, notes and read marks to the test
// server's database (a *_test one, never a person's own) and take them away again; the two that write the
// settings file run only when HISTMERSION_TEST_CONFIG_FILE names the test server's own file.
import assert from 'node:assert/strict';
import { after, before, test } from 'node:test';
import { configOverride, isChromium, launch } from './engine.mjs';
import { routeFixtures } from './perf-fixtures.mjs';
import { routeLibrary } from './library-fixtures.mjs';

const baseURL = process.env.HISTMERSION_SERVER_URL;
assert.ok(baseURL, 'Set HISTMERSION_SERVER_URL to the test server');
const origin = new URL(baseURL).origin;
const desk = { width: 1440, height: 900 };
const tablet = { width: 820, height: 1180 };

let browser;
before(async () => { browser = await launch(); });
after(async () => { await browser?.close(); });

async function withPage(viewport, run, { path = '/explore', fixtures = 'explore', permissions = [] } = {}) {
  const context = await browser.newContext({ viewport, hasTouch: viewport.width < 1000 });
  if (permissions.length && isChromium) await context.grantPermissions(permissions, { origin });
  try {
    const page = await context.newPage();
    const errors = [];
    page.on('pageerror', e => errors.push(e.message));
    page.on('dialog', d => d.accept());
    await page.addInitScript(() => {
      Object.defineProperty(window, '__testMap', { configurable: true, get: () => window.histmersionMap });
    });
    await page.route('**/*', r => (new URL(r.request().url()).origin === origin ? r.continue() : r.abort()));
    if (fixtures === 'explore') await routeFixtures(page);
    if (fixtures === 'library') await routeLibrary(page);
    await configOverride(page);
    await page.goto(new URL(path, baseURL).href);
    await clearMine(page);
    await page.reload();
    if (path.startsWith('/explore')) await page.waitForFunction(() => window.__testMap);
    await run(page);
    await clearMine(page);
    assert.deepEqual(errors, [], 'application JavaScript errors');
  } finally {
    await context.close();
  }
}

/** Every bookmark and note of the test server's person taken away, read marks set back. */
async function clearMine(page) {
  await page.evaluate(async () => {
    const del = (p) => fetch(p, { method: 'DELETE' });
    for (const b of await (await fetch('/api/bookmarks?limit=1000')).json()) await del(`/api/bookmarks/${b.id}`);
    for (const n of await (await fetch('/api/notes?limit=1000')).json()) await del(`/api/notes/${n.id}`);
    for (const m of await (await fetch('/api/reading?show=read&limit=1000')).json())
      await fetch('/api/reading', { method: 'PUT', headers: { 'content-type': 'application/json' },
        body: JSON.stringify({ kind: m.kind, id: m.id, read: false }) });
  });
}

const api = (page, path) => page.evaluate(async p => (await fetch(p)).json(), path);

// ── #7781: a row's actions are the row's own ─────────────────────────────────────────────────────────────

test('#7781: a result row\'s bookmark and new-tab actions sit inside that row, at its right, as tall as it', async () => {
  await withPage(desk, async page => {
    await page.locator('#list .hit-source .row-actions').nth(2).waitFor();
    for (let i = 0; i < 3; i++) {
      const row = page.locator('#list .hit-source').nth(i);
      const [r, a] = await Promise.all([row.boundingBox(), row.locator('.row-actions').boundingBox()]);
      assert.ok(a.y >= r.y - 0.5 && a.y + a.height <= r.y + r.height + 0.5, `row ${i}: the actions stay inside their row`);
      assert.ok(Math.abs(a.x + a.width - (r.x + r.width)) <= 2, `row ${i}: the actions are at the row's right`);
      assert.ok(a.height >= r.height - 2, `row ${i}: the actions are as tall as the row (${a.height} of ${r.height})`);
      // what the pointer meets over the new-tab icon belongs to this row, not to the one below
      const tab = await row.locator('.new-tab').boundingBox();
      const owner = await page.evaluate(([x, y]) => {
        const el = document.elementFromPoint(x, y);
        return [...document.querySelectorAll('#list .hit-source')].indexOf(el.closest('.hit-source'));
      }, [tab.x + tab.width / 2, tab.y + tab.height - 2]);
      assert.equal(owner, i, `row ${i}: the new-tab icon's bottom edge is still row ${i}'s`);
    }
    // the actions light with their row's hover
    const row = page.locator('#list .hit-source').first();
    await row.hover();
    const lit = await row.locator('.row-actions').evaluate(el => getComputedStyle(el).borderLeftColor);
    assert.notEqual(lit, 'rgba(0, 0, 0, 0)', 'the hovered row draws its actions\' edge');
  });
});

// ── #7780: the results column ──────────────────────────────────────────────────────────────────────────────

async function tabsFit(page) {
  return page.evaluate(() => {
    document.querySelectorAll('.results-panel .tabs .tab').forEach(t => {
      let c = t.querySelector('.tab-count');
      if (!c) { c = document.createElement('span'); c.className = 'tab-count'; t.appendChild(c); }
      c.textContent = '88888';
    });
    const tabs = document.querySelector('.results-panel .tabs');
    return { client: tabs.clientWidth, scroll: tabs.scrollWidth, count: tabs.querySelectorAll('.tab').length };
  });
}

for (const [name, vp] of [['a desktop', desk], ['a tablet', tablet]]) {
  test(`#7780: on ${name} the results column shows all five tabs with five-digit counts, unclipped`, async () => {
    await withPage(vp, async page => {
      await page.locator('.results-panel .tabs .tab').nth(4).waitFor();
      const m = await tabsFit(page);
      assert.equal(m.count, 5);
      assert.ok(m.scroll <= m.client, `the tab row fits (${m.scroll} px in ${m.client} px)`);
    });
  });
}

test('#7780: the results column resizes by keyboard and by drag, is remembered, and leaves the map to resize alone', async () => {
  await withPage(desk, async page => {
    const width = () => page.locator('.results-panel').evaluate(el => Math.round(el.getBoundingClientRect().width));
    const w0 = await width();
    const handle = page.locator('.sidebar-resize');
    assert.equal(await handle.getAttribute('role'), 'separator');
    await handle.focus();
    for (let i = 0; i < 3; i++) await page.keyboard.press('ArrowLeft');
    assert.equal(await width(), w0 + 48, 'three ← make it 48 px wider');
    await page.keyboard.press('Home');
    const narrowest = await width();
    assert.ok(narrowest < w0, 'Home: the narrowest');
    // at its narrowest the tabs scroll rather than clip
    const m = await tabsFit(page);
    assert.ok(m.scroll > m.client ? await page.locator('.results-panel .tabs').evaluate(el => getComputedStyle(el).overflowX) === 'auto' : true,
      'a tab row wider than the column scrolls');
    // a drag, then a reload: the width is kept
    const box = await handle.boundingBox();
    await page.mouse.move(box.x + box.width / 2, box.y + 200);
    await page.mouse.down();
    await page.mouse.move(box.x + box.width / 2 - 120, box.y + 200, { steps: 6 });
    await page.mouse.up();
    const dragged = await width();
    assert.ok(Math.abs(dragged - (narrowest + 120)) <= 2, `dragged 120 px wider (${dragged})`);
    const findings = [];
    page.on('request', r => { if (new URL(r.url()).pathname === '/api/findings') findings.push(r.url()); });
    await page.reload();
    await page.waitForFunction(() => window.__testMap);
    assert.equal(await width(), dragged, 'the width this browser kept');
    await page.waitForTimeout(800);
    findings.length = 0;
    await page.locator('.sidebar-resize').focus();
    await page.keyboard.press('ArrowRight');
    await page.waitForTimeout(800);
    assert.deepEqual(findings, [], 'resizing the column asks for no findings again');
  });
});

// ── bookmarks ─────────────────────────────────────────────────────────────────────────────────────────────

test('bookmarks: an item from a result row and from the reading pane, listed in My library and opened back', async () => {
  await withPage(desk, async page => {
    const first = page.locator('#list .hit-source').first();
    await first.locator('.row-bookmark').click();
    await page.waitForFunction(() => document.querySelector('#list .hit-source .row-bookmark')?.getAttribute('aria-pressed') === 'true');
    const saved = await api(page, '/api/bookmarks');
    assert.equal(saved.length, 1);
    assert.deepEqual([saved[0].target.kind, saved[0].target.id], ['source', 'zz_src_0']);
    // clicking again takes it away
    await first.locator('.row-bookmark').click();
    await page.waitForFunction(async () => (await (await fetch('/api/bookmarks')).json()).length === 0);
  });
  await withPage(desk, async page => {
    await page.locator('.lib-row').nth(3).click();
    await page.locator('.reading-item .item-bookmark').click();
    await page.locator('.reading-item .item-bookmark[aria-pressed="true"]').waitFor();
    assert.match(await page.locator('.reading-item .item-bookmark').innerText(), /Bookmarked/);
    await page.locator('.my-library [data-shelf="bookmarks"]').click();
    await page.waitForURL(u => new URL(u).search.includes('shelf=bookmarks'));
    const row = page.locator('.shelf-row').first();
    assert.match(await row.innerText(), /Zz library source 3/);
    assert.match(await page.locator('.my-library [data-shelf="bookmarks"] .side-count').innerText(), /^1$/);
    await page.goto(new URL('/library?shelf=bookmarks', baseURL).href);
    await page.locator('.shelf-row .shelf-title').first().click();
    await page.waitForURL(u => new URL(u).search.includes('item=zz_lib_3'));
    await page.locator('.reading-item[data-key="zz_lib_3"]').waitFor();
    await page.locator('.shelf-row .shelf-remove').click();
    await page.locator('.shelf-list .library-empty').waitFor();
  }, { path: '/library', fixtures: 'library' });
});

test('"Save this view": the filters, the span and the map kept under a name, listed, and opened back', async () => {
  await withPage(desk, async page => {
    await page.evaluate(() => window.__testMap.setView([37.6, 22.8], 7));
    await page.locator('#save-view').click();
    await page.locator('#save-view-name').fill('Zz Argolid view');
    await page.locator('.save-view-form button[type=submit]').click();
    await page.locator('.saved-view-open', { hasText: 'Zz Argolid view' }).waitFor();
    const [b] = await api(page, '/api/bookmarks?kind=view');
    assert.equal(b.label, 'Zz Argolid view');
    assert.ok(Math.abs(b.target.lat - 37.6) < 0.01 && Math.abs(b.target.zoom - 7) < 0.01, 'the map position is kept');
    const span = await page.evaluate(() => location.hash);
    // move away, then open the view back
    await page.evaluate(() => window.__testMap.setView([10, 10], 3));
    await page.locator('#tl-all-time').click();
    await page.locator('.saved-view-open').click();
    await page.waitForFunction(() => Math.abs(window.__testMap.getCenter().lat - 37.6) < 0.05 && Math.abs(window.__testMap.getZoom() - 7) < 0.05);
    assert.equal(await page.evaluate(() => location.hash), span, 'the filters and the span are back');
    // and it is a bookmark in My library
    await page.goto(new URL('/library?shelf=bookmarks', baseURL).href);
    assert.match(await page.locator('.shelf-row').first().innerText(), /Saved view[\s\S]*Zz Argolid view/);
  });
});

// ── notes ─────────────────────────────────────────────────────────────────────────────────────────────────

test('notes: written with a preview, the draft kept, saved, changed, listed where they were left, deleted', async () => {
  await withPage(desk, async page => {
    await page.locator('.lib-row').nth(2).click();
    await page.locator('.reading-item .item-note').click();
    const text = page.locator('#note-editor-text');
    await text.fill('Zz compare with **Langgut**');
    await page.keyboard.press('Escape');
    await page.locator('.note-editor').waitFor({ state: 'detached' });
    // the unsaved text is kept by the browser
    await page.locator('.reading-item .item-note').click();
    await page.locator('.note-draft-restored').waitFor();
    assert.equal(await text.inputValue(), 'Zz compare with **Langgut**');
    await page.locator('.note-preview-tab').click();
    assert.equal(await page.locator('.note-editor-preview strong').innerText(), 'Langgut');
    await page.locator('.note-save').click();
    await page.locator('.reading-item .note-card').waitFor();
    assert.match(await page.locator('.reading-item .note-card').innerText(), /Your note · just now[\s\S]*Zz compare with Langgut/);
    assert.equal(await page.evaluate(() => Object.keys(localStorage).filter(k => k.startsWith('histmersion.noteDraft.')).length), 0,
      'a saved note leaves no draft');
    // changed
    await page.locator('.reading-item .note-edit').click();
    await text.fill('Zz changed note');
    await page.locator('.note-save').click();
    await page.locator('.reading-item .note-card', { hasText: 'Zz changed note' }).waitFor();
    const [n] = await api(page, '/api/notes');
    assert.deepEqual([n.target.kind, n.target.id, n.text], ['source', 'zz_lib_2', 'Zz changed note']);
    // My notes lists it, and opens back to it
    await page.goto(new URL('/library?shelf=notes', baseURL).href);
    const row = page.locator('.shelf-row').first();
    assert.match(await row.innerText(), /Note on a source[\s\S]*Zz changed note/);
    await row.locator('.shelf-title').click();
    await page.waitForURL(u => new URL(u).search.includes('item=zz_lib_2'));
    await page.locator('.reading-item[data-key="zz_lib_2"] .note-card').waitFor();
    // deleted (the dialog is accepted)
    await page.locator('.reading-item .note-delete').click();
    await page.locator('.reading-item .note-card').waitFor({ state: 'detached' });
    assert.deepEqual(await api(page, '/api/notes'), []);
  }, { path: '/library', fixtures: 'library' });
});

test('a note at a place on the map: left from the map\'s menu by keyboard, shown on the "My notes" layer', async () => {
  await withPage(desk, async page => {
    await page.evaluate(() => window.__testMap.setView([37.6, 22.8], 7));
    await page.locator('#map canvas.maplibregl-canvas').focus();
    await page.keyboard.press('Shift+F10');
    const menu = page.locator('.context-menu[role=menu]');
    await menu.waitFor();
    assert.equal(await menu.getAttribute('aria-label'), 'Map actions');
    assert.equal(await page.evaluate(() => document.activeElement?.getAttribute('role')), 'menuitem', 'the first action has the focus');
    await page.keyboard.press('ArrowDown');
    assert.match(await page.evaluate(() => document.activeElement.innerText), /Add a note here/);
    await page.keyboard.press('Enter');
    await page.locator('#note-editor-text').fill('Zz a bridge here?');
    await page.locator('.note-save').click();
    await page.locator('.note-editor').waitFor({ state: 'detached' });
    const [n] = await api(page, '/api/notes');
    assert.equal(n.target.kind, 'point');
    assert.ok(Math.abs(n.target.lat - 37.6) < 0.05 && Math.abs(n.target.lon - 22.8) < 0.05, 'left at the map\'s centre');
    // the layer is off by default; on, the note is a marker that opens the note
    assert.equal(await page.locator('.hd-note-marker').count(), 0, 'the notes layer starts off');
    if (!(await page.locator('#layers-panel').isVisible())) await page.locator('#layers-btn').click();
    await page.locator('.layer-group[data-layer="notes"] .btn--toggle').click();
    const marker = page.locator('.hd-note-marker');
    await marker.waitFor();
    await marker.focus();
    await page.keyboard.press('Enter');
    await page.locator('.note-popup .note-card', { hasText: 'Zz a bridge here?' }).waitFor();
    await page.locator('.layer-group[data-layer="notes"] .btn--toggle').click();
    // My notes opens it back at its place
    await page.goto(new URL('/library?shelf=notes', baseURL).href);
    await page.locator('.shelf-row .shelf-title').click();
    await page.waitForURL(u => new URL(u).pathname === '/explore');
    await page.waitForFunction(() => Math.abs(window.__testMap.getCenter().lat - 37.6) < 0.05);
  });
});

// ── read marks and My library ───────────────────────────────────────────────────────────────────────────────

test('read marks: a dot for not read, Mark as read and unread, "Not read yet" and "Recently opened"', async () => {
  await withPage(desk, async page => {
    // two real sources, so the server's "Not read yet" has something of its own to list
    await page.evaluate(async () => {
      for (const [ck, t] of [['zz_mark_a', 'Zz mark A'], ['zz_mark_b', 'Zz mark B']])
        await fetch(`/api/sources/${ck}`, { method: 'PUT', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ title: t }) });
    });
    await page.reload();
    const row1 = page.locator('.lib-row[data-key="zz_lib_1"]');
    await row1.locator('.unread-dot').waitFor();
    await row1.click();
    const read = page.locator('.reading-item .item-read');
    assert.equal(await read.innerText(), 'Mark as read', 'opening marks nothing read');
    await read.click();
    await page.locator('.reading-item .item-read', { hasText: 'Mark as unread' }).waitFor();
    await row1.locator('.unread-dot').waitFor({ state: 'detached' });
    await read.click();
    await row1.locator('.unread-dot').waitFor();
    // Recently opened lists what was opened, newest first
    await page.locator('.lib-row[data-key="zz_lib_4"]').click();
    await page.locator('.reading-item[data-key="zz_lib_4"]').waitFor();
    await page.locator('.my-library [data-shelf="recent"]').click();
    await page.waitForFunction(() => document.querySelectorAll('.shelf-row').length >= 2);
    await page.waitForFunction(() => /^Zz library source 4\b/.test(document.querySelector('.shelf-row .shelf-title')?.innerText ?? ''),
      null, { timeout: 5000 }).catch(() => {});
    const recent = (await page.locator('.shelf-row .shelf-title').allInnerTexts()).slice(0, 2);
    assert.match(recent[0], /^Zz library source 4\b/);
    assert.match(recent[1], /Zz library source 1\b/);
    // Not read yet: the server's own sources, less what is marked read
    await page.locator('.my-library [data-shelf="unread"]').click();
    await page.locator('.shelf-row', { hasText: 'Zz mark B' }).waitFor();
    await page.locator('.shelf-row', { hasText: 'Zz mark B' }).locator('.shelf-title').click();
    await page.locator('.reading-item[data-key="zz_mark_b"] .item-read').click();
    await page.locator('.shelf-row', { hasText: 'Zz mark B' }).waitFor({ state: 'detached' });
    assert.ok(await page.locator('.shelf-row', { hasText: 'Zz mark A' }).count() === 1);
  }, { path: '/library', fixtures: 'library' });
});

// ── the context menu and its keyboard path ────────────────────────────────────────────────────────────────────

test('the context menu: a right click and Shift+F10 on a row, the "⋯" button in the reading pane, copies', async () => {
  await withPage(desk, async page => {
    const row = page.locator('#list .hit-source').first();
    await row.click({ button: 'right' });
    const menu = page.locator('.context-menu[role=menu]');
    await menu.waitFor();
    const labels = await menu.locator('[role^=menuitem]').allInnerTexts();
    assert.deepEqual(labels.map(l => l.trim()), ['Copy the text', 'Copy as Markdown', 'Copy the link', 'Bookmark', 'Open in a new tab']);
    await page.keyboard.press('Escape');
    await menu.waitFor({ state: 'detached' });
    // the keyboard: focus the row, Shift+F10, ↓, Escape gives the focus back
    await row.focus();
    await page.keyboard.press('Shift+F10');
    await menu.waitFor();
    await page.keyboard.press('ArrowDown');
    assert.match(await page.evaluate(() => document.activeElement.innerText), /Copy as Markdown/);
    await page.keyboard.press('ArrowDown');
    await page.keyboard.press('Enter');
    await menu.waitFor({ state: 'detached' });
    if (isChromium) {
      const copied = await page.evaluate(() => navigator.clipboard.readText());
      assert.equal(copied, `${origin}/source/zz_src_0`, 'Copy the link: the item\'s whole address');
    }
    await row.focus();
    await page.keyboard.press('Shift+F10');
    await menu.waitFor();
    await page.keyboard.press('Escape');
    assert.equal(await page.evaluate(() => document.activeElement.classList.contains('hit-source')), true, 'the focus is back on the row');
    // Bookmark from the menu
    await row.click({ button: 'right' });
    await menu.locator('[role^=menuitem]', { hasText: 'Bookmark' }).click();
    await page.waitForFunction(async () => (await (await fetch('/api/bookmarks')).json()).length === 1);
  }, { permissions: ['clipboard-read', 'clipboard-write'] });
  await withPage(desk, async page => {
    await page.locator('.lib-row').first().click();
    const more = page.locator('.reading-item .item-more');
    await more.click();
    await page.locator('.context-menu[role=menu]').waitFor();
    await page.locator('.context-menu [role^=menuitem]', { hasText: 'Copy as Markdown' }).click();
    if (isChromium) assert.equal(await page.evaluate(() => navigator.clipboard.readText()),
      `[Zz library source 0: a study of the long past](${origin}/source/zz_lib_0) — A, B, C et al., 1950 [[zz_lib_0]]`);
  }, { path: '/library', fixtures: 'library', permissions: ['clipboard-read', 'clipboard-write'] });
});

// ── the map's and the timeline's lock, start and periods ───────────────────────────────────────────────────

test('lock the map: no wheel or drag moves it, it says so, and Unlock frees it', async () => {
  await withPage(desk, async page => {
    await page.evaluate(() => window.__testMap.setView([37.6, 22.8], 6));
    await page.locator('.map-actions-btn').click();
    await page.locator('.context-menu [role^=menuitem]', { hasText: 'Lock the map' }).click();
    await page.locator('.map-lock-note').waitFor();
    const before = await page.evaluate(() => [window.__testMap.getCenter(), window.__testMap.getZoom()]);
    const box = await page.locator('#map').boundingBox();
    const [x, y] = [box.x + box.width / 2, box.y + box.height / 2];
    await page.mouse.move(x, y);
    await page.mouse.wheel(0, -400);
    await page.mouse.down();
    await page.mouse.move(x + 150, y + 80, { steps: 6 });
    await page.mouse.up();
    await page.waitForTimeout(400);
    assert.deepEqual(await page.evaluate(() => [window.__testMap.getCenter(), window.__testMap.getZoom()]), before, 'the locked map stayed');
    await page.locator('.map-unlock').click();
    await page.locator('.map-lock-note').waitFor({ state: 'hidden' });
    await page.mouse.move(x, y);
    await page.mouse.down();
    await page.mouse.move(x + 150, y + 80, { steps: 6 });
    await page.mouse.up();
    await page.waitForFunction(lng => Math.abs(window.__testMap.getCenter().lng - lng) > 0.1, before[0].lng);
  });
});

test('lock the timeline by its menu\'s keyboard path; keep its years as one of my periods', async () => {
  await withPage(desk, async page => {
    const span = () => page.locator('.tl-span-label').innerText();
    await page.locator('.tl-menu-btn').focus();
    await page.keyboard.press('Enter');
    await page.locator('.context-menu[aria-label="Timeline actions"]').waitFor();
    await page.keyboard.press('Enter'); // the first: Lock the timeline
    await page.locator('.tl-lock-note').waitFor();
    const s0 = await span();
    await page.locator('#tl-all-time').click();
    const ov = await page.locator('.tl-overview-svg').boundingBox();
    await page.mouse.click(ov.x + ov.width * 0.1, ov.y + ov.height / 2);
    assert.equal(await span(), s0, 'the locked timeline keeps its years');
    await page.locator('.tl-unlock').click();
    await page.locator('.tl-lock-note').waitFor({ state: 'hidden' });
    // my periods: the years shown, kept under a name, then chosen from "Zoom to period"
    await page.locator('.tl').click({ button: 'right', position: { x: 20, y: 5 } });
    await page.locator('.context-menu [role^=menuitem]', { hasText: 'Add these years to my periods' }).click();
    await page.locator('#tl-period-name').fill('Zz my crisis years');
    await page.locator('.tl-period-form button[type=submit]').click();
    await page.locator('.tl-my-period-item', { hasText: 'Zz my crisis years' }).waitFor();
    const [p] = await api(page, '/api/bookmarks?kind=period');
    assert.equal(p.label, 'Zz my crisis years');
    await page.locator('#tl-all-time').click();
    const all = await span();
    await page.locator('#tl-zoom-period').click();
    await page.locator('.tl-my-period-item', { hasText: 'Zz my crisis years' }).click();
    await page.waitForFunction(a => document.querySelector('.tl-span-label').innerText !== a, all);
    assert.equal(await span(), s0, 'my period shows its years');
  });
});

/** Tests that WRITE the server's settings file run only when HISTMERSION_TEST_CONFIG_FILE names that very file. */
async function writableSettings(page, t) {
  const want = process.env.HISTMERSION_TEST_CONFIG_FILE;
  const file = await page.evaluate(async () => (await (await fetch('/api/settings?prefix=setup')).json()).file);
  if (!want || !file || file !== want) {
    t.skip(`set HISTMERSION_TEST_CONFIG_FILE to the test server's own settings file (${file ?? 'none'}) to run it`);
    return false;
  }
  return true;
}

async function settingValue(page, key) {
  return page.evaluate(async k => {
    const p = await (await fetch(`/api/settings?prefix=${encodeURIComponent(k.split('.').slice(0, -1).join('.'))}`)).json();
    return p.sections.flatMap(s => s.fields).find(f => f.key === `histmersion.${k}`)?.value ?? null;
  }, key);
}

async function unset(page, keys) {
  // a change the page itself made a moment ago may still move the file's version: read it again on a 409
  const answer = await page.evaluate(async ks => {
    let last = [0, ''];
    for (let i = 0; i < 3 && last[0] !== 200; i++) {
      const version = (await (await fetch('/api/settings?prefix=ui')).json()).version;
      const r = await fetch('/api/settings', { method: 'POST', headers: { 'content-type': 'application/json' },
        body: JSON.stringify({ changes: ks.map(key => ({ key })), version }) });
      last = [r.status, await r.text()];
    }
    return last;
  }, keys);
  assert.equal(answer[0], 200, `the settings set back: ${answer[1]}`);
}

test('start the map here and start the timeline at its years: written to the settings file, then set back', async t => {
  await withPage(desk, async page => {
    if (!(await writableSettings(page, t))) return;
    const keys = ['ui.map-start.mode', 'ui.map-start.lat', 'ui.map-start.lon', 'ui.map-start.zoom', 'ui.map-start.label',
      'ui.time.default-from', 'ui.time.default-to'];
    try {
      await page.evaluate(() => window.__testMap.setView([37.6, 22.8], 6));
      await page.locator('#map canvas.maplibregl-canvas').focus();
      const centre = await page.evaluate(() => window.__testMap.getCenter());
      await page.keyboard.press('Shift+F10');
      await page.locator('.context-menu [role^=menuitem]', { hasText: 'Start the map here' }).click();
      await page.waitForFunction(async () => {
        const p = await (await fetch('/api/settings?prefix=ui.map-start')).json();
        return p.sections.flatMap(s => s.fields).find(f => f.key === 'histmersion.ui.map-start.mode')?.value === '"place"';
      });
      const lat = await settingValue(page, 'ui.map-start.lat');
      assert.ok(Math.abs(Number(lat) - centre.lat) < 0.01, `the map's centre (${centre.lat}) is where it starts: ${lat}`);
      await page.locator('.tl-menu-btn').click();
      const label = await page.locator('.tl-span-label').innerText();
      await page.locator('.context-menu [role^=menuitem]', { hasText: 'Start the timeline at' }).click();
      await page.waitForFunction(async () => {
        const p = await (await fetch('/api/settings?prefix=ui.time')).json();
        return p.sections.flatMap(s => s.fields).some(f => f.key === 'histmersion.ui.time.default-from' && f.value != null);
      });
      assert.ok(label.length > 0);
    } finally {
      await unset(page, keys);
    }
    assert.equal(await settingValue(page, 'ui.map-start.mode'), null, 'set back to the default');
  });
});
