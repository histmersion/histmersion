# Applied pagination batch: remaining compilation work

Latest checkpoint: build-v3, after the eight-file web state batch, compiles web
production successfully and reports the same three server errors. Full Test/compile
remains blocked by server production. The web-specific test runs are tracked separately
under web-tests-v1/v2/v3; the first two retain five then two assertion type errors.
The assertion rewrites preserve all expected values and test identities. API factory
parameters, server contracts, the original 115-test comparison and project-wide
remeasurement remain outstanding. No overall pagination fix credit is claimed.

Web-tests-v3 compiles web tests and passes all 11 tests in FilterTabSuite/ListPageSuite,
with no failures or skips. This does not replace the separate 115-test API/core baseline
or whole-project Test/compile requirement.

The following checkpoints are historical observations.

Latest checkpoint: build-v2, after the six-file web record batch, reports seven web
errors and the same three server errors. Test/compile is still pending. TabPage,
TabState and ListPage fields have propagated; their broader signal/state consumers
remain required work. The earlier build-v1 checklist below is retained as history,
not a claim that those record declarations remain primitive. Compiler error counts
can change as later incremental phases become reachable.

Build-v1 compiled API JVM and Scala.js, then failed in web/server. It did not reach
Test/compile. Fourteen errors are compiler observations, not fourteen new violations.
All nineteen applied sources match preview-v3. No production fix is credited yet.

- Web TabPage.total and ListPage.offset/total still use primitives. Propagate wire
  RowCount/Offset through these records and their factories/callers. ListPage is
  generic: cover explicit type applications and omitted defaults in rewrite fixtures.
- ResultsList.pageTotal and its Var[Option[Int]] total signal need consistent count
  typing. Inspect consumers before changing nested type leaves. SourceRenderOpts.total
  is a separate candidate that must be checked for the same purpose.
- LibraryLists combines source/article totals and constructs ListPage. Keep typed
  counts in state; perform arithmetic through a deliberate count operation or local
  mechanism conversion rather than erasing API types at each assignment.
- App's Plural.count call is a rendering boundary analogous to Plural.noun. Configure
  the exact argument conversion; do not use a blanket web-module exemption.
- LeadService.InMemory.leads returns a primitive total in a tuple. Compare the service
  contract, DB implementation and callers before choosing a domain count type or
  an explicit layer conversion. Do not fix one implementation in isolation.
- CompileArticlePipeline.filter compares the returned count and passes it to Found.
  Review Found and its consumers for typed propagation; isolate comparison/logging
  conversion from model storage.
- Factory `of` parameters still contain primitives even where their result records
  are now typed. This remains migration work whether or not compilation diagnoses it.
- Compile tests after production succeeds, retain all 115 selected identities, and
  compare with the two original ArticlesSuite failures. The generic test verifier's
  single historical plugin-failure exception does not authorize ignoring these.
- Rerun project-wide assessment only once actual source/compiled evidence agree.
  Keep all previous inventories, failed previews and original test evidence intact.

Filter.limit/default semantics, Filter.offset and PassageHit's distinct text offset
remain outside the applied batch. Broader model candidates and independent detector
quality are still open. Copy-to-optics automation remains paused.
