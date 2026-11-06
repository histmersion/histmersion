# Generated helper adapters

Applied through the existing frozen-preview pipeline:513 AST patches in110
production files, zero application test edits. There are471 E007 adaptations and
42 E008 native-member repairs; existing owner/native/construction paths are included
alongside the new generated-helper support. This is not a policy repair denominator.

Both nominal types must belong to the immediate implementation owner, have exact
same native base/Pure constraints, unique compatible module/source-set/platform
definitions, and recognized property/generated-member provenance. At least one
must be a generated non-property helper. Custom constructors/methods, constraints,
unknown provenance, cross-owner calls, differing bases and property-to-property
conversions stay unresolved. Given aliases may remain. No type definition changes,
individual source-expression mappings or new runners/verifiers were introduced.
E134 is forced off through native-Boolean owner mode; an invalid-mode fixture
rejects enabling it. Existing structural conversions retain producer evaluation.

Five positive expressions match expected/Helpers.scala and actual rewritten
Scala3.10 future-source/fatal-E165 compile/runtime passes. Runtime checks include
empty collections and single tuple/function producer evaluation with repeated
callback calls. All six negative diagnostics stay outside the plan. Origins are
synthetic test evidence. The initial unqualified-base fixture selected zero;
qualified final inputs exercise the existing safety gate. Five old ordinary/two
owner exact outputs are unchanged from the runtime-tested earlier batch.

All110 files match their frozen preview. ../project-compile-frontier-23 is the
actual full build: API2478 ->2297 each platform, values latest31 ->25,
upstreams346 ->282, vaults94 ->98, Calibre194 ->193, ai-http89 ->77. Three other
failures are cached; all ten tasks still fail and dependent tests remain blocked.
The vault increase remains visible. Changed-site inspection finds callback and
outer result-contract mismatches exposed after inner calls compile; it does not
classify every changed diagnostic or prove zero regressions. No assertions changed.

../type-positions-after-helper-adapters-1 preserves all3169 Scala files and stable
raw/newtype IDs with zero delta. Its complete Scala hashes match the build snapshot
(which additionally includes build.sbt). Raw candidates886 production/6009 test;
opaque shapes26894 production/19 test;2981 overlap groups;16228 unresolved references.
No accepted global inventory, new exception or overall automation rate is inferred.
Canonical history/status remains ../../../migration-rounds.tsv and
../../../priority-family-status.md.
