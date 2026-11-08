# Scalameta trees

Scalafix 0.14.9 uses Scalameta 4.17. Check what a tree looks like with `scripts/tree-of.sh '<code>'`; don't guess. This file is a map of the common nodes and how to move around them.

## Common nodes and their fields

Read fields by name. Positional extractors (`Term.Apply(fun, args)`) changed between Scalameta versions when argument and parameter clauses became their own nodes. Only the stable ones, `Term.Select(qual, name)` and `Term.Name(value)`, are safe in patterns.

### Calls and selections

| Code | Node | Fields |
|---|---|---|
| `x` | `Term.Name` | `value` |
| `a.b` | `Term.Select` | `qual`, `name` |
| `a.b(c, d)` | `Term.Apply` | `fun` (= `Term.Select(a, b)`), `argClause: Term.ArgClause` → `.values: List[Term]` |
| `a.b(c)(d)` | `Term.Apply` nested in `Term.Apply` | the outer `fun` *is* the inner apply. Each argument list is its own node |
| `f(x)(using ord)` (Scala 3) | `Term.Apply` | `argClause.mod == Some(Mod.Using())`. A Scala 2 implicit list passed explicitly, `f(x)(ord)`, looks like an ordinary argument list |
| `a.b[T](c)` | `Term.Apply(fun = Term.ApplyType)` | `ApplyType`: `fun`, `targClause.values: List[Type]` |
| `a b c`, `a b (c, d)` | `Term.ApplyInfix` | `lhs`, `op: Term.Name`, `targClause`, `argClause.values` |
| `!a`, `-a` | `Term.ApplyUnary` | `op`, `arg` |
| `a.b { c }` | `Term.Apply` whose single argument is a `Term.Block` | |
| `a.b { case ... }` | `Term.Apply` whose single argument is a `Term.PartialFunction` | `cases: List[Case]` |
| `a.b: x => ...` (Scala 3 colon argument) | `Term.Apply` whose argument is a `Term.Block` holding the function | |
| `f _` | `Term.Eta` | `expr` |
| `new A(x)` | `Term.New` | `init.tpe`, `init.argClauses` |
| `a = b` | `Term.Assign` | `lhs`, `rhs` |
| `s"a $b"` | `Term.Interpolate` | `prefix`, `parts: List[Lit]`, `args` |
| `(a, b)` | `Term.Tuple` | `args` |
| `e: T` | `Term.Ascribe` | `expr`, `tpe` |
| `1`, `"s"`, `true` | `Lit.Int`, `Lit.String`, `Lit.Boolean` | `value` |

### Functions, blocks, control flow

