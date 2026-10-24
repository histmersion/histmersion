# Build and test speed (#3585, #3710, 2026-10-23; round 2 #3765, 2026-10-24)

`just test-it` took ~25 minutes and `just bundle` 7–9; every agent also turned sbt 2's cache off
(`Global / cacheStores := Seq.empty`) because the machine-wide cache had replayed results across git
worktrees. This note measures where the time went, fixes the cache properly, and records what changed
and what each change bought. All figures are from one machine (Apple M-series, 14 cores, 48 GB,
shared with other agents' builds — single runs, so ±10 % is noise), GraalVM CE 25.0.1 (the pin then; §5 moved it to 25.4), sbt 2.0.11.

## 1. Where the time went (before)

Measured on a clean checkout of master (`a6ebcd99`) with the workaround on, each phase timed from
timestamped output, peak RSS from a 1 Hz sample of the whole process tree (sbt, forks, Postgres,
native-image):

**`just test_db_port=55701 test-it` — 1 661 s (27.7 min), peak 5.8 GB**

| phase | s | peak RSS GB |
|---|---|---|
| sbt start, meta-build | 15 | 0.8 |
| compile: core 150 src (54 s), core tests, db, server 126 src (~40 s), plugins, cli | ~150 | 5.0 |
| Scala.js: web compile + `fullLinkJS` (server resource) + test `fastLinkJS` | ~23 | 5.0 |
| test DB start (initdb + createdb) and migrations | < 5 | — |
| tests: pluginApi 0, vaults 8, cli 6, core 7, web 1 | 22 | 5.4 |
| tests: db (59 suites, one at a time) | 114 | 5.7 |
| tests: **server (123 suites, one at a time)** | **1 450** | 5.7 |

Slowest suites: IngestHttpSuite 144 s, HardeningHttpSuite 134, PlacesHttpSuite 115, BundleHttpSuite 73,
HotReloadSuite 73, PassagesHttpSuite 71, OpsHttpSuite 71, WriteHttpSuite 57, BasemapHttpSuite 44,
AliasRuleHttpSuite 43, FormatHttpSuite 43, ZeroLengthUploadSuite 43, db.BundleSuite 34,
AcquireAutoMatchSuite 29, MediaSourcesSuite 29.

The server suites were 87 % of the run, and most of their time was **waiting**: every test server is
stopped with tapir's `NettySyncServer.stop`, which waits until every open channel has closed, up to
the 10 s graceful-shutdown timeout — and an *idle keep-alive* connection counts as open. The suites
talk to their server with `java.net.http.HttpClient`, which keeps connections alive, so nearly every
one of the ~130 test-server stops waited the full 10 s. HardeningHttpSuite's 10 tests are 10 servers:
134 s.

**`just bundle` — 535 s (8.9 min), peak 10.5 GB** (clean checkout, workaround on)

| phase | s | peak RSS GB |
|---|---|---|
| `postgres.sh` (copy the pixi PostgreSQL) | 10 | — |
| `converter.sh` (CPython, locked deps, docling models — network-bound) | 93 | — |
| sbt start | 7 | |
| compile (server + deps, Scala.js full link) | 79 | ~4 |
| **Sloth: 134 server jars, one JVM after another** | **124** | ~1 |
| native-image server | 108 | 10.5 |
| compile cli | 2 | |
| **Sloth: the CLI's 32 jars, patched again** | **50** | ~1 |
| native-image CLI | 53 | 7.3 |
| relink kit, hello plugin, bundle assembly | 9 | |

## 2. The cross-worktree cache — root cause and fix

sbt 2 caches every task result as an *action* entry (`ac/`, keyed by the hash of the task's inputs and
code) whose outputs live in a content-addressed store (`cas/`), and it **restores outputs as symlinks
into that store**. The default store, `~/Library/Caches/sbt/v2`, is one per machine: every checkout's
`target/out` linked into blobs that other sbt processes wrote, pruned or were writing (no write is
atomic; `cleanFull` anywhere deletes the whole store). The keys themselves are sound — sources are
hashed by content, paths relativised to `${BASE}`/`${OUT}` — so two worktrees with *equal* inputs
legitimately share an entry. What breaks is the store under them:

- an action entry whose blob is gone still counts as a hit (`DiskActionCacheStore.syncBlobs` skips a
  missing blob silently): the checkout gets the entry's *value* without its outputs — "compiled", no
  classes — and the compile that follows downstream fails;
- sbt 2 caches compile failures too (sbt/sbt#7662), under the downstream module's input key, so that
  failure is then replayed in every checkout with the same sources, long after the blob came back;
- this machine's store held 22 970 action entries whose blobs were missing (an agent's
  `dangling-ac-*.txt` from 2026-10-23), and four `ac.stale-*` directories moved aside by hand.

Reproduced twice. In a two-copy scratch project (one shared store): copy A builds, one CAS blob of A's
class directory is deleted, copy B (same sources) "compiles" with zero compilations and
`ClassNotFoundException: Hello`. And on this repository, `scripts/dev/cache-isolation-check.sh --shared`
(two temporary worktrees on one shared store) fails on all three replays: A replays B's compile, A
replays B's *cached compile failure*, and B gets A's entry with A's blob lost — no compile, no class.

**Fix:** each checkout has its own store, `target/sbt-cache` — `Global / localCacheDirectory` in
`build.sbt` (the build) and `project/plugins.sbt` (the meta-build), plus
`-Dsbt.global.localcache=<checkout>/target/sbt-cache` in the justfile's `sbt` and in
`scripts/bundle/server.sh` for sbt's own empty `global-plugins` project, which loads before either.
Nothing is shared, so nothing can be pruned, raced or replayed from elsewhere; within a checkout the
cache keeps working (rebuilds after `git switch` back and forth, re-runs after a `clean`).
`rm -rf target` empties it. The check `just cache-check` (≈5 min) makes two temporary worktrees with
this checkout's build definition, builds them alternately with different sources, and passes only if
each compiles its own source, a failure in one is never replayed in the other, each cache is inside
its checkout, and every restored output links into that checkout's own store (10/10 on this machine;
`--shared` shows the old behaviour: 3 failures).

