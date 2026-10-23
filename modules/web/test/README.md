# Browser regressions

These tests cover interactions that pure Scala.js tests cannot observe: the map's container resizing,
touch-slider versus drawer gestures, cancelled pointer sequences, and controlled search inputs —
and (`perf.test.mjs`) the web UI's speed and memory budgets and a soak test that fails on a leak
(docs/research-web-perf.md).
They use the real application and Chromium; external tile requests are blocked. They make no corpus
writes. Use a separately started test server so tests do not depend on the live corpus.

From the repository root:

```bash
npm ci --prefix modules/web
npm --prefix modules/web exec -- playwright install chromium
just test-db
```

Start an already-built native server in a second terminal:

```bash
HISTMERSION_RUNTIME=false \
HISTMERSION_DB_URL=jdbc:postgresql://127.0.0.1:55432/histmersion_test \
HISTMERSION_PORT=18080 HISTMERSION_PUBLIC_URL=http://127.0.0.1:18080 \
HISTMERSION_BASEMAP_DIR=modules/web/test/fixtures/basemap \
HISTMERSION_BASEMAP_TERRAIN_ON_DEMAND=false \
dist/histmersion/bin/histmersion-server
```

Then run:

```bash
HISTMERSION_SERVER_URL=http://127.0.0.1:18080 npm --prefix modules/web run test:browser
```

`HISTMERSION_BASEMAP_DIR` points the server at a small Aegean extract of the base maps' data
(`fixtures/basemap`, 10.5 MB with its physical names, sea floor and an Argolid focus region, its licences
in its README); without it the Readable/Physical tests are skipped. `HISTMERSION_BASEMAP_TERRAIN_ON_DEMAND=false`
keeps the server from reading deeper relief from the provider when a test zooms in (no network).

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
the shape sets and the shapes under a point, the cluster layer's counts, the drawn base-map names.
`test:browser` runs both files; the budgets and the soak test take ~3 minutes of it (100 soak rounds).
`npm --prefix modules/web run test:perf` runs them alone; `HISTMERSION_SOAK_ROUNDS=20` shortens the
soak, `HISTMERSION_PERF_REPORT=1` prints every measured figure. `perf-baseline.mjs` measures the
same paths on any server, read-only (non-GET requests are refused) — the live baseline in the doc:

```bash
HISTMERSION_SERVER_URL=http://localhost:8000 node modules/web/test/perf-baseline.mjs --runs 3
```

`PLAYWRIGHT_CHROMIUM_EXECUTABLE` can point to an installed Chrome/Chromium binary instead of downloading
one. Stop the test server and run `just test-db-down` afterwards. The Node/Playwright dependency is
test-only and does not enter the Scala or native-image classpath.
