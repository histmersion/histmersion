# Generated companion/value name fixture

origins/ contains synthetic test provenance, not production observations. The
empty plan changes no source. Five positive collisions cover String list elements,
Int list elements, a scalar, an empty list and a preserved Ordering given. The independent golden preserves
the original public values and type aliases, moves the actual newtype companions
to fresh names and changes only rooted constructors inside their own initializers.
NamesValue is already taken, so the fresh name must be NamesValue2. Consumer
assertions require the old aliases, list indexing/iteration, scalar and empty-list
behavior to work. A foreign Label field must remain unchanged. Original inputs have naming/cyclic errors; rewritten output must
compile/run with Scala3.10, -source:future and fatal E165.

Nine negative cases stay outside the plan: unknown provenance, custom behavior,
a non-Pure constraint, unqualified construction, a self reference, an untyped value
and an instance owner, plus a self-referencing given and a rooted self reference. They are deliberately invalid source inputs, preserved as
exclusions rather than edited into easier passing cases. The initial three-case
fixture/preview is superseded by the four-case fixture that also proves no rooted
constructor is needed for Nil. The current five-case fixture additionally checks
given preservation and foreign member spelling; all original negatives remain.
No negative assertion was removed or weakened.

Run value-names-fixture-index.conf and value-names-fixture.conf through the existing
Scalafix CLI, compare the generated Values.scala with expected/Values.scala, then
run that generated source with scala-cli and --main-class collisionfixture.checkValueNames.
Only the positive rewritten source belongs in that runtime run. The production
mode config supplies the whole-project snapshot, index and roots, not individual
source/type/name mappings. shapes.tsv retains each original/new opaque-definition
identity and the carried origin; ordinary value uses elsewhere stay unchanged.