What it costs: a fresh worktree no longer reuses another's compile results — it compiles once (the
cold column below), exactly what the workaround did on *every* run.

## 3. Tests

- **Shutdown grace** — `server.shutdown-grace-seconds` (default 10 s, as before;
  `HISTMERSION_SHUTDOWN_GRACE`), `0` = close the connections at once. The forked test JVMs set 0. Summed
  server suite time 1 450 s → 506 s.
- **Parallel forked groups, a database each** — db and server deal their suites into forked groups
  (db 2, server 5), longest first by `project/test-weights.tsv` (from a measured run,
  `scripts/dev/test-weights.py`), so the groups end together. Each group runs on its own database,
  `histmersion_<module>_<n>_test`, which `TestDb` creates *and migrates* on first use beside
  `HISTMERSION_TEST_DB_URL`; inside a group, one suite at a time, as before. The old reason for one test
  task at a time (`Tags.exclusive(Tags.Test)`, #1567) was the one shared database; with a database per
  group it is gone. At most `HISTMERSION_TEST_FORKS` (default 6) test JVMs run at once (db 1 GB, server
  2 GB heap each); `test-db` allows 300 connections. Two suites leaned on the old arrangement and were
  fixed: AliasRuleHttpSuite deleted from tables before anything of its own migrated (an earlier suite
  always had), NativeMetadataSuite looked for the module's metadata outside jars (a fork sees the module
  as its jar).
- **Cached results know about the database** — `Test / extraTestDigests` carries whether
  `HISTMERSION_TEST_DB_URL` was set, so a `sbt test` pass with the DB suites skipped is never replayed
  as a pass with them run. `test-it` and `test-quick` still run `testOnly *`.
