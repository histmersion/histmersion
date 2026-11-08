# Semantic API: symbols, types, synthetics

A `SemanticRule` gets an implicit `SemanticDocument`: the tree plus the SemanticDB data the compiler wrote for the file. SemanticDB tells you what a name refers to, its signature, and what the compiler inserted.

## Symbols

Every reference has a **symbol** in SemanticDB format:

| Symbol | Meaning |
|---|---|
| `scala/Option#` | class or trait `scala.Option` (`#` = type) |
| `scala/Option.` | object `Option` (`.` = term) |
| `scala/collection/MapOps#mapValues().` | method `mapValues` of trait `MapOps` |
| `` scala/Int#`/`(+3). `` | the 4th overload of `/` (overloads are numbered `(+1)`, `(+2)`, ...; operators are backquoted) |
| `demo/Prices.total().(m)` | parameter `m` of method `total` |
| `local3` | a local value, unique only within the file |

```scala
name.symbol                    // Symbol (Symbol.None when SemanticDB has nothing)
sym.value                      // "scala/collection/MapOps#mapValues()."
sym.owner                      // scala/collection/MapOps#
sym.displayName                // "mapValues"
sym.isLocal; sym.isNone
Symbol("scala/Some.")          // build one by hand
```

Ask for the symbol of a **name**, e.g. the `name` of `Term.Select(qual, name)` or the `op` of an infix. Other nodes may have no symbol, or a surprising one.

## Matching with `SymbolMatcher`

```scala
val mapValues  = SymbolMatcher.normalized("scala.collection.MapOps.mapValues")  // dotted name, all overloads
val exact      = SymbolMatcher.exact("scala/collection/MapOps#mapValues().")   // one exact symbol
val both       = mapValues + exact                                             // either one

mapValues.matches(name)    // takes a Tree or a Symbol
case mapValues(t) => ...   // also works as an extractor in patterns
```

- `normalized` ignores the term/type distinction and overload numbers. That's usually what you want.
- A reference resolves to the method of the **static type of the receiver**. If a subclass overrides the method, the call gets the subclass's symbol: `Some(1).get` is `scala/Some#get().`, and `SymbolMatcher.normalized("scala.Option.get")` does **not** match it. List the overrides in the matcher when they matter (`normalized("scala.Option.get", "scala.Some.get")`), and run `explore.sh` without a symbol filter to see which symbols occur in practice.
- Extension methods resolve to the extension: `"abc".reverse` refers to `scala/collection/StringOps#reverse().`, not to anything on `String`.

## Signatures and types

`sym.info: Option[SymbolInformation]`. Its `.signature` is one of:

```scala
sym.info.map(_.signature) match {
  case Some(MethodSignature(typeParams, parameterLists, returnType)) =>  // def
  case Some(ValueSignature(tpe)) =>                                    // val, var, parameter, local
  case Some(ClassSignature(typeParams, parents, self, decls)) =>       // class, trait, object
  case Some(TypeSignature(typeParams, lower, upper)) =>                // type alias / abstract type
  case _ =>
}
```

The types are `SemanticType`s, mostly `TypeRef(prefix, symbol, typeArguments)`:

```scala
// does this method return Unit?
case Some(MethodSignature(_, _, TypeRef(_, ret, _))) if ret.value == "scala/Unit#" =>

// the declared type of the receiver, when it's a val/param/def with a declared or inferred type
def typeOf(t: Term)(implicit doc: SemanticDocument): Option[Symbol] =
  t.symbol.info.map(_.signature).collect {
    case ValueSignature(TypeRef(_, s, _)) => s
    case MethodSignature(_, _, TypeRef(_, s, _)) => s
  }
```

Limits:

- SemanticDB has types for **symbols**, not for arbitrary expressions. For the type of `m.mapValues(f)` or `a.b(c)`, use the return type of the method symbol (`mapValues` → `MapView[K,W]`). Type parameters stay abstract (`Option[T]`).
- `info` works for symbols defined in the project and on the classpath. If it's `None`, check that the classpath was passed (`run-rule.sh` and sbt do this).
- `parameterLists` gives parameter symbols. Call `.info` on them to get their types, e.g. to tell an implicit `ExecutionContext` parameter list from an ordinary one.

## Synthetics (Scala 2 only)

Synthetics are the code the compiler added that isn't in the source: implicit arguments, implicit conversions (extension methods), inferred type arguments, `apply` insertion, and desugared for-comprehensions.

```scala
term.synthetics   // List[SemanticTree] for this term
```

| Printed as | Means |
|---|---|
| `*(Int)` | an implicit argument list was added: `List(3, 1).sorted` gets `(Ordering.Int)` |
| `*[Int]` | inferred type arguments |
| `*.apply` | `apply` inserted, e.g. a `{ case ... }` literal turned into a function |
| `orig(xs).foreach[Unit]({ (x) => orig(println(x)) })` | a `for` desugared into `foreach`. `orig(...)` marks original source code |
| `augmentString(*)` | an implicit conversion wrapped around the receiver `*`, e.g. for `"abc".reverse` |

Uses:

- **Is an implicit argument list passed implicitly or explicitly?** In `xs.sorted(ord)` the argument list is a tree node. Without it, there's a synthetic `*(...)` instead.
- **Does a `for` desugar to a method you care about?** Pattern-match on the synthetic tree (`SelectTree`, `ApplyTree`, `OriginalTree`, ...) or check its symbols.

With Scala 3, `synthetics` is always empty. Rely on tree shape plus symbols, and lint the cases you can't decide.

## Debugging semantic information

- `scripts/explore.sh` prints the symbol, signature and synthetics of every name around an occurrence. Run it before you guess a symbol string.
- Inside a rule, a temporary `println(s"${name.symbol} ${name.symbol.info}")` shows up in the scalafix output.
- `Symbol.None` everywhere means SemanticDB wasn't found: wrong `--sourceroot`, no `-Xsemanticdb`/`semanticdbEnabled`, or stale compilation.
