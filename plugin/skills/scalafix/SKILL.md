---
name: scalafix
description: Write, test and apply Scalafix rules (automatic rewrites and linters) for Scala code migrations. Use when the user wants to migrate or refactor many usages of an API across a codebase, replace a deprecated or harmful call, inspect Scalameta trees, or create, debug or run a Scalafix rule.
---

# Scalafix rules

Scalafix rewrites Scala code using rules written in Scala. A rule walks the Scalameta syntax tree of each file, asks SemanticDB what the names refer to, and returns **patches**: text edits or lint diagnostics.

Rules fail in practice for three reasons, and the workflow below guards against each one:

1. **The tree doesn't look the way you assumed.** `a.b(c)`, `a b c`, `a.b { ... }` and `for (x <- a.b) ...` are four different trees. So **look at real trees before writing a matcher.**
2. **A name matches the wrong thing.** `get` on `Option`, on `Map`, and on your own class are three different symbols. So **match symbols, not text.**
3. **The rewrite looks right but is wrong.** It doesn't compile, it changes behaviour, or it breaks formatting. So **test against an expected output and compile the result.**

## The round trip

Work through these steps in order, and loop back whenever a later step finds something new. Never declare a rule done before `run-rule.sh` exits with 0 (every check prints OK).

### 0. Is there a rule already?

Writing a rule is the last resort. Check first:

