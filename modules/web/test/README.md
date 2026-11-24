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
platform. Without a GPU (CI's Linux runners: Chromium answers "SwiftShader") the tests of the GPU path — relief,
contours, the sea's depth; `withPage(…, { gpu: true })` in `browser.test.mjs` — tell their pages a GPU renderer
(`engine.mjs` `standInGpu`), so that path is tested there too, drawn by the CPU; every other test takes the no-GPU
path, as a person's browser without a GPU would (the GPU path drawn by the CPU makes every frame several times slower,
and the motion tests count frames). The first line of the run names the renderer. The tests reach the map through `window.histmersionMap` (`web/MapHandle.scala`): the view,
the shape sets and the shapes under a point, the cluster layer's counts, the drawn base-map names, a
base-map layer's visibility and how many of its features are drawn (`layerVisibility`, `layerDrawn`: the
sea's depth, the contours inside the Argolid focus region).
The map's point markers are reached the same way, never through their elements, so the tests hold whatever
draws them (row 31 moves them from DOM elements to a canvas): `markers(spec)` lists each drawn marker — its pane,
centre (`x`/`y` in the map, `clientX`/`clientY` for the pointer), box, stacking, flags (its kind and look:
`hd-cluster`, `hd-ev--approximate`, `hd-cluster-ghost`, …), id, citekey, accessible name, data, bubble count,
fill, edge, opacity, and with `detail: true` its drawn shape (outline, glyph, a bubble's rings and count);
`markerAt([x, y])` is the marker the pointer meets there, `spots(panes)` the markers' centres frame by frame,
`markersIn(panes)` how many are drawn, `markersSettled()` whether every marker of the view is made (the map still,
its tiles in and read, no layout queued: the cluster layer makes its markers a slice a frame, so on slow frames a
few hundred take many frames; wait on it before counting or listing the markers), `spiderLegs()` the legs of a
fanned-out bubble. A spec picks markers by
`flag` and `not` (a name or a list), `id`, `ck`, `pane`, `ghost`, `offscreen` and `data`; `markers.mjs` holds
the helpers that wait for, hover and click them with the real pointer.
`test:browser` runs the three files one at a time (`--test-concurrency=1`): side by side they wrote the same
settings file at once and measured the budgets while two other browsers were busy; the budgets and the soak
test take ~3 minutes of it (100 soak rounds). In CI (ubuntu-24.04, docs/adr/0028-ci-on-linux-bundles-on-demand.md)
the `budgets: …` tests run in their own job (`budgets`, `--test-name-pattern='^budgets: '`), the rest in four
parallel `browser` jobs (`--test-skip-pattern`, the same pattern). Each of those loads every file with
`--import ./test/shard-preload.mjs`, which registers only its shard's tests (`HISTMERSION_TEST_SHARD=<i>/<n>`;
`HISTMERSION_TEST_SHARD_PINS=perf.test.mjs=1` sends that file whole to shard 1, every other test goes by the FNV-1a
hash of its name to one of the other shards, `test-shards.mjs`), registers a file's hooks only when the file has a
test in the shard, and writes every test it saw with its shard to `HISTMERSION_TEST_SHARD_MANIFEST`. The
`browser-shards` job (`shards-check.mjs`) then checks that every listed test but the budgets ran exactly once, in its
own shard, and that none was skipped. To run one shard locally:

```bash
HISTMERSION_TEST_SHARD=2/4 HISTMERSION_TEST_SHARD_PINS=perf.test.mjs=1 HISTMERSION_SERVER_URL=http://127.0.0.1:18080 \
  node --import ./test/shard-preload.mjs --test --test-concurrency=1 --test-skip-pattern='^budgets: ' \
  test/browser.test.mjs test/marks.test.mjs test/perf.test.mjs     # from modules/web
```

