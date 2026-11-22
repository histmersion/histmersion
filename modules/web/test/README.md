# Browser regressions

These tests cover interactions that pure Scala.js tests cannot observe: the map's container resizing,
touch-slider versus drawer gestures, cancelled pointer sequences, and controlled search inputs —
and (`perf.test.mjs`) the web UI's speed and memory budgets and a soak test that fails on a leak
(docs/research-web-perf.md).
They use the real application and Chromium; external tile requests are blocked. They make no corpus
writes, but for `marks.test.mjs`: it writes the person's bookmarks, notes and read marks (and two sources named
`zz_mark_*`) into the test server's database, a `*_test` one, and takes the bookmarks, notes and read marks away
again. Use a separately started test server so tests do not depend on the live corpus.

From the repository root:

```bash
npm ci --prefix modules/web
npm --prefix modules/web exec -- playwright install chromium
just test-db
```

Start an already-built native server in a second terminal:

```bash
HISTMERSION_RUNTIME=false HISTMERSION_CONFIG=/tmp/histmersion-test.conf \
HISTMERSION_DB_URL=jdbc:postgresql://127.0.0.1:55432/histmersion_test \
HISTMERSION_PORT=18080 HISTMERSION_PUBLIC_URL=http://127.0.0.1:18080 \
HISTMERSION_BASEMAP_DIR=modules/web/test/fixtures/basemap \
HISTMERSION_BASEMAP_TERRAIN_ON_DEMAND=false HISTMERSION_BASEMAP_VECTOR_ON_DEMAND=false \
HISTMERSION_BASEMAP_BATHYMETRY_ON_DEMAND=false \
dist/histmersion/bin/histmersion-server
```

Then run:

```bash
HISTMERSION_SERVER_URL=http://127.0.0.1:18080 HISTMERSION_TEST_CONFIG_FILE=/tmp/histmersion-test.conf \
  npm --prefix modules/web run test:browser
```

Start the server with `HISTMERSION_CONFIG` naming a scratch settings file (an empty one will do), never your
own. The tests that WRITE the settings file through Settings and the setup run only when
`HISTMERSION_TEST_CONFIG_FILE` is set to that same file (they put it back as it was); without it they are
skipped. The harness answers `/api/config` with `setupDue: false`, so a server whose settings file sets nothing
opens on the page a test asks for, not on the first-run setup.

`HISTMERSION_BASEMAP_DIR` points the server at a small Aegean extract of the base maps' data
(`fixtures/basemap`, 10.5 MB with its physical names, sea floor and an Argolid focus region, its licences
in its README); without it the Readable/Physical tests are skipped. `HISTMERSION_BASEMAP_TERRAIN_ON_DEMAND=false`
keeps the server from reading deeper relief from the provider when a test zooms in, and the `VECTOR_` and
`BATHYMETRY_` ones from reading the map and the sea floor outside the extract (no network).

**Two engines.** `HISTMERSION_TEST_BROWSER=chromium` (the default) or `firefox` picks the browser
(`test/engine.mjs`); `npm --prefix modules/web run test:browser:firefox` runs both files in Firefox and
`test:browser:all` in both (install Firefox once: `npm --prefix modules/web exec -- playwright install
firefox`). Firefox has no CDP: the heap, the listener count and the layout objects are reported as not
measurable there (every other figure is held to the same budget), long tasks are measured by a 10 ms timer
arriving late (no Long Tasks API), a touch drag is a mouse drag, and the no-GPU test is Chromium's own.
Frames are counted dropped against 60 Hz in both (Firefox paints headless at 120 Hz).
`HISTMERSION_TEST_CONSOLE_LOG=<file>` appends every warning and error a page prints to that file.

