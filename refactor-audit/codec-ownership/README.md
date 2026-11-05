# Domain codec consumer evidence

## Compiled evidence changes the repair decision

The bounded JDK classfile scan (`compiled-1`) covers 9,889 current JVM main/test class
files and finds five external consumers of three domain codecs:

| Codec | Generated consumer classes | Treatment |
|---|---|---|
| Source.Status | BSourceRow, FullSourceRow | Extract to the source feature and import the given at both derivation sites |
| Option[Confidence] | BFindingRow, FindingIdRow | Extract to the finding feature and import the given at both derivation sites |
| Place.Precision | PlaceDetailRow | Extract to the place feature and import the given at the derivation site |
| Edge.Kind | No external compiled reference observed | Guarded removal probe; absence remains bounded to the scanned JVM outputs |

All five owning row definitions still exist in current sources. The generic array
codec has 89 external compiled consumer classes and must remain in util.database.
These compiled references were not present in the source/synthetic inventory below.
Deleting the whole domain codec block based on that inventory would be wrong.

The initial probe failed to compile because Scala 2.13 rejected JDK 25 classfile
sealed-type metadata with cyclic-reference errors (and a List ordering expression
needed correction). The working probe calls the same public JDK classfile interfaces
through reflection, then reads typed constant-pool member records. No application
source was changed by either attempt. Reflection here is tooling API access, not a
claim to detect reflective application consumers. The class manifest preserves the
scanned artifact hashes; stale classes may conservatively contribute references.

Next: one AST batch extracts the three required definitions, updates all five known
derivation sites, removes the obsolete edge definition only under a compiler probe,
and removes the three reviewed unused imports. Preserve codec bodies and fallback
semantics. Recompile all production/tests, inspect fallback diagnostics, verify the
five generated consumers now target the feature-owned definitions, then remeasure
the original isolation IDs. No codec repair is claimed until those checks pass.

## Earlier source and synthetic evidence

`inventory-1` covers all 3,149 application and test source files with current
SemanticDB. It records source-name references and compiler-generated IdTree symbols
under `com/histmersion/util/database/Codecs$package`, plus the five given ASTs.
No source was changed.

The generic List[String] codec has observed compiler-synthetic and explicit-name
references. The three named domain codecs have only declaration-name occurrences;
the anonymous Confidence codec has no observed consumer. Definition-tree `.symbol`
is empty in this API; the AST and named-reference records retain that limitation.
Do not infer zero consumers or safe deletion from these counts.

The repository skill's claim that Scala 3 synthetics are always empty does not hold
for this compiler/build: actual Scala 3.10.0 evidence includes the List codec.
Conversely, successful collection of some synthetics does not establish complete
macro expansion coverage. The inventory explicitly leaves that uncertainty open.

SourceRepo's local codec targets the API Source.Status, while the utility codec
targets the separate domain Source.Status. Existing VocabCodecSuite assertions
exercise the API/repository path. The next bounded check must determine whether
the domain codecs still have compiled consumers before choosing extraction or
removal. See `../isolation-triage/review-2/README.md` for the treatment batch.