**The absolute budgets are held on a real Mac** (owner decision 2026-11-22), at full speed and with the CPU
throttled 3× (`HISTMERSION_CPU_THROTTLE=3`, Chromium's own throttling, printed first), against a test server started as
above:

```bash
HISTMERSION_SERVER_URL=http://127.0.0.1:18080 npm --prefix modules/web run budgets:local
```

It runs the `budgets: …` tests twice and fails when either run does; rows 30 and 31 of the plan are judged on these
figures.

**In CI the budgets compare a change with its merge base** (owner decision 2026-11-22). GitHub's hosted runner is no
user's device (a shared 4-core Linux virtual machine without a GPU: WebGL is drawn by SwiftShader, the map takes its
no-GPU path and the vector maps' `…Software` budgets), so `budgets` holds no absolute limit there. It builds and starts the merge base's server beside this
change's, waits until neither server runs or queues a job (a fresh database imports the bundled gazetteer for about
five minutes after start: measured during it, the first round of both sides ran beside two imports), runs the budget
tests against each three times, alternating, each side with its own test code (the merge base's from its worktree,
`--base-web`), and when a figure comes out past the noise, two more rounds of each side decide (`--confirm 2`: three
runs of one side above three of the other happen by chance one time in twenty, five above five one time in 252; a
longest task is 0 on a run without any long task and 50 ms or more on one with one, so such a figure separates by
chance more often than its spread suggests) (`budgets-ab.mjs`; `HISTMERSION_BUDGETS_RECORD`
makes `perf.test.mjs` write every figure instead of holding it), and compares figure by figure (`budgets-compare.mjs`,
`budget-figures.mjs`): a main-thread figure is a regression when every run of the change is worse than every run of
the base and the medians differ by more than the runs' own spread (the larger of the two sides') and a floor (20 ms;
3 MB of heap; 5 % of the DOM nodes and listeners; nothing for the counts that must not move, such as `notAnimated`).
Frame-bound figures (below) are printed, not compared (`budget-figures.mjs` `FRAME_BOUND`). A budget run of either side that fails, skips a test or runs
none fails the job too (the base's with its own tests: a real problem of the base). When the change alters the code
that measures (`perf.test.mjs` or any module it imports, compared file by file), no figure is compared: each is
printed as "measurement changed: not compared", with the files and a warning in the summary — neither a pass nor a
failure. `HISTMERSION_BUDGETS_INJECT_ZOOM_MS=60` in the job's step adds 60 ms of work to every frame
the map draws during a zoom (zoomstart to zoomend) in the change's runs (`budgets-inject.mjs`), so a zoom's longest
task grows by 60 ms however long it already was; it adds no listener and touches nothing outside a zoom. It is the
check that the job turns red. Measured here (M4 Pro, the same server on both sides, three rounds): the base against
itself 73 figures, no regression; with the injection exactly the six zooms' longest tasks (0 → 67–84 ms) and nothing
else.

**Frame-bound figures** are the ones a slow painter moves however idle the page's main thread is: dropped frames
(`droppedPct`, `stillFramesPct`) and every input-to-next-paint figure (`inputP95`, the hover cards'
`shownMax`/`filledMax`/`warmMax`, the typing's `keyP95`/`keyMax`/`suggestMs`/`previewToListMs`, the article's
`windowMs`/`contentMs`, the release's `releaseToListMs`, Library's `clickMs`/`keyMs`/`pageMs`). The CI comparison
prints them and never fails on them; a local run of the budgets holds every figure. `budgets` is not a required
check; its colour is the comparison's verdict.

The functional tests' click checks (a chip of the approvals, runs and jobs windows, the news mark in Research's side
navigation, the theme button) are measured twice — the click's own work (its handlers and the microtasks after them,
the theme's restyle) and the click to the next frame — and both are held under 100 ms everywhere, the CI runners
included. (On the hosted macOS runner, whose paravirtual GPU painted about 8 frames a second, the second was printed
rather than held; that runner is no longer used.)
`npm --prefix modules/web run test:perf` runs them alone; `HISTMERSION_SOAK_ROUNDS=20` shortens the
soak, `HISTMERSION_PERF_REPORT=1` prints every measured figure. `perf-baseline.mjs` measures the
same paths on any server, read-only (non-GET requests are refused) — the live baseline in the doc:

```bash
HISTMERSION_SERVER_URL=http://localhost:8000 node modules/web/test/perf-baseline.mjs --runs 3
```

`PLAYWRIGHT_CHROMIUM_EXECUTABLE` can point to an installed Chrome/Chromium binary instead of downloading
one. Stop the test server and run `just test-db-down` afterwards. The Node/Playwright dependency is
test-only and does not enter the Scala or native-image classpath.