**The GPU.** The map is drawn with WebGL (MapLibre GL JS). On macOS the tests launch Chromium with
`--enable-gpu --ignore-gpu-blocklist --use-angle=metal`, so the map is drawn by the GPU and held to the
GPU budgets; elsewhere, or with `HISTMERSION_TEST_GPU=0`, Chromium draws WebGL in software
(SwiftShader), the page takes its no-GPU path (no relief, the notice's extra line) and the vector maps'
budgets are their `…Software` ones (`perf.test.mjs`: input and stalls as on a GPU, the frame rate measured but not budgeted). `HISTMERSION_TEST_GPU=1` asks for the GPU on any
platform. The tests reach the map through `window.histmersionMap` (`web/MapHandle.scala`): the view,
the shape sets and the shapes under a point, the cluster layer's counts, the drawn base-map names, a
base-map layer's visibility and how many of its features are drawn (`layerVisibility`, `layerDrawn`: the
sea's depth, the contours inside the Argolid focus region).
`test:browser` runs the three files one at a time (`--test-concurrency=1`): side by side they wrote the same
settings file at once and measured the budgets while two other browsers were busy; the budgets and the soak
test take ~3 minutes of it (100 soak rounds). In CI the `budgets: …` tests run in their own job (`macos-budgets`,
`--test-name-pattern='^budgets: '`), the rest in `macos` (`--test-skip-pattern`, the same pattern).

**Budgets on a virtual GPU** (owner decision 2026-11-22). GitHub's macOS runner is an Apple M1 virtual machine (3
cores) whose WebGL renderer is "Apple Paravirtual device": it paints about 8 frames a second however idle the page's
main thread is (a pan drops 49 % of its frames with no long task; the same pan, the CPU throttled 3× on a real GPU,
drops none). There, and only there, the budgets hold the main-thread figures — every `longTaskMax`, the first
render's and the loads' times (`mapMs`, `markersMs`, `listMs`, `drawnMs`), the playback's request rates, the cluster
zoom's `notAnimated`, the heap, the DOM and the listeners — and print the frame-bound ones without failing on them:
dropped frames (`droppedPct`, `stillFramesPct`) and every input-to-next-paint figure (`inputP95`, the hover cards'
`shownMax`/`filledMax`/`warmMax`, the typing's `keyP95`/`keyMax`/`suggestMs`/`previewToListMs`, the article's
`windowMs`/`contentMs`, the release's `releaseToListMs`, Library's `clickMs`/`keyMs`/`pageMs`). The limits are the
same. The mode needs both the job's `HISTMERSION_VIRTUAL_GPU=1` and a WebGL renderer naming a paravirtual
device (`engine.mjs` `frameMode`); anything else — a real GPU, Firefox, an unknown renderer — holds every figure.
The first line of the run says which mode is in force. The job's colour is its own verdict: red when a held figure
is over its budget (or a budget test fails otherwise, is skipped or none ran); it is not a required check.

The same rule, the same switch (the `macos` job sets the variable too), applies to the functional tests' click
checks (owner decision 2026-11-22): a chip of the approvals, runs and jobs windows, the news mark in Research's side
navigation, the theme button. Each is measured twice — the click's own work (its handlers and the microtasks after
them, the theme's restyle), held under 100 ms everywhere, and the click to the next frame, held under 100 ms on a real
GPU and printed when over on a virtual one. `browser.test.mjs` prints its mode first, like the budgets.
`npm --prefix modules/web run test:perf` runs them alone; `HISTMERSION_SOAK_ROUNDS=20` shortens the
soak, `HISTMERSION_PERF_REPORT=1` prints every measured figure. `perf-baseline.mjs` measures the
same paths on any server, read-only (non-GET requests are refused) — the live baseline in the doc:

```bash
HISTMERSION_SERVER_URL=http://localhost:8000 node modules/web/test/perf-baseline.mjs --runs 3
```

`PLAYWRIGHT_CHROMIUM_EXECUTABLE` can point to an installed Chrome/Chromium binary instead of downloading
one. Stop the test server and run `just test-db-down` afterwards. The Node/Playwright dependency is
test-only and does not enter the Scala or native-image classpath.
