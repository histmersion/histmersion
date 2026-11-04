# Patches

A rule returns a `Patch`: a set of text edits on tokens, plus lint diagnostics. Scalafix merges all patches of a file and applies them once.

## API

| Patch | Use |
|---|---|
| `Patch.replaceTree(tree, "new code")` | replace a node's text, e.g. rename a method: `Patch.replaceTree(name, "iterator")` |
| `Patch.removeTokens(tokens)` / `Patch.removeToken(token)` | delete tokens, e.g. the tokens from the last `.` before the name remove `.view` from `m.view` (see *Syntax and precedence*) |
| `Patch.replaceToken(token, "text")` | replace one token |
| `Patch.addLeft(tree, "...")` / `Patch.addRight(tree, "...")` | insert text before or after a node (also for tokens) |
| `Patch.addGlobalImport(importer"com.example.Util._")` | add an import at the top of the file (quasiquotes work in rules) |
| `Patch.addGlobalImport(Symbol("com/example/Util."))` | the same, from a symbol |
| `Patch.removeGlobalImport(symbol)` / `Patch.removeImportee(importee)` | remove an import |
| `Patch.replaceSymbols("a.b.Old" -> "a.b.New")` | rename all references to a symbol and fix the imports |
| `Patch.lint(Diagnostic("", message, pos))` | report a problem without changing code |
| `p1 + p2`, `Seq(...).asPatch`, `Patch.empty` | combine patches |
| `patch.atomic` | all or nothing; required for suppression (see below) |

## Small edits, not reprinting

Prefer the smallest edit that does the job:

```scala
// m.mapValues(f)  →  m.view.mapValues(f).toMap
case app: Term.Apply => app.fun match {
  case sel @ Term.Select(recv, name) if mapValues.matches(name) =>
    // anchor on the `.` of `.mapValues`: recv.pos excludes parentheses around the receiver
    val dot = sel.tokens.find(t => t.is[Token.Dot] && t.start >= recv.pos.end)
    dot.fold(Patch.empty)(d => (Patch.addLeft(d, ".view") + Patch.addRight(app, ".toMap")).atomic)
  case _ => Patch.empty
}
```

Reprinting (`Patch.replaceTree(app, s"${recv.syntax}.view.mapValues(${arg.syntax}).toMap")`) also works, but it loses comments and line breaks between the parts, and it rewrites code you didn't need to touch. If you do reprint, build the new text from the `.syntax` of the original subtrees, never from `.toString` of a constructed tree.

## Overlapping patches

Two patches that touch the **same tokens** don't compose. Scalafix applies both, and the result is garbage, e.g. removing the tokens of `.mapValues` and replacing the name `mapValues` produced `pricesmapValues`. That's why:

- Every token should be edited by at most one patch. Removing tokens and replacing a tree that contains them is an overlap.
- In nested occurrences (`m.mapValues(f).mapValues(g)`), `collect` visits both calls. Edit only names and separators that belong to each call, not the whole receiver. Otherwise the outer patch reprints the inner occurrence while the inner patch edits it too.
- Adding text with `addLeft`/`addRight` at the same position from two patches is fine: both insertions are kept.

## Syntax and precedence

- **A tree's position doesn't include the parentheses around it.** In `(m ++ m).mapValues(f)`, the receiver is the `Term.ApplyInfix` `m ++ m`, and `Patch.addRight(recv, ".view")` produces `(m ++ m.view)`. Anchor on tokens that belong to the outer node instead, like the `.` of the select (see the example above).
- Inserting a call after an expression that isn't parenthesized needs parentheses when the expression isn't simple: `a + b` followed by `.toMap` has to become `(a + b).toMap`. Check `recv.is[Term.Name] || recv.is[Term.Select] || recv.is[Term.Apply]`, and otherwise wrap the receiver in `(` `)`.
- Infix calls (`a op b`) can be rewritten to dotted form (`a.op(b)`), or kept infix (`a newOp b`). Pick one and make it consistent with the expected output.
- Scala 3 braceless code has no braces to anchor to. Prefer patches on names and on whole expressions over brace and paren tokens.

## Imports

- `addGlobalImport` can **duplicate** an import that is already in the file (seen with `importer"..."` when the existing import sits inside the `package` clause). Before adding one, check `doc.tree.collect { case i: Importer => i.syntax }` or the symbols already imported.
- Import patches (`addGlobalImport`, `removeGlobalImport`, `replaceSymbols`) may also split, merge or reorder existing import lines. Check the result against the expected output. If the expected output and the tool disagree only on import layout, ask the user which one to keep.
- If the new code calls an extension method or an implicit, it needs the import, or it won't compile. The recompile step in `run-rule.sh` catches a missing import.
- An import that's no longer used after the rewrite is harmless unless the project compiles with `-Wunused:imports` and `-Xfatal-warnings`. Remove it with `Patch.removeGlobalImport` only when you're sure no other code in the file uses it.

## Lints

```scala
Patch.lint(Diagnostic("", "Map#mapValues is deprecated; use .view.mapValues(f).toMap.", name.pos,
  severity = scalafix.lint.LintSeverity.Warning))
```

- `Diagnostic(id, message, position, explanation = "", severity = LintSeverity.Error)`. The default is `Error`, which makes scalafix exit with code 64. Use `Warning` if lints shouldn't fail the run.
- `id` is a category *within* the rule: `Diagnostic("", ...)` is reported as `[MyRule]`, `Diagnostic("lazyView", ...)` as `[MyRule.lazyView]`. Don't repeat the rule name, or you get `[MyRule.MyRule]`.
- Point `pos` at the smallest relevant node, e.g. the method name, not the whole statement.
- The message should say what's wrong and what to do instead, because it's what the user sees in CI.

## Suppression and `.atomic`

Users silence a rule with `@SuppressWarnings(Array("scalafix:MyRule"))` on a definition or with a `// scalafix:ok` comment on the line. Scalafix then drops the **lints** there, but it still applies a **rewrite** unless the patch is `.atomic`. Make every rewrite `(...).atomic`. An atomic patch that partially overlaps a suppressed region is dropped as a whole.

`[UnusedScalafixSuppression]` warnings about suppressions for *other* rules are harmless.

## Configurable rules

Hard-code the targets at first. Make a rule configurable only when it will be reused, e.g. with the target method as a setting:

```scala
import metaconfig.{ConfDecoder, ConfEncoder, Configured}
import metaconfig.generic.{deriveDecoder, deriveEncoder, deriveSurface, Surface}

final case class MyRuleConfig(target: String = "scala.collection.MapOps.mapValues", rewrite: Boolean = true)
object MyRuleConfig {
  val default: MyRuleConfig = MyRuleConfig()
  implicit val surface: Surface[MyRuleConfig] = deriveSurface[MyRuleConfig]
  implicit val decoder: ConfDecoder[MyRuleConfig] = deriveDecoder[MyRuleConfig](default)
  implicit val encoder: ConfEncoder[MyRuleConfig] = deriveEncoder[MyRuleConfig]
}

class MyRule(config: MyRuleConfig) extends SemanticRule("MyRule") {
  def this() = this(MyRuleConfig.default)
  override def withConfiguration(c: Configuration): Configured[Rule] =
    c.conf.getOrElse("MyRule")(config).map(new MyRule(_))
  ...
}
```

```hocon
# .scalafix.conf
MyRule.target = "scala.collection.MapOps.mapValues"
```
