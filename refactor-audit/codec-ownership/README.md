# Domain codec consumer evidence

## Extraction verified

The configured `ExtractGivens` Scalafix batch moved three codecs into feature-owned
objects, propagated imports to five generated row-codec consumers and three probe
suites, removed the unused edge codec, and removed the reviewed domain imports.
The three codec definition ASTs are unchanged. The generic list codec remains.
Preview 1 is retained as an intermediate whitespace result; preview 2 coalesces
adjacent AST-token removals and is the applied, hash-checked patch.

Full production and test compilation passes across 3,156 source files. Nine fixed
codec probes pass both before and after extraction, without skips; their assertion
ASTs are unchanged. These exercise JDBC read/write and null/fallback behavior using
a narrow proxy, not a live database. The compiled scan finds all five generated
consumers using the new providers and all 89 generic consumers unchanged. Edge-codec
removal compiles without an observed external reference before or after. Reflection,
external consumers and native-image behavior remain outside this evidence.

Fresh architecture/model scans and the isolation history gate cover the same source
hashes as the successful build. `reconciliation-1` verifies exactly 29 original
production IDs removed, no unexpected removals, no new or reopened IDs, and all 317
remaining relationships preserved. These are dependency relationships, not 29
independent design defects. Current production treatment is 13 confirmed, 109
accepted and four unresolved; tests retain 59 prior non-violations and 132 candidates.
`current-treatment.tsv` retains original review hashes as historical evidence.

Production model candidates remain 30,387 leaves. All-scope primitives are 62,221
and unresolved leaves 46,903; test additions and ownership changes do not establish
model repairs. No detector refinement, independent quality acceptance, new exception
or migration-wide automation percentage is claimed.

Reproduce the final gate with the established Scala 2.13.18 Scalafix CLI and
`--rules file:tools/refactor/CodecReconciliation.scala --scala-version 3.10.0
--files tools/refactor/CodecReconciliation.scala`. It refuses to overwrite its
evidence directory; preserve each observation before using a new output destination.

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

The planned AST batch extracts the three required definitions, updates all five known
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