- **`just test-quick [module…]`** — `scripts/dev/affected.py` maps the branch's changes (committed,
  staged, unstaged, untracked, against the merge base with master) to modules, adds their dependents
  (core → db, cli, web, server; db → server; plugin-api → vaults, server; build.sbt, project/ or an
  unknown path → everything) and runs `<module>/testOnly *`, with the throwaway Postgres only when db or
  server is among them. `just test-quick cli`: 14 s. `test-it` stays the gate.
- Not done: a template database cloned per group (`CREATE DATABASE … TEMPLATE`) — a group's migration
  runs once in ~2 s, the suites' own scratch databases migrate themselves; the sbt thin client
  (`sbt --client`) — it drops the caller's environment (the test DB URL), and sbt startup is now ~5 s.

## 4. Bundle

- **Sloth** (`build.sbt`, `slothPatch`): our own `histmersion-*` jars (Scala 3.8, nothing to patch) are
  copied; the Sloth runs go side by side (half the cores, ≤ 8), reading the *original* jars as the type
  hierarchy so no run reads a jar another is rewriting; a patched, verified jar is kept in
  `target/sloth-store` (keyed by the original's and Sloth's SHA-256), so the CLI reuses the server's.
  Server 124 s → 36 s, CLI 50 s → 4 s.
- native-image itself is unchanged (see §5).

## 5. GraalVM and native-image options

