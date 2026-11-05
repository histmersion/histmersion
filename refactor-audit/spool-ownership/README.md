# Acquisition spool ownership

The reviewed Spool adapter validates library citekeys and defines the fetched-file
layout. It belongs to library acquisition, while generic storage remains behind
Store. This is the existing `spool-ownership` treatment decision, not a new exception.

The existing semantic RelocateOwners rule moved the definition from util.ocr to
domain.library.acquisition and updated all five caller files, including three test
files. Its six-file preview records eleven matched names and their ASTs. Grouped
imports and a fully qualified constructor call were handled by existing matchers.
No application source or assertion was edited manually. The class definition AST
is unchanged; primitive arguments and other policy concerns remain visible.

Full production/test compilation passed on 3,156 source files. SpoolSuite ran three
tests with zero failures or skips: valid placement, traversal rejection and extension
sanitization. Test bodies remain unchanged apart from the relocated import. This
batch does not establish database, network or native-image behavior.

The fresh architecture and model inventory covers all 28 source modules and the
same hashes as the build. Relocation check finds no old-owner references.
OwnershipMoveReconciliation verifies exactly the three reviewed original production
IDs disappeared, the definition AST is unchanged, caller patches match the frozen
preview, and all other 314 identities remain unchanged. No new or reopened IDs.
The reconciliation is configurable for subsequent ownership moves; it refuses a
different treatment universe, unexpected removals or changed residual evidence.

Current isolation treatment: ten confirmed production relationships (seven unread
projection, three media-source boundary), 109 accepted exact contributions and four
unresolved registry relationships. Tests retain 59 prior non-violations and 132
candidates. Seventy relationships have ambiguous owners. Unclassified direction
evidence is 182,261 rows; it remains unresolved rather than implicitly compliant.
Model counts remain 30,387 production member primitive leaves, 62,221 all-scope
primitive leaves and 46,903 unresolved leaves. No model repair is claimed.

Evidence is in preview-1, build-1 and reconciliation-1. Configurations are
tools/refactor/spool-relocation*.conf and spool-reconciliation.conf. Run them through
the established Scala 2.13.18 Scalafix CLI with Scala 3.10.0 sources; semantic passes
use the fresh target/out classpath. Preserve existing report directories when
repeating a measurement. No detector refinement or independent quality acceptance
is claimed; this is one ownership defect represented by three relationships.

Next design work must preserve behavior: media delegates source parsing through its
owned MediaSources interface with every implementation and test double updated;
personal needs its own typed unread projection with explicit contributor conversion.
The import-registry ownership conflict remains unresolved, with no blanket exception.
