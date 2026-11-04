# Applying a rule to a real codebase

Only apply a rule after it passes `run-rule.sh` on the samples. Work on a clean branch, so the rule's changes are a single reviewable diff.

## sbt

```scala
// project/plugins.sbt
addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.14.9")

// build.sbt
ThisBuild / semanticdbEnabled := true
ThisBuild / semanticdbVersion := scalafixSemanticdb.revision
```

```bash
sbt "scalafixAll file:rules/MyRule.scala"          # apply to all modules, main and test sources
sbt "scalafixAll --check file:rules/MyRule.scala"  # only report, e.g. in CI; fails if something would change
sbt "core/scalafix file:rules/MyRule.scala"        # one module, main sources only
```

- `scalafixAll` covers all configurations (`Compile`, `Test`, ...). `scalafix` alone only covers `Compile`.
- **Published rules** run with `dependency:`, e.g. the Scala 2.13 collection migration from scala-collection-compat:
  `sbt "scalafixAll dependency:Collection213Upgrade@org.scala-lang.modules::scala-collection-migrations:2.14.0"`.
  The `dependency:` syntax is an sbt-scalafix feature. The Scalafix CLI (and `run-rule.sh`) reports it as an unknown rule.
- Turning on SemanticDB triggers a full recompile the first time.
- Scala 3 modules work the same way. Rules that need synthetics will see none there ([semantic-api.md](semantic-api.md)).
- To explore an sbt codebase, run `TreeExplorer.scala` the same way ([exploring.md](exploring.md)).

To run a rule regularly instead of passing `file:` each time, list it in `.scalafix.conf` at the repository root:

```hocon
rules = [
  "file:rules/MyRule.scala"
]
```

Then `sbt scalafixAll` and `sbt "scalafixAll --check"` pick it up.

## scala-cli projects

```bash
scripts/run-rule.sh --apply rules/MyRule.scala <project-dir>
```

The script runs the rule on a copy, recompiles the result, and copies the changed files back only if it compiles.

## Other builds

Mill has the `mill-scalafix` plugin, and Gradle and Maven have scalafix plugins too. Each one needs SemanticDB enabled for semantic rules. Without a plugin, run the CLI directly. It needs the SemanticDB files, the classpath and the Scala version (see `run_scalafix` in `scripts/lib.sh`):

```bash
scalafix --rules file:rules/MyRule.scala --scala-version 2.13.16 \
  --sourceroot . --semanticdb-targetroots <dir-with-META-INF/semanticdb> --classpath <cp> <files>
```

## Review the result

1. **Compile and run the tests** of every changed module. Compile errors mean a shape the samples missed.
2. **Review the diff by shape.** Group the changes, e.g. `git diff -U0 | grep '^+' | sort | uniq -c | sort -rn`, and read one or two of each group carefully. The groups should match the inventory rows marked REWRITE. If a group doesn't match any row, there's a missing shape: go back to the samples.
3. **Check what was left alone.** Run `explore.sh` (or `TreeExplorer` in sbt) with the rule's symbol filter on the rewritten code. What's left should be exactly the LINT, UNCHANGED and suppressed cases.
4. **Count.** Tell the user how many places changed, how many were linted, and which shapes were left alone and why.

## Rolling out

- Prefer one commit per rule, with the rule file and the samples included, or referenced in the commit message, so reviewers can check the rule and not just the hundreds of changed lines.
- For very large diffs, apply the rule module by module and merge in several PRs.
- Keep the rule as a linter afterwards: `scalafixAll --check` in CI stops the old pattern from coming back.