- **Built-in rules** ship with Scalafix, e.g. `RemoveUnused`, `ProcedureSyntax`, `ExplicitResultTypes`, `OrganizeImports`, `DisableSyntax`, `NoValInForComprehension`, `RedundantSyntax`, `LeakingImplicitClassVal`: <https://scalacenter.github.io/scalafix/docs/rules/overview.html>. Try one on the samples with `run-rule.sh <RuleName> <input-dir> <expected-dir>`.
- **Published rules**: the [community rules](https://scalacenter.github.io/scalafix/docs/rules/community-rules.html), and the migration guide of the library or Scala version you migrate to. Example: Scala 2.12 → 2.13 collections have `Collection213Upgrade` from scala-collection-compat. They run through sbt-scalafix with `dependency:` ([references/applying.md](references/applying.md)), not through `run-rule.sh`.

If a rule covers part of the migration, run it first and write your own rule only for what's left. Tell the user what you found.

### 1. Explore: what do the real occurrences look like?

Find the occurrences in the real codebase (`rg`, `grep`), then get their exact trees and symbols:

```bash
<skill-dir>/scripts/explore.sh <project-dir> '<name-regex>' ['<symbol-regex>']
<skill-dir>/scripts/tree-of.sh '<snippet>'          # quick syntax-only check of one snippet
```

`explore.sh` compiles the project with SemanticDB and groups every occurrence of the name by **shape**: the tree around it, with unrelated subtrees abstracted away. For each shape you get the locations, an example with its full tree, the symbols with their signatures, and compiler synthetics (implicits, desugared for-comprehensions). Then write a **shape inventory**: one line per shape with the action it needs (REWRITE, LINT, UNCHANGED or DECIDE).
→ [references/exploring.md](references/exploring.md): finding occurrences, reading the output, building the inventory.

### 2. Samples: input and expected output

Build a scala-cli **input** project with at least one sample per shape, copied from real code, plus look-alikes that must stay unchanged. Mark every sample that should get a lint with `// assert: MyRule` at the end of the line where the lint is reported. Then create the **expected** output: a copy with each sample written the way the rule should leave it. The user owns the expected output. If it's missing, propose it and get it confirmed. Never change the input or the expected output to make a test pass, not even a comment. If you think one of them is wrong, say so and ask. Compile the expected output too (`scala-cli compile <expected-dir>`): if it doesn't compile, no rule can be right.
→ [references/testing.md](references/testing.md)

### 3. Write the rule

Write one file, e.g. `rules/MyRule.scala`. The class name must equal the rule name. No build project is needed: Scalafix compiles `file:` rules itself. Write one matcher per inventory shape, and use the trees from step 1, not guesses.
→ [references/scalameta-trees.md](references/scalameta-trees.md): tree nodes, fields, navigation, tokens, Scala 3.
→ [references/semantic-api.md](references/semantic-api.md): symbols, `SymbolMatcher`, signatures and types, synthetics.
→ [references/patches.md](references/patches.md): patch API, atomic patches, imports, lints, configuration.

### 4. Test against the samples

```bash
<skill-dir>/scripts/run-rule.sh rules/MyRule.scala <input-dir> <expected-dir>
```

The script copies the input (the original is never modified), compiles it with SemanticDB, runs the rule, shows the diff, compares it with the expected output, and compiles the result with the project's own compiler options. It also checks the reported lints against the `// assert:` markers: a marked line without a lint, or a lint on an unmarked line, is a failure. Iterate until every check prints OK and the script exits with 0. A rewrite that doesn't compile is a failure, even if the text looks right. When a fix isn't obvious, check [references/pitfalls.md](references/pitfalls.md).

### 5. Apply, and close the loop

Run the rule on the real codebase (→ [references/applying.md](references/applying.md)). Then compare what it did with the inventory from step 1:

- a changed place that matches no inventory shape, or
- an occurrence that `explore.sh` lists but the rule left alone, or
- a compile or test failure

means a shape is missing. Add a sample for it, get its expected output confirmed, and go back to step 3. Report to the user how many places changed, how many were linted, and which shapes were left alone and why.

## Scripts

| Script | Use |
|---|---|
| `scripts/explore.sh <dir> <name-regex> [symbol-regex]` | Trees, symbols and synthetics of every occurrence in a scala-cli project, grouped by shape |
| `scripts/tree-of.sh [--scala3] [--tokens] '<code>'` | Tree (and tokens) of a snippet. Syntax only, no compilation needed |
| `scripts/run-rule.sh [--apply] <Rule.scala or built-in rule> <dir> [expected-dir]` | Run a rule on a copy, check the lints against `// assert:` markers, compare with the expected output, recompile |
| `scripts/TreeExplorer.scala` | The read-only rule behind `explore.sh`. It also runs in sbt, see [exploring.md](references/exploring.md) |

Prerequisites: `scala-cli`. The scripts use `scalafix` from the PATH if it's there (`cs install scalafix`), and otherwise launch it through scala-cli. The Scala version comes from the `//> using scala` directive of the project. The first run downloads dependencies and takes about a minute.

## Skeleton

```scala
import scalafix.v1._
import scala.meta._

class MyRule extends SemanticRule("MyRule") {
  private val target = SymbolMatcher.normalized("scala.Option.get")

  override def fix(implicit doc: SemanticDocument): Patch =
    doc.tree.collect {
      case sel @ Term.Select(_, name) if target.matches(name) =>
        Patch.lint(Diagnostic("", "Explain the problem and the fix.", sel.pos))
    }.asPatch
}
```

Use a `SemanticRule` whenever it matters *which* method or type something is, e.g. `Map#mapValues` (deprecated) vs `MapView#mapValues` (not deprecated). That covers almost every migration. A `SyntacticRule` only sees text and names.

## Rules of thumb

- Match on the symbol of the selected **name** (`target.matches(name)`), not on the text or the whole expression.
- Read fields (`app.fun`, `app.argClause.values`), not positional extractors like `Term.Apply(fun, args)`, which changed between Scalameta versions. `Term.Select(qual, name)` is fine.
- Prefer small patches (rename a name, remove a few tokens) over reprinting expressions. They keep the user's formatting and comments.
- Make every rewrite `.atomic`, so that `// scalafix:ok` and `@SuppressWarnings` can turn it off.
- When a mechanical fix isn't safe, lint instead of rewriting, and say why in the message.
