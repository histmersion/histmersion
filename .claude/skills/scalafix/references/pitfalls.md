# Pitfalls: symptom → cause → fix

## The rule doesn't match where it should

| Symptom | Cause | Fix |
|---|---|---|
| Works for `a.b(c)`, misses `a b c` or `for (x <- a.b)` | Only one syntax is matched | Run `explore.sh` and match every shape it lists: `Term.ApplyInfix`, `Term.For`, Scala 3 colon arguments |
| Misses some calls of the right method | The receiver's static type is a subclass that overrides it, e.g. `Some(1).get` is `Some#get`, not `Option#get` | Run `explore.sh` without a symbol filter and add the overriding symbols to the `SymbolMatcher` |
| Matches nothing, `name.symbol` is `Symbol.None` | No SemanticDB for the file: wrong `--sourceroot`, SemanticDB disabled, stale build | Use `run-rule.sh`, or check `semanticdbEnabled` in sbt. On macOS, `/var` vs `/private/var` paths make the sourceroot not match |
| Pattern `Term.Apply(fun, args)` doesn't compile or match | Positional extractors changed in Scalameta 4.6+ | Match `app: Term.Apply` and read `app.fun`, `app.argClause.values` |
| A quasiquote pattern `q"$a.b($c)"` misses cases | Quasiquotes match only the exact syntax | Match node types and fields |

## The rule matches where it shouldn't

| Symptom | Cause | Fix |
|---|---|---|
| Rewrites `MapView#mapValues` along with `Map#mapValues` | Matching on the name text | Match the symbol with a `SymbolMatcher` |
| Rewrites suppressed code | The patch isn't atomic | `(...).atomic` on every rewrite |
| Rewrites a definition (`def mapValues = ...`) | Matched every `Term.Name`, including definitions | Match references only: the `name` of a `Term.Select`, `op` of an infix, or check the parent |

## The output text is wrong

| Symptom | Cause | Fix |
|---|---|---|
| Garbled text like `pricesmapValues`, duplicated or missing pieces | Two patches touch the same tokens (e.g. `removeTokens` and `replaceTree` on overlapping ranges, or nested occurrences that both rewrite the shared part) | Each token is edited by at most one patch. Edit names and separators, don't reprint receivers |
| `(a ++ b).x` becomes `(a ++ b.view).x` | A tree's position excludes the parentheses around it | Anchor on a token of the outer node, e.g. the `.` before the method name |
| `a + b.toMap` instead of `(a + b).toMap` | Appended a call to a non-simple expression | Wrap the receiver in parentheses unless it's a name, select or apply |
| Comments or line breaks disappear | Reprinted the whole expression with `replaceTree` | Use small patches on names and tokens |
| Duplicate import | `addGlobalImport` doesn't always notice an existing import | Check the file's importers before adding |
| Imports reordered, split or merged | Import patches rewrite the import section | Compare with the expected output and ask the user which layout to keep |

## The output doesn't compile

| Symptom | Cause | Fix |
|---|---|---|
| `discarded non-Unit value` (under `-Wvalue-discard -Xfatal-warnings`) | The new API wants `A => Unit`, and the callback returns a value | Lint instead, or wrap the body so it returns `()`, as decided in the expected output |
| `match may not be exhaustive` / `MatchError` risk | A partial function (`{ case ... }`) passed where a total function is now expected | Add a fallback `case _ => ()`, but only if the match isn't already exhaustive |
| `unreachable code` | A fallback case added to a match that is already exhaustive | Detect exhaustiveness (e.g. a case for every subtype of a sealed type, or a wildcard), and don't add the fallback then |
| `value x is not a member of ...` | The new method is an extension and its import is missing | `Patch.addGlobalImport` |
| `too many arguments` / `missing argument list` | An explicit argument list (e.g. an implicit parameter passed explicitly) that the new method doesn't take, or takes differently | Lint, or handle the shape `Term.Apply(Term.Apply(...), ...)` explicitly |
| `type mismatch` on a function value | The argument is a `val pf: PartialFunction[...]` or a method reference whose type differs from the new parameter type | Look at the argument's symbol signature. Rewrite only the types that fit, and lint the rest |

## The process goes wrong

| Symptom | Cause | Fix |
|---|---|---|
| "Fixed" the test by editing the expected output | - | Never. The user owns the expected output. Explain and ask |
| The rule only passes because of sample-specific conditions | Overfitting | Match shapes and symbols, never `syntax == "..."` of a sample or line numbers |
| Passes the samples, breaks the real build | The samples miss a shape, or the samples use different compiler flags | Run `explore.sh` on the real code, add the missing shape, and copy the real `scalacOptions` |
| Scalafix exits with 64 although the rewrite looks fine | A lint with the default `Error` severity, or a rule crash | Read the messages. Use `LintSeverity.Warning` for advisory lints |
| `[UnusedScalafixSuppression]` warnings | Suppressions for other rules in the input | Harmless |
| The first run takes about a minute | Downloading scalafix and the compiler | Expected. Later runs are faster |
