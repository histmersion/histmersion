# Generated companion/value name repairs

This is the applied naming preview:176 generated newtype companions,560 AST
patches in108 production files and zero application test edits. Public value names
and original public type aliases remain. Fresh canonical aliases/companions avoid
existing names;208 rooted constructors inside their owning value initializer are
updated. The configuration supplies the whole snapshot/index/roots and one mode,
not individual application type/expression/name mappings.

The rule requires a unique typed same-name value, exact owner-match generated
value provenance, canonical alias, stable object owner and empty qualified Pure
companion. Other same-name terms in that initializer veto repair; no constructor
is required for Nil or raw initializers. Custom/instance/ambiguous/unindexed cases
and constructor/member references outside the initializer remain outside coverage.
Twelve unresolved IDs are retained in shapes.tsv. Examples include Ordering-given
behavior, a lowercase origin colliding with an uppercase marker, and conservative
rejection of a foreign member with the same spelling. No exception was inferred.

Preview1 selected56/188 candidates and was not applied. Preview2 selects176/188
after adding the no-constructor case, leaving12 unresolved. All seven negative
fixture cases remain unchanged. Four positives match the independent golden and
actual rewritten Scala3.10 future-source/fatal-E165 compile/runtime passes, testing
aliases, list indexing/iteration, scalar/empty values and fresh-name selection.
Fixture provenance is synthetic. The same existing ApplyFrozenPreview and
CompileBatch pipeline performs application/verification; no new runner is used.

This cohort also applies ../project-helper-adapters-2 (168 patches/48 production
files) before naming, and ../project-helper-adapters-3 (65 patches/36 files) after
the newly exposed compiler errors. Each plan verifies before a later overlapping
application. Final36 files match their preview and the final complete Scala hash
list matches ../project-compile-frontier-26. There are793 total patches in142
distinct production files, not793 distinct verified policy violations.

Actual builds24/25/26 all fail the same ten tasks. API errors2297 ->2232 ->1986
->1958 each platform; values latest25 ->18; upstreams282 ->262 ->250 ->247;
vaults98 ->97 ->95; Calibre193 ->178 ->155 ->148; ai-http77 ->76 ->65.
Printed naming E120 diagnostics133 ->6, remaining6 in26. Counts do not classify
every added/removed diagnostic or prove zero regressions. Compiler settings are
unchanged and all source/build hashes stable. Dependent tests remain blocked;
plugin API/resolver/console sources are unchanged and no new test pass is claimed.

RENAME_NEWTYPE evidence explicitly maps all176 old/new opaque IDs. The existing
appliedPlans index history carries all176 generated origins to their new IDs;
../type-positions-after-value-names-2 retains those origins exactly. Opaque shapes
remain26894 production/19 test and raw candidates886 production/6009 test, with
zero raw-ID delta. The full3169-Scala census matches frontier26, which additionally
snapshots build.sbt. Unresolved references16256 (previous16228) and overlap groups
2980 (previous2981) remain partial AST evidence, not reduced semantic obligations.
The canonical ledger/status is ../../../migration-rounds.tsv and
../../../priority-family-status.md; no parallel tracking system is introduced.
