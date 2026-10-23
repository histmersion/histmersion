# Troubleshooting

## First checks

```bash
histmersion health            # is the server up? database and schema version
histmersion about             # version, job queue, last backup, storage on disk, plugins, problems
just logs                     # the server log (also: just logs postgres, just logs converter)
just restart
```

The logs are in the data folder's `logs/` (`server.log`, `postgres.log`, `converter.log`, and
`relink.log` for plugin rebuilds).

## Where the disk space goes

`histmersion about` (and the About window of the web page) ends with a **Storage** section: every
place Histmersion keeps data, its path and its size, and the free space on each disk.

| location | what it is | how to make it smaller |
|---|---|---|
| database (Postgres) | the corpus: sources and their text, findings, figures, articles; the largest tables are named | `histmersion maintenance vacuum`; `agents.keep-messages-days` trims the agents' message traces |
| database files (bundled Postgres) | the same database as files, `<data>/pg` — the database is part of it | as above |
| backups | `backups.dir`: one full dump per backup | lower `backups.keep`; the next backup deletes the older archives |
| stored originals (uploads) | `converter.spool-dir`: every uploaded or fetched PDF/EPUB, by checksum | — (deleting a source deletes its original) |
| bundle scratch · acquisition scratch | `bundles.dir`, `acquire.spool-dir` | safe to empty while no bundle export/import or acquisition runs |
| gazetteer extracts (cache) · gazetteer folder | the pinned place extracts (`~/.cache/histmersion/gazetteer`) and the LPF drop folder (`<data>/gazetteer`) | the cached extracts can be deleted; `histmersion places load` fetches them again |
| base map data (cache) | `basemap.dir`: the vector, relief and names files, one by one | `histmersion basemap rm` (one layer: `--layer`) |
| cache: … | any other folder in `~/.cache/histmersion` | a cache: safe to delete |
| install · OCR converter · plugin relink kit | the bundle itself (binaries, Postgres, the converter's Python and models) | — |
| GraalVM toolchain | `<data>/toolchain`, only after `histmersion-toolchain install` | `histmersion-toolchain remove` (needed again only to relink plugins) |
| plugins · agent definitions · logs | `<data>/plugins`, the generated agent trees, `<data>/logs` | delete old log files |
| originals folder (this machine) | `cli.calibre.originals-dir`, shown by the CLI when the server runs on the same machine | — |

The sizes are measured in the background at most once a minute (`storage.ttl-seconds`) and the
answer says how long ago; the first About after a start may say *measuring…* for a moment (the web
window asks again by itself). A very large folder is measured up to a limit (`storage.max-millis`,
`storage.max-entries`) and then shown as *at least* that much. Links inside a folder are not
followed, so nothing is counted twice. When a disk has less than `storage.low-free-gb` (10 GB) free,
About says so and names the biggest locations on it that can be made smaller, with how.

## Known problems

- **`WARNING: A terminally deprecated method in sun.misc.Unsafe has been called … scala.runtime.LazyVals$`** on
  stderr from the native binaries (JDK 25, JEP 498). Cosmetic. Scala 3.8 itself compiles lazy vals to
  `VarHandle`s (our own jars are clean); the caller is the *old* `LazyVals.getOffset*` path inside
  dependencies still published from a pre-3.8 compiler — circe-core, cats-core, tapir-core, sttp core,
  decline, sconfig, magnolia (`unzip -p <jar> … | grep getOffset` to check). It goes away as those
  libraries re-publish on 3.8+. **Fix: [VirtusLab Sloth](https://github.com/VirtusLab/sloth)** rewrites
  that bytecode to the 3.8 `VarHandle` form; `nativeImage` runs it over a copy of every classpath jar
  (`target/sloth/`) when a Sloth CLI jar is found — `SLOTH_JAR` first, else a Sloth checkout built
  with `sbt cli/assembly` next to this checkout (`<repo>/../sloth`), else, when building from a
  `git worktree`, one next to the MAIN worktree (found with `git rev-parse --git-common-dir`; see
  `project/SlothLookup.scala`) — and logs a warning and builds unpatched otherwise. Sloth is
  alpha; the patched CLI/server pass the same tests and run clean on JDK 25. Without it,
  `-Dsun.misc.unsafe.memory.access=allow` on the binary's command line does not help (the property is
  read before `main`); stdout is unaffected either way.

| symptom | cause and fix |
|---|---|
| `cannot reach Histmersion at http://localhost:8000 — is the server running?` | the server is down or on another port. `just health`, `just logs`, `just restart`. Check `HISTMERSION_PORT`/`HISTMERSION_SERVER_URL` agree. |
| `NoClassDefFoundError` / `IncompatibleClassChangeError` on `ox.DoNothingScope` right after "Histmersion listening" | an old tapir (1.11.x → ox 0.5.x) on JDK 25. The pin is tapir 1.13.31 / ox 1.0.2 — `sbt update` after a rebase, or `sdk env install` if the JDK drifted. |
| `not a directory on the server: /some/path` from `seed` | the CLI reads a path that exists on this machine itself and asks the server only otherwise — so the path exists on neither. Check it; for plain `histmersion seed`, set `HISTMERSION_CORPUS_DIR` (`.env`) and re-run `just install`. |
| Native binary dies on a missing class or resource | metadata is stale. Run the app once under sbt (`sbt server/run`), commit the changed `META-INF/native-image/reachability-metadata.json`, rebuild. |
| `sbt test` "passes" without running | sbt 2 caches test results. `sbt "testOnly *"`. |
| An env var is ignored by sbt | the sbt 2 thin client shares a server and drops the environment. `sbt --server -batch <task>`. |
| Map tiles do not load | offline or firewalled, on an online base map. Download the local maps once (`histmersion basemap fetch --keep-all`) and choose Readable instead of Standard (online), or set `ui.base-map-source = "local"`: [Working offline](../how-to/offline.md). The map library itself is vendored, so the rest of the page works. |
| The map is blank, or says it draws without a GPU | the browser has no WebGL, or draws it in software (a virtual machine, a remote desktop, hardware acceleration off). Switch hardware acceleration on in the browser's settings; without it Readable and Physical are drawn without their relief: [Base maps](../how-to/base-maps.md#fonts-credits-and-computers-without-a-graphics-card) |
| Server exits 3 at startup | a Flyway migration failed, or the database is unreachable. `just logs`; the message names the migration. |
| `git status` shows `agent-pid*/` | a forked `sbt run` was killed mid-write. Safe to delete; they are gitignored. |
