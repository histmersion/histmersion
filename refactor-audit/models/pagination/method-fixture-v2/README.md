# Method parameter propagation

ModelTypeRewrite now accepts reviewed method symbols, ordered parameter names and
the parameter-to-newtype mapping in `methods`, using the existing Record config
shape. Definitions, trait declarations, defaults, named/positional callers and
forwarding participate in the same patch as record fields and constructors. An
unrelated method with the same name remains unchanged. Unsupported declaration
shapes (multiple parameter clauses, changed names/order or non-Int types) fail.
Eta expansion and arbitrary indirect function/dataflow propagation are not implemented.
The compiler remains a required closure check; a successful preview alone is not a fix.

Both the expected and actual fixture compile and run successfully. Runtime checks
cover values 0, 4, 5, 7, 9, 11, 13 and 17, optional defaults and raw-Int type rejection.
The exact output includes two distinct count types and checks the location of `.value`
inside the conversion constructor. The earlier fixture also passed before this case
was added. The first Scalafix invocation used the wrong sourceroot and reported missing
SemanticDB; correcting it to the compiler's input root succeeded, without any stale
SemanticDB bypass.

Commands run from the worktree (each was a separate command):

```
scala-cli compile --server=false --semanticdb tools/refactor/model-rewrite-methods/input/Models.scala tools/refactor/model-rewrite-methods/Types.scala --compilation-output target/model-methods-baseline-v2
scala-cli run --server=false -q -S 2.13.18 --dep ch.epfl.scala:scalafix-cli_2.13.18:0.14.9 --main-class scalafix.cli.Cli -- --config tools/refactor/model-rewrite-methods/rule.conf --scala-version 3.10.0 --sourceroot tools/refactor/model-rewrite-methods/input --auto-classpath --auto-classpath-roots target/model-methods-baseline-v2 --files tools/refactor/model-rewrite-methods/input/Models.scala --out-from /Users/dev/Workspaces/GitHub/histmersion-wt/contracts3-scalafix/tools/refactor/model-rewrite-methods/input/ --out-to /Users/dev/Workspaces/GitHub/histmersion-wt/contracts3-scalafix/target/model-methods-preview-v2/
diff -u tools/refactor/model-rewrite-methods/expected/Models.scala target/model-methods-preview-v2/Models.scala
scala-cli run --server=false target/model-methods-preview-v2/Models.scala tools/refactor/model-rewrite-methods/Types.scala tools/refactor/model-rewrite-methods/Checks.scala
scala-cli run --server=false tools/refactor/model-rewrite-methods/expected/Models.scala tools/refactor/model-rewrite-methods/Types.scala tools/refactor/model-rewrite-methods/Checks.scala
```

All five commands exited zero. FreezeDomainCounts independently checked exact output
again before sealing the domain previews. This is rewrite development evidence, not
an independent detector precision/recall sample and not a new confirmed denominator.

Negative check: the same Scalafix command with `rejected-order.conf` and a separate
`target/model-methods-rejected/` destination exits 1 with `Reviewed method parameter
order changed: methodfixture/Service.page().`. It does not produce an accepted rewrite.
