# Library-owned import registry

The four original `import-referrers` identities are confirmed violations of utility
isolation: ImportOptions and BundleService.Wiring carried a library-owned registry,
although only the library importer consumed it. The earlier unresolved decisions
remain in `unread-projection/reconciliation-2/current-treatment.tsv`.
`ownership-preview-1/adjudication.tsv` records the explicit correction; the generated
`prior-treatment.tsv` retains every other identity and decision.

The dependency now follows the owner:

`Deps.sourceReferrers → LibraryData → LibraryExchange.carried → SourcesCarried.sources → SourceBundleRepo.sourcesIn`

ImportOptions and BundleService.Wiring have no library registry field or import.
Both rename paths receive the same registry directly. The importer still uses
`given DbCon = w.transaction`; the repository opens no transaction. Carried-kind
order, stage ranks, blob renames and derived stages are unchanged. LibraryData is
an instantiated contribution; its companion retains the owner and mirror-record
vocabulary. TestExchange receives the original SourceFixtures registry through the
same carried-kind factory; all five test options constructions drop the old argument.

`ImportRegistryPreview` generates 33 AST/token patches across 12 frozen files. It
checks the complete prior compiled source manifest, exact occurrence counts and
nonoverlapping patches before producing a preview. The existing ApplyFrozenPreview
rule applies that preview with Scala 3 selected. No application source is edited
manually. This is an exact design batch, not a reusable generalized injection rule
or independent detector sample; it receives **no bulk-automation percentage credit**.

## Validation

- All 39 original PostgreSQL import/merge/rename tests pass before and after, with
  no skips. The earlier prerequisite failures remain in the separate historical runs.
- All seven DataContributionsSuite and MirrorSuite checks pass, including real
  migrated-schema ownership, contribution order and the mirror vocabulary.
- Full production/test compilation passes on the same 3,159 source files.
- ImportRegistryVerification checks that the importer body differs only in the
  registry access expression, the referrer registry AST is identical, and changed
  test ASTs differ only in registry injection. It checks the full source universe
  against the preview, compiled manifest and fresh inventories.
- Original-identity reconciliation is required before claiming the four repairs.
  New test composition edges require explicit per-identity review; no baseline
  reset or broad test exemption is allowed.

The broader migration remains incomplete: independent detector acceptance and the
90% automation denominator are not established. Copy automation remains paused.

## Final reconciliation

`ownership-verification/status.txt` passes. Four original production identities
are recorded as verified fixed, with manual/design treatment and no bulk credit.
The old test value identity is explicitly replaced by its factory-method identity,
with no repair credit. Three exact test relationships are reviewed as composition
non-violations; the initial failed identity observation is retained.

Observation `2026-11-05T05-55-56.421242Z` passes with zero new/reopened findings.
The current treatment ledger contains 111 production relationships, all previously
accepted contributions; the four reviewed unresolved registry cases are closed.
Tests contain 85 reviewed non-violations and 130 candidates. This is not proof that
module isolation is complete: 75 relationships still have ambiguous owners and
182,318 direction evidence rows remain unclassified. All 28 modules are scanned.
Primitive inventory remains 30,393 production member leaves, 62,229 all-scope leaves
and 46,914 unresolved leaves. Other families retain their previous checkpoints.

The first structural verifier exhausted memory by rematching generated AST nodes;
`ownership-verifier-failed-1.txt` records the tooling failure and its correction.
The final verifier checks a terminating normalization and the exact source-manifest
hashing format produced by the history gate. No application source, expected assertion,
test denominator or detector assertion was relaxed to pass verification.
