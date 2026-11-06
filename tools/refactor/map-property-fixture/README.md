# Map property sharing and combined declaration repair

The combined input includes value-names-fixture through additionalManifest and
uses its explicitly synthetic origins alongside this fixture's synthetic origins.
Original compilation deliberately fails. The preview must change exactly the two
positive files and match both independent goldens. Both negative files stay absent
from the plan, and the legacy five-property fixture output remains byte-identical.

Five helper aliases cover a key, a value, both slots and a method-result key.
The actual rewritten Scala3.10 future-source/fatal-E165 runtime checks require
exact type identity, lookups, empty input, one producer evaluation and unchanged
duplicate-key behavior. The naming fixture separately checks its values, aliases,
indexing, fresh names, ordering and foreign member preservation in the same pass.

Map exclusions cover unknown origins, conflicting key properties, transformed
projections, custom behavior, nested bindings, protected model properties and raw
keys. Conflict is used for both a Slug key and a Title value, plus a separate valid
key projection: value constraints must taint the entire helper, so neither alias
is emitted. Ordinary cases retain constraints before filtering. Value proposals
need the compiler's exact V upper bound; missing/unsupported bounds stay unresolved.
No record property identities are merged and no expression body is rewritten.

Use map-property-fixture-index.conf, compile the four original sources into its
build.log, then run map-property-fixture.conf with the existing Scalafix CLI.
Compare the generated files with map-property-fixture/expected/MapFlows.scala and
value-names-fixture/expected/Values.scala. Run the positive generated sources with
main classes mapfixture.checkMapFlows and collisionfixture.checkValueNames.
No new runner, verifier or report framework is needed.
