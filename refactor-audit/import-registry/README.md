# Import registry: prerequisites and bounded next batch

At the prerequisite checkpoint, the four original `import-referrers` isolation
identities remained outstanding. This prerequisite batch does not claim their repair,
detector acceptance, or progress toward 90% bulk
automation. The current treatment checkpoint remains
`unread-projection/reconciliation-2/current-treatment.tsv` until fresh reconciliation.

The subsequent ownership batch and its final reconciliation are recorded in
[OWNERSHIP.md](OWNERSHIP.md); the prerequisite observations below remain historical.

## Database baseline exposed failures

The worktree-local PostgreSQL cluster in `target/transaction-repair-pg` on port
55573 runs only test databases. BundleSuite, MergeSuite, MergeExtrasDbSuite and
RenameRepoSuite ran 39 tests: 29 passed, 10 failed, none skipped. The failed baseline
is retained in `tests/baseline.tsv`; no failure was accepted or assertion relaxed.

Two failures decode lowercase `current` through Magnum enum derivation in
CitationRepo. SourceRepo already supplied the required vocabulary codec, but only
locally. The first repair shared that API-status codec through library-owned
SourceColumns and imported it into both repositories. The 38/39 passing intermediate
run then exposed the same problem in ArticleRepo, after the earlier failure stopped
masking it. That run is retained in `prerequisite-tests/baseline.tsv`.

The final codec is `com.histmersion.api.library.SourceColumns` in the **db module**:
instances for API records at the JDBC boundary, without a database dependency in
the API module. SourceRepo, CitationRepo and ArticleRepo import it. The separate
domain-status codec is restored unchanged, with no new research-to-library edge.
`api-codec-preview-1` is the frozen follow-up patch; `api-codec-tests` is the final run.

The other eight failures use distinct API/domain enums in equality tests. This
includes a merge-resolution selector: its always-false comparison chose the wrong
resolution, so it was a test setup defect as well as an assertion defect. The batch
uses API Bundle.Kind and MergeIssue.Kind for API results. Expected counts, expected
merged text, assertions and test identities stay intact. BundleSuite also uses the
API kind for file-name observations; both enum versions spell those files identically.

`tools/refactor/import-registry-prerequisites.conf` uses the existing AstRecipe rule
with the full prior build source manifest. It freezes 22 matched ASTs across six
files. Preview 1 is superseded only for an indented blank line; preview 2 is applied.
The initial apply command omitted `--scala-version 3.10.0`, failed parsing and changed
no application source. The prematurely launched `tests/after.tsv` therefore repeats
the ten failures and is **not** post-repair evidence. Both observations are retained.
The corrected apply uses Scala 3; `prerequisite-applied-verification.txt` separately
checks all six intermediate resulting hashes.

`ImportPrerequisiteVerification` requires the same 39 test identities, no skips,
all passing after repair, ten original failures, the frozen source patch chain, and
unchanged expected `assertEquals` arguments. It gives no policy-repair credit.

## Registry rewrite checklist

Only SourceBundleRepo.sourcesIn consumes the registry carried by generic
ImportOptions. Its two rename paths use the Writer transaction. The intended
injection chain is Deps → LibraryData → LibraryExchange → SourcesCarried →
SourceBundleRepo.sourcesIn. Utility-owned import options then need no library type.

- Preserve all eight referrers and their order; preserve `given DbCon = w.transaction`.
- Instantiate LibraryData with the registry; retain owner and SourceNotes on its
  companion so existing mirror consumers retain their vocabulary.
- Make the library carried-kind factory capture the registry; leave stage ranks,
  other carried kinds, body rename and derived stages unchanged.
- Remove the registry field from ImportOptions and BundleService.Wiring, updating
  the server construction and all five test option constructions in the same patch.
- Supply SourceFixtures.referrers through TestExchange, preserving the fixture order.
- Re-run the actual database suites against the repaired baseline; full compile,
  project-wide measurement and original-identity reconciliation must follow.

The original four findings are not silently accepted. Their ownership decision
and transition from unresolved to confirmed must be recorded before repair credit.
Cross-layer enum comparisons and codec-provider coverage also need detector work:
these failures show that compilation and the earlier codec probes did not establish
runtime coverage of every caller. No independent precision/recall claim follows.

## Verified prerequisite result

The final run passes all 39 identical database test identities with zero skips.
`prerequisite-verification.txt` passes, including unchanged expected assertion
values and exact source hashes across both AST patches. Full production and test
compilation passes on 3,159 source files (`api-codec-build-1`); the intermediate
3,158-file compile is retained separately. Seven net application/test files changed.
No native-image validation was run. The registry ownership rewrite remains pending.

Fresh architecture/model inventories cover the same 3,159 compiled source hashes
and all 28 source modules. `final-verification.txt` checks the entire patch chain
against the original compiled universe, excluding unplanned source changes.
Isolation observation `2026-11-05T05-43-36.892228Z` passes: 115 production and 213 test
relationships, zero new/reopened, 77 ambiguous owners and 182,320 unclassified
direction rows. Primitive inventory retains 30,393 production member leaves and
62,229 all-scope leaves. Unresolved leaves are 46,914 (one fewer after the changed
declaration shapes); this is not a verified model repair or a compliance claim.
Earlier counts and failed observations remain intact. Other policy families retain
their previous checkpoints; copy automation remains paused.