Server and CLI images built by `native-image` directly from the same Sloth-patched class path
(the bundle's relink kit), timed by native-image's own summary; size of the binary; startup = median
of 5 warm runs of `histmersion-server --self-check` and `histmersion --help`.

| GraalVM CE | server build | server peak RSS | server size | server startup | CLI build | CLI peak | CLI size | CLI startup |
|---|---|---|---|---|---|---|---|---|
| 25.0.1 (pinned until 2026-10-23) | 103 s / 95 s | 11.2 / 12.4 GB | 210 MB | 46 ms | 49 s | 8.7 GB | 87 MB | 10 ms |
| 25.0.2 | 92 s / 94 s | 13.1 / 12.0 GB | 210 MB | 46 ms | 50 s | 8.6 GB | 87 MB | 10 ms |
| 25.4 (25i4, JDK 25.0.4.1.1, "innovation") | refuses the class path¹; with the flag: 94 s / 103 s | 13.4 / 11.4 GB | **168 MB** | 43 ms | 53 s | 8.5 GB | **68 MB** | 9 ms |
| 25.4, adopted: tzdb jar excluded, no flag (`just bundle`, 2026-10-23) | 93 s | 11.7 GB | **168 MB** | 36 ms² | 48 s | 8.0 GB | **69 MB** | 8 ms² |

¹ `scala-java-time-tzdb_3-2.6.0.jar` (a transitive dependency) contains `java.time.zone.TzdbZoneRulesProvider`,
which 25.4's builder refuses on the class path unless `-H:+AllowDeprecatedBuilderClassesOnImageClasspath`.
It is not needed on the JVM at all: scribe's perfolation 1.3.0 depends on it (a Scala.js time-zone database),
and on the JVM java.base's own `java.time` always wins. build.sbt now excludes `scala-java-time-tzdb_3` from
every JVM class path; the binaries use the JDK's tzdb (604 zones, tzdb 2026b), which `histmersion-server
--self-check` checks (`TimeZoneCheck`, and `scripts/bundle/smoke.sh` under `TZ=Australia/Lord_Howe`).

² The adopted row is one run inside `just bundle` (288 s wall for the whole bundle, peak RSS 12.6 GB),
sizes as `ls -l` bytes / 2²⁰ (176 688 608 and 72 040 864 bytes); startup is the median of 5 runs after a
warm-up, timed around `subprocess.run` — not necessarily the same harness as the rows above.

Options on 25.0.1 (server unless noted):

| option | build | peak RSS | size | note |
|---|---|---|---|---|
| default | 95–103 s | 11–12 GB | 210 MB | |
| `--parallelism=7` | 101 s | 11.4 GB | 210 MB | no gain; the builder already uses all 14 threads at ~6× load |
| `-J-Xmx8g` | — | — | — | **OutOfMemoryError** after 43 s: the server image needs more than 8 GB of builder heap |
| `-Ob` (quick build) | 122 s | 12.3 GB | 222 MB | not faster here (CLI: 51 s, 93 MB); bigger, slightly slower binary |
| `--exact-reachability-metadata` | — | — | — | a strictness switch (missing metadata fails at run time), no build-time effect; not adopted |

**Decision (2026-10-23): adopted GraalVM CE 25.4** (JDK 25.0.4.1.1), pinned with its SHA-256 in
`scripts/bundle/graalvm-pins.txt`. Why: the binaries are 20 % smaller (server 210 → 168 MB, CLI 87 → 69 MB)
— less to download, install and relink — at the same build time and memory, and start slightly faster.
The class-path obstacle was fixed at its cause (the tzdb jar excluded), not with the deprecated-classes
flag. **The caveat:** 25.4 is the *innovation* line, which gets no fixes once the next innovation release is
out, so the pin moves with each new one as it lands (pins, docs and a re-checked metadata run in one
commit — AGENTS.md, "The JDK pin"); and it ships no macOS x64 build, so an Intel Mac falls back to the
prebuilt binary or JVM mode. sdkman has no download of it (`.sdkmanrc` names no JDK now;
`just toolchain install` provisions it). Earlier: 25.0.2 built the server 0–10 % faster with the same
binary — inside the run-to-run noise of a shared machine (25.0.1's own two runs differ by 8 %) — and was
not worth a pin move on its own. 25.4's builder also warned that `--no-fallback` has no effect any more (it sets the deprecated
`FallbackThreshold`) and that `--enable-url-protocols` should become reachability metadata; both were removed
on 2026-10-24 (#3752) — the `https` handler's constructor is in the server's reachability metadata already.
No native-image option sped the build.

## 6. Before and after

| | before (workaround on) | after |
|---|---|---|
| `test-it`, clean checkout | 1 661 s, peak 5.8 GB | 402 s (4 groups, unweighted), peak 9.5 GB |
| `test-it`, warm (same checkout, small change) | ≈1 661 s (no cache) | 195 s, peak 10.0 GB; final run 176 s, peak 10.7 GB |
| build phase only (`Test/fullClasspath`), clean | 97 s, peak 4.4 GB | 104 s, peak 4.5 GB |
| build phase, `target/out` deleted, cache warm | 97 s (no cache) | 34 s, peak 2.5 GB |
| build phase, nothing changed | — | 7.5 s |
| the old shared cache, clean / warm | 93 s / 25 s — as fast warm, but the store of §2 | — |
| `just bundle`, clean checkout | 535 s, peak 10.5 GB (of it `server.sh` 432 s) | 445 s, peak 12.1 GB (of it `server.sh` 287 s; `converter.sh`'s model download took 138 s against 93 s) |
| `just test-quick cli` | — | 14 s |
| `scripts/bundle/smoke.sh dist/histmersion` | — | 193 s, peak 14.3 GB (passed) |

Peak RSS of `test-it` went up from 5.8 to ~10 GB (sbt plus up to six test JVMs); `HISTMERSION_TEST_FORKS=2`
brings it back toward the old figure at the cost of time.

Why `test-it`'s build phase (~170–190 s) is longer than `Test/fullClasspath` alone (~100 s): `testOnly *`
compiles each module's tests when its test task starts, so compiles and the first suites interleave
less well. The tests themselves are now under three minutes; the cold compile is the largest part of a
fresh checkout's first run.

## 7. Round 2 (#3765, 2026-10-24)

Measured first: each forked suite's own time (munit's `Test run … finished … N s`) in a `just test-it` on
this machine (M4 Pro, 14 cores, JVM 25.0.1). The db and server suites summed **618 s** over 7 groups; the
server's twelve slowest each started a test server per test. Timestamps showed why that hurt: a start
took ~0.15 s, the **stop ~4 s** — with `server.shutdown-grace-seconds = 0` already set in the forks.

| idea (in order of payoff) | finding | outcome |
|---|---|---|
| one test server per suite instead of per test | the cost was the stop, not the start: tapir shuts its event loops and then its own executor down with netty's defaults, a 2 s quiet period each, whatever the grace. With grace 0, `Main.closeAtOnce` gives the listener event loops of the server's scope, shut down with no quiet period (stop 4 040 → 1 ms; the server's own grace path is unchanged). A shared server per suite (tried on HardeningHttpSuite: 45 → 20 s before the fix) then saves ~0.15 s a test | **done** as the stop fix; the per-suite server was not kept (≈7 s over 51 call sites, for tests that would no longer start clean) |
| data-isolated DB suites, parallel within a group | after the stop fix the groups end within ~25–35 s of each other inside a ~60 s run; the rest is sbt's own work and the pure suites beside them. Making ~180 suites free of whole-table rewrites and counts is weeks of audit for at most those seconds | not done — the scratch-database suites were sped up instead (below); a third db group measured 60 s vs 61 s (noise), so the groups stay 2 + 5 |
| the bundle and merge suites' scratch databases | each test made databases by running all 98 migrations. Now `DbSuite.ScratchDatabases` migrates one template per suite and copies it (`CREATE DATABASE … TEMPLATE`), then gives each copy its own instance id, `origin` and row uids, as a fresh migration would | **done**: BundleSuite + MergeSuite + MergeExtrasDbSuite 78.1 → 61.1 s (run alone, same DB) |
| the weakest hash/KDF cost in tests | nothing to lower: the code hashes only content (SHA-256, no cost parameter); the vaults run external tools that the tests fake with scripts; the scripts' GPG test keys are ed25519 with no passphrase (no S2K cost). VaultsSuite's 13 s is bash subprocesses and three 1 s timeout cases | nothing to change |
| seed a NEW worktree's `target/sbt-cache` by copy | the action entries are keyed by content with paths relative to the checkout, and the store holds no symbolic link — only `target/out` links (absolutely) into it. So a copy of `target/sbt-cache` alone, never `target/out`, restores into the new checkout's own copy: `scripts/dev/seed-cache.sh` (`just cache-seed`) refuses a source holding a link, refuses a checkout with a cache of its own, clones on APFS (`cp -c`, 0.8 s for 1 398 entries / 456 MB), and `--verify` checks after the build that every output links inside the checkout | **done**: a new worktree's `Test/fullClasspath` 96 s → 35 s (sbt 84 → 24 s, cache 39 → 98 %); `--verify`: 13 815 restored outputs, all inside it. The main worktree must have built once (its checkout had no cache on 2026-10-24) |
| a warm sbt server per worktree for `test-quick` | a cold `sbt --server -batch` invocation costs 5–6 s here (a two-second test task: 4.8–6.2 s wall). Solving the thin client's dropped environment (the test DB URL is read as a SETTING at load, `testDbOn`) for that is not worth the risk of a server running with a stale database URL | not done |

Also found: `just test-quick` did not run core's lint suites when only the web page changed —
`MapEngineBoundarySuite` and `StyleLintSuite` read the web sources and `PluralSuite` every module's
main Scala, so a change passed `test-quick` and failed `test-it`. `scripts/dev/affected.py` now adds
coreJVM (without its dependents) for such a change.

| | before | after |
|---|---|---|
| `just test-it`, warm checkout (same cache state) | 163 s (sbt 146 s) | 75 s (sbt 59 s) with the stop fix; 66 s (sbt 52 s) with the scratch-database templates too |
| db + server suites, summed | 618 s | 223 s |
| one server stop (grace 0) | 4 040 ms | 1 ms |
| a new worktree's first `Test/fullClasspath` | 96 s | 35 s, seeded (`just cache-seed`) |

## How it was measured

`measure.py` (a wrapper, not in the repository) ran each command with every output line prefixed by its
time since the start, sampled the process tree's RSS once a second (plus `target/test-pg`'s Postgres,
which `pg_ctl` daemonises), and `-Dsbt.traces=true` wrote sbt's task timings (`target/traces/build.trace`).
Suite times are munit's own `Test run … finished: … total N s` lines. Memory was checked before every
heavy step (free + inactive + speculative pages ≥ peak + 4 GB).
