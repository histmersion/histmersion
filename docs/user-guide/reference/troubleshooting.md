# Troubleshooting

## First checks

```bash
histmersion health            # is the server up? database and schema version
histmersion about             # version, job queue, last backup, plugins, problems
just logs                     # the server log (also: just logs postgres, just logs converter)
just restart
```

The logs are in the data folder's `logs/` (`server.log`, `postgres.log`, `converter.log`, and
`relink.log` for plugin rebuilds).

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
| Map tiles do not load | offline or firewalled, on an online base map. Download the local map once (`histmersion basemap fetch --keep-all`) and choose Standard instead of Standard (online), or set `ui.base-map-source = "local"`: [Working offline](../how-to/offline.md). Leaflet itself is vendored, so the rest of the page works. |
| Server exits 3 at startup | a Flyway migration failed, or the database is unreachable. `just logs`; the message names the migration. |
| `git status` shows `agent-pid*/` | a forked `sbt run` was killed mid-write. Safe to delete; they are gitignored. |
