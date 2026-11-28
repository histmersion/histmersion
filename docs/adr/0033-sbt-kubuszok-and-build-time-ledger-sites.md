# 0033. The build follows sbt-kubuszok; the ledger sites are written by the build, once, before any test JVM

- Status: accepted (owner, 2026-11-28, issues #69 and #144)
- Date: 2026-11-28

## Context

The owner's other repositories build with his sbt plugin, sbt-kubuszok, and its convention (the `sbt-kubuszok-setup`
and `sbt-kubuszok-github-actions` skills of the kubuszok agent-skills marketplace). The owner asked (2026-11-21):
"it's my plugin and my convention", and on 2026-11-28 to adopt it now, together with the fix of the CI hang (#144).

The hang (#144): thread dumps from run 46165523925 (the watchdog of #145) showed two `scala-cli run --server=false`
launchers for Scalafix at once, one from each forked server test JVM, neither of which had started Scalafix after five
minutes. `LedgerSites.ensure()` generated the ledger sites lazily, once per JVM: every fork that ran
`SentenceLedgerSuite` or `ProblemCasesSuite` resolved and ran Scalafix itself, beside the other forks.

## Decision

**sbt-kubuszok 0.2.6** (the version for sbt 2.1) replaces sbt-scalajs and sbt-scalafmt in `project/plugins.sbt`; it
bundles them at the same versions.

- **Version from git.** `version` comes from `git describe` (sbt-git). With no tag in the repository it is
  `<commit>-SNAPSHOT`; the first version tag makes it `<tag>-<n>-g<sha>-SNAPSHOT`, and a tag on HEAD the release. The
  CI jobs that run sbt check out the full history, so every job of a run sees the same version. The application's own
  version (`api/instance/Version.scala`) is not the build's and does not change.
- **Nothing is published.** Every module keeps the plugin's default `projectType` (NonPublished). plugin-api and
  plugin-testkit become `ScalaLibrary` when snapshots are published (#80); no publishing secret is added before then.
- **Formatting.** The plugin formats on compile outside CI. CI's format job runs the scalafmt CLI over every file
  `.scalafmt.conf` covers, without sbt; `just fmt` and `just fmt-check` do the same. It covers what
  `scalafmtCheckAll` and `scalafmtSbtCheck` covered (the modules, `build.sbt`, `project/*.scala`): `tools/` and
  `.claude/` are excluded, because they hold Scalafix rules and scripts in Scala 2.13, which the scala3 dialect of
  `.scalafmt.conf` would rewrite into Scala 3 syntax.
- **Aliases.** `al.commandAliases()` on the root registers `ci-jvm-3`, `ci-js-3`, `test-jvm-3` and `test-js-3`
  (`testFull`, which sbt 2 never replays). `HistmersionAliases` adds the plain projects, which the plugin's `Aliases`
  (project matrices only) does not see, and leaves out `clean` (it would empty the per-checkout disk cache) and
  coverage.
- **Kept as they were**: Linux-only CI (0031), Scala.js, the `nativeImage` task, the per-checkout sbt disk cache, the
  lint annotations module on every class path, the CI watchdog and thread dumps, the `-Xmacro-settings` compiler
  options, the sharded test jobs.

**The ledger sites are written by the build** (`server/ledgerSites`, project/LedgerSitesGen.scala): Scalafix over every
module's SemanticDB, in the sbt JVM, before the server's suites are listed (`Test / definedTests` waits for it). It runs
once per sbt invocation and only when its key changed: the digest of every module's sources and of the rule files, and
the tool versions. The key is written beside the outputs after a successful run and put on the server's test class path;
the suites only read the outputs, and fail when the key is not the one of the sources they were built from. A run that
outlives `HISTMERSION_LEDGER_SITES_TIMEOUT` seconds (default 900) is killed with its child processes, and the error
carries the last 40 lines of its output. On CI the build job writes them and hands `target/guidelines` to the shards
with the compile output; the shards find them current and have no scala-cli at all, so no test resolves a dependency.

## Consequences

- No two Scalafix launchers ever run at once in one sbt run, and a stall shows its output instead of a silent 900 s.
- A local server test run after a source change pays one Scalafix run (about 30 s) before the suites start, also when
  the selected suites do not read the ledger sites.
- Loading the build in a checkout without tags prints git's `No names found, cannot describe anything` twice; the first
  version tag ends it.