| Code | Node | Fields |
|---|---|---|
| `x => body`, `(x: Int) => body` | `Term.Function` | `paramClause.values: List[Term.Param]`, `body` |
| `_ + 1` | `Term.AnonymousFunction` | `body`, containing `Term.Placeholder` |
| `{ case p => b }` | `Term.PartialFunction` | `cases` |
| `{ a; b }` | `Term.Block` | `stats` |
| `x match { ... }` | `Term.Match` | `expr`, `casesBlock.cases` |
| `case p if g => b` | `Case` | `pat`, `cond: Option[Term]`, `body` |
| `if (c) a else b` | `Term.If` | `cond`, `thenp`, `elsep` (a `Lit.Unit` when there's no else) |
| `try a catch { ... } finally c` | `Term.Try` | `expr`, `catchClause: Option[Term.CasesBlock]`, `finallyp` |
| `for (x <- xs) body` | `Term.For` | `enumsBlock.enums`, `body` |
| `for (...) yield e` | `Term.ForYield` | `enumsBlock.enums`, `body` |
| `x <- xs` / `y = e` / `if c` | `Enumerator.Generator` / `Enumerator.Val` / `Enumerator.Guard` | `pat`, `rhs` / `pat`, `rhs` / `cond` |

A `for` has **no** `foreach`/`map`/`flatMap` calls in the tree. The compiler adds them later. `explore.sh` shows them as synthetics.

### Patterns

| Code | Node |
|---|---|
| `x` (binding) | `Pat.Var(name)` |
| `_` | `Pat.Wildcard` |
| `Some(y)` | `Pat.Extract(fun, argClause.values)` |
| `e: Ex` | `Pat.Typed(lhs, rhs)` |
| `a @ p` | `Pat.Bind(lhs, rhs)` |
| `A \| B` | `Pat.Alternative(lhs, rhs)` |

### Definitions and structure

| Code | Node | Fields |
|---|---|---|
| file | `Source` | `stats`, usually a `Pkg` |
| `package a` | `Pkg` | `ref`, `body.stats` |
| `import a.b.{c => d, _}` | `Import` | `importers: List[Importer]` → `ref`, `importees` (`Importee.Name`, `.Rename`, `.Wildcard`, `.Given`) |
| `object O { ... }` | `Defn.Object` | `name`, `templ.inits`, `templ.body.stats` |
| `class A(x: Int) extends B` | `Defn.Class` | `mods`, `name`, `ctor.paramClauses`, `templ` |
| `trait T` | `Defn.Trait` | like `Defn.Class` |
| `def f[A](a: Int)(implicit b: B): C = ...` | `Defn.Def` | `mods`, `name`, `paramClauseGroups` (each has `tparamClause` and `paramClauses`, and each clause has `.values` and `.mod`), `decltpe`, `body` |
| `val a, b: Int = 1` | `Defn.Val` / `Defn.Var` | `mods`, `pats`, `decltpe`, `rhs` |
| `def f: Int` (abstract) | `Decl.Def` | |
| `@ann` | `Mod.Annot` | `init.tpe`, `init.argClauses` |
| `implicit`, `private`, `override` | `Mod.Implicit`, `Mod.Private`, `Mod.Override` | |
| Scala 3: `given x: T = ...` | `Defn.GivenAlias` | |
| Scala 3: `extension (s: String) def f = ...` | `Defn.ExtensionGroup` | `paramClauseGroup`, `body` |

### Types

| Code | Node |
|---|---|
| `Int` | `Type.Name` |
| `a.B` | `Type.Select(qual, name)` |
| `List[Int]` | `Type.Apply(tpe, argClause.values)` |
| `A => B` | `Type.Function` |

## Moving around

```scala
tree.collect { case t: Term.Apply => ... }  // all matching nodes, outermost first, nested ones too
tree.traverse { case t: Term.Name => ... }  // like collect, but with no result
tree.children                               // direct subtrees
tree.parent                                 // Option[Tree]
tree.is[Term.Apply]; tree.isNot[Lit]        // type tests without a cast
tree.syntax                                 // the source text (exactly as written, for parsed trees)
tree.structure                              // the tree as a string, the way tree-of.sh prints it
```

- `collect` visits **every** matching node, including nested ones. In `f.a(x).a(y)` both calls are visited separately, so make sure their patches don't overlap ([patches.md](patches.md)).
- To match a call *with its context*, match the outermost node and look inside it. For example, match the `Term.Apply` for `a.b(c)` and check `app.fun`, instead of matching the `Term.Name` and climbing `.parent`. Use `.parent` to check where a node is used: "is this `m.mapValues(f)` the receiver of a `.toMap`?".
- Quasiquote patterns work in rules (`case q"$recv.mapValues($f)" =>`), but they only match the exact syntax they spell out. They don't match infix or braces variants. Prefer node types and fields.

## Positions, tokens, comments

```scala
tree.pos                    // Position: start, end, startLine (0-based), startColumn, text
tree.tokens                 // the tokens of the node, including whitespace and comments inside it
doc.tokens                  // all tokens of the file
doc.comments.leading(tree)  // comments right before a node (Scala comments are trivia, not tree nodes)
```

Token types: `Token.Ident`, `Token.Dot`, `Token.LeftParen`, `Token.Space`, `Token.LF`, `Token.Comment`, `Token.KwCase`, and so on (`tree-of.sh --tokens` lists them). Token-level patches keep everything you don't touch. To remove `.view` from `m.view`, take the tokens from the last `.` before the name: `sel.tokens.filter(_.start >= dot.start)`. Don't start at `qual.pos.end`: a position excludes the parentheses around the receiver, so in `(a ++ b).view` that would also remove the `)`.

## Scala 3

- Scalafix parses with the dialect of `--scala-version` (in sbt, `scalaVersion`). Use `tree-of.sh --scala3` for Scala 3 syntax.
- Braceless syntax (`if a then b`, indentation blocks, `xs.map: x =>`) produces the same node types as the braces version. Only the tokens differ, so token-level patches need care: there may be no `{` or `}` to anchor to.
- New nodes: `Defn.GivenAlias`, `Defn.Given`, `Defn.ExtensionGroup`, `Defn.Enum`, `Term.ContextFunction`, `Mod.Using` on argument and parameter clauses.
- Semantic rules work on Scala 3, but SemanticDB has **no synthetics** there (see [semantic-api.md](semantic-api.md)).
