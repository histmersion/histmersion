# 0012. Developer build and test tooling

- Status: accepted
- Date: 2026-11-21

## Context

The build is sbt 2 with a content-addressed disk cache; several checkouts (worktrees) build side by side. The CI splits
the suites over shards, some of which need PostgreSQL. `just test-quick` tests only what a change affects. The licence
report must classify every shipped library against the AGPL. Performance drivers run against copies of a real corpus.

## Decision

**sbt**
- The recipes run sbt with `--server -batch`, a dedicated server per invocation: sbt 2's thin client shares one server
  and drops the caller's environment (`HISTMERSION_TEST_DB_URL` among it). It runs on the pinned GraalVM CE (ADR 0001),
  so tests, the tracing agent and the native images see the JDK the binaries are built with.
- **Each checkout's disk cache is its own** (`Global / localCacheDirectory = <checkout>/target/sbt-cache`, and
  `-Dsbt.global.localcache` for sbt's global-plugins project). Two checkouts never replay each other's entries.
  `scripts/dev/cache-isolation-check.sh` proves it with two temporary worktrees and a probe source. With `--shared` it
  shows the failure mode of a shared cache: a blob lost from the CAS while its action entry stays, so the other checkout
  gets a "hit" and no classes.
- `seed-cache.sh` clones a cache with copy-on-write on APFS, and copies it elsewhere.

**Suites and shards**
- `classify.py` marks a suite IT when it reaches the test database: a file with a marker does (markers are looked for in
  strings too, since an environment variable is a string), and so does a file that uses a name a reaching file defines,
  computed as a fixed point. Only db and server can reach the database; an IT suite elsewhere is a misreading.
- `plan.py` deals suites into shards: the heaviest first, each to the lightest bucket, ties broken by name, which is the
  same rule as `project/TestGroups.split`. Other projects are dealt whole, because a Scala.js project links its tests
  once. A suite of no class is dealt nowhere, and `check.py` fails the run on it after the shards have run the rest.
- `project/test-weights.tsv` (refreshed by `test-weights.py`) balances the forked groups. A suite missing there weighs
  1 s, and stale numbers only cost balance, never correctness.
- `affected.py` maps changed paths to modules. `web → server` is a build edge only: no server suite tests the Scala.js
  code. The lint suites (api, config, core) read other modules' sources, so they run when those change, but their
  dependents do not. A suite that only reads a changed file is tested; its dependents are not. The repository files
  the suites read (agents, skills, reference pages, seed data) map to the modules that read them.

**Licences** (`third_party_licences.py`, `just licences`)
- Every shipped library's licence is mapped to an SPDX id and classed against AGPL-3.0: compatible, `INCOMPATIBLE`
  (GPL-2.0-only, EPL-1.0, CDDL, MPL-1.1, OpenSSL, proprietary, …), or to be checked. A library marked incompatible does
  not go in.
- The class paths come from `sbt show …/fullClasspath`, because sbt 2.1's `export` prints nothing when the task's
  result comes from the cache. A local version such as `2.13.0+cpu` (the PyTorch CPU index) is looked up as the PyPI
  release it was built from.

**Performance drivers**
- The live-volume drivers record digests, counts and timings only. A value the corpus holds (a tag, a place id, a
  citekey, a slug) is looked up at run time and recorded under a symbolic name, never itself.

## Consequences

- Worktrees can build in parallel without corrupting each other's results.
- A mis-classified suite fails CI rather than silently skipping.

## Code

`scripts/dev/` (`sbt.sh`, `cache-isolation-check.sh`, `seed-cache.sh`, `affected.py`, `test-weights.py`,
`third_party_licences.py`), `scripts/ci/` (`classify.py`, `plan.py`, `check.py`), `scripts/perf/*_live.py`,
`project/TestGroups.scala`.
