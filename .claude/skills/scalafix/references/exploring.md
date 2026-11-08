# Exploring real occurrences

Goal: before writing a matcher, know **every shape** the pattern takes in the real code, and what each name in it refers to. Most rule bugs come from a shape nobody looked at.

## 1. Find candidates

Start wide, with text search over the real codebase:

```bash
rg -n '\.mapValues\b' --type scala         # dotted calls
rg -n '\bmapValues\b' --type scala | wc -l # also infix calls, imports, definitions
```

Text search finds candidates. It can't tell you whether a `mapValues` is `Map#mapValues`, `MapView#mapValues` or someone's own method. That's what SemanticDB is for.

## 2. Look at their trees and symbols

### On a scala-cli project: `explore.sh`

```bash
scripts/explore.sh <project-dir> '<name-regex>' ['<symbol-regex>']
scripts/explore.sh samples 'mapValues'                         # every `mapValues`
scripts/explore.sh samples 'mapValues' '^scala/collection/MapOps#'
```

- `<name-regex>` must match the whole name (`mapValues|filterKeys`, `get`).
- `<symbol-regex>` is searched in the SemanticDB symbol (e.g. `scala/collection/MapOps#mapValues().`). Leave it out at first: the look-alikes with other symbols are exactly the cases the rule must leave alone.
- `EXPLORE_UP=6` includes more enclosing calls in the context (default 4). Use it when the interesting part is further out, e.g. `m.mapValues(f).toMap.getOrElse(k, 0)`.
- `EXPLORE_EXAMPLES=3` prints more examples per shape.

Definitions (`def mapValues = ...`) are skipped. Only references are reported.

### Output

```
=== Shape 4: 1 occurrence(s)
symbol: scala/collection/MapOps#mapValues().
shape:  Term.Select(Term.Apply(Term.Select(<Term.ApplyInfix>, Term.Name("mapValues")), Term.ArgClause(<Term.Name>)), Term.Name("toMap"))
at:     Prices.scala:11
--- example at Prices.scala:11
  code: (prices ++ prices).mapValues(withVat).toMap
  tree: Term.Select(
          Term.Apply(
            Term.Select(
              Term.ApplyInfix(...),
              Term.Name("mapValues")),
            Term.ArgClause(List(Term.Name("withVat")), None)),
          Term.Name("toMap"))
  symbol prices: demo/Prices.prices.  --  : Map[String,Int]
  symbol mapValues: scala/collection/MapOps#mapValues().  --  [W](f: Function1[V,W]): MapView[K,W]
  symbol withVat: demo/Prices.withVat().  --  (p: Int): Int
  synthetic for `(prices ++ prices).mapValues`: *[Int]
```

- **symbol**: what the name refers to. Occurrences with the same shape but different symbols are listed as separate shapes.
- **shape**: the tree from the outermost enclosing call/select/for down to the name. Subtrees off that path are abstracted to `<Type>`, except member names and the kinds of the arguments. That's usually what decides the rewrite: a lambda, a partial function and a method value need different handling.
- **at**: all locations with this shape. A shape with many occurrences deserves the most care. One with a single occurrence may be better linted than rewritten.
- **tree**: the full `structure` of one example. Copy node types and field layout from here, not from memory.
- **symbol lines**: every name in the example with its SemanticDB symbol and signature. Use them for `SymbolMatcher`s and type checks.
- **synthetic**: what the compiler inserted, as in `*(Int)` (an implicit argument list, here `Ordering.Int`), `*[Int]` (inferred type arguments), `*.apply`, `augmentString(*)` (an implicit conversion), `orig(xs).foreach[Unit](...)` (a desugared for-comprehension). Synthetics are available for Scala 2 only.

### On an sbt project: `TreeExplorer` directly

`TreeExplorer.scala` is a normal read-only rule configured with environment variables, so it runs in any build with sbt-scalafix and SemanticDB enabled (see [applying.md](applying.md)):

```bash
EXPLORE_NAME='mapValues' EXPLORE_SYMBOL='^scala/collection/MapOps#' \
  sbt "scalafixAll file:<skill-dir>/scripts/TreeExplorer.scala"
```

Start a fresh sbt, not a running sbt server or `sbt --client`, because the rule reads the environment of the JVM it runs in. The report is printed once per sub-project and configuration. Locations are absolute paths unless you also set `EXPLORE_ROOT` to the repository root.

### A single snippet: `tree-of.sh`

```bash
scripts/tree-of.sh 'prices mapValues { p => p * 2 }'
scripts/tree-of.sh --scala3 'xs.map: x =>
  x + 1'
scripts/tree-of.sh --tokens '(a ++ b).mapValues(f)'
```

It parses only, with no types or symbols, so it's fast and works on code that doesn't compile. Use it to check what a variant looks like, or what the rewritten code should parse to. `--tokens` lists the tokens for token-level patches.

## 3. Write the shape inventory

Before writing the rule, write down one line per shape. Keep it in your notes or show it to the user:

Example for the deprecated `Map#mapValues` (Scala 2.13), which should become `m.view.mapValues(f).toMap`:

| # | Shape (simplified) | Example | Count | Action |
|---|---|---|---|---|
| 1 | `<recv>.mapValues(<lambda or name>)` | `prices.mapValues(_ * 2)` | 14 | REWRITE to `prices.view.mapValues(_ * 2).toMap` |
| 2 | `<recv> mapValues <lambda>` (infix) | `m mapValues f` | 1 | REWRITE, keep it as dotted |
| 3 | `<recv>.mapValues(f).<iteration>` | `m.mapValues(f).foreach(...)` | 3 | DECIDE: drop `.toMap` (lazy is fine here) or not? |
| 4 | result relies on the lazy, re-evaluated view | `m.mapValues(expensive)` held in a field | 1 | LINT |
| 5 | `mapValues` on a `MapView` or a user class | `m.view.mapValues(f)` | 4 | UNCHANGED |

Rules for the action column:

- **REWRITE** only when the new code means the same thing *for every occurrence of the shape*, and compiles.
- **LINT** when a fix exists but needs judgment, e.g. it depends on a value you can't see or an argument the new API doesn't take.
- **UNCHANGED** for look-alikes: the same name with another symbol, or code the rule is not about.
- **DECIDE**: when there's more than one reasonable answer, ask the user and don't pick silently.

Every row becomes at least one sample in the test input ([testing.md](testing.md)).

## 4. What to look out for

- **The same call, different syntax**: dotted `a.b(c)`, infix `a b c`, braces `a.b { c }`, Scala 3 colon arguments `a.b: c`, postfix `a b`, extra argument lists `a.b(c)(d)`, explicit type arguments `a.b[T](c)`.
- **Desugarings**: `for` turns into `foreach`/`map`/`flatMap`/`withFilter` calls that are *not in the tree*. Only synthetics show them.
- **Implicits**: extension methods (an implicit conversion around the receiver), implicit argument lists, and `apply` insertion. They show up as synthetics, not as tree nodes.
- **Method values and eta-expansion**: `xs.foreach(log)` vs `xs.foreach(log _)` vs `xs.foreach(log(_))`.
- **Chains**: in `a.x(1).x(2)`, both calls are occurrences, and one is nested inside the other. Rewrites of nested occurrences must not overlap.
- **Look-alikes**: same name, other owner (`MapView#mapValues`, `Option#get` vs `Map#get`, user code).
- **Comments and formatting** inside the expression, which a reprinting rewrite would lose.
- **Suppressions** already in the code (`// scalafix:ok`, `@SuppressWarnings(Array("scalafix:..."))`).
