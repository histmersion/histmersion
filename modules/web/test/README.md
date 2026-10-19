# Browser regressions

These tests cover interactions that pure Scala.js tests cannot observe: Leaflet container resizing,
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
HISTMERSION_BASEMAP_STYLE=/tmp/histmersion-test-style.conf \
dist/histmersion/bin/histmersion-server
```

Then run:

```bash
HISTMERSION_SERVER_URL=http://127.0.0.1:18080 npm --prefix modules/web run test:browser
```

`HISTMERSION_BASEMAP_DIR` points the server at a small Aegean extract of the terrain base map
(`fixtures/basemap`, 9.4 MB with its physical names, sea floor and an Argolid focus region, its licences
in its README); without it the terrain tests are skipped. `HISTMERSION_BASEMAP_TERRAIN_ON_DEMAND=false`
keeps the server from reading deeper relief from the provider when a test zooms in (no network). The style hot-reload test also needs the server started with
`HISTMERSION_BASEMAP_STYLE=<a file>` and the tests run with `HISTMERSION_TEST_STYLE_FILE=<the same
file>` (it writes the file, and empties it afterwards).
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
