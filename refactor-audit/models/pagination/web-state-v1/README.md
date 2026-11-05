# Web count state propagation

Eight files, 25 AST edits. PaginationWebStatePreview scopes every match to an exact
file and declaration/expression shape, requires the inspected build-v2 source hash,
requires exact match counts, rejects overlapping edits and parses every output.
This is a targeted rewrite for a frozen source revision, not a reusable semantic
dataflow detector. A missing or changed source must fail rather than broaden matches.
All eight applied sources match the frozen plan (web-state-verification.txt).

The initial CLI invocation supplied a nonexistent tools/refactor/Empty.scala driver
and exited 1; the afterComplete preview nevertheless completed. The generated plan
and AST evidence were inspected, then the independent apply and verify invocations
succeeded. No application edit was performed by that initial invocation.

Production web compilation succeeds in build-v3. Three server errors remain. The
web-only test compilation observations in web-tests-v1 and v2 preserve five and two
assertion errors respectively. PaginationWebAssertions unwraps the observed values
in five assertions without changing expectations. PaginationWebAssertionWidening
adds Int ascriptions in two Either map callbacks because Iron's Pure refinement is
otherwise retained. An attempted CLI invocation naming a second class reported
Unknown rule and made no changes; the widening rule was then loaded from its own
file and applied successfully. Follow-up verification is web-tests-v3.

Web-tests-v3 passes: web test compilation, Scala.js linking, eight FilterTabSuite
tests and three ListPageSuite tests, zero failures or skips. All 3142 application
source hashes stayed unchanged during the run. The original expected values are
retained; these 11 tests are additional evidence, not a replacement for the original
115-test API/core baseline.

Raw MapListState and unread result counts are adapted at the typed signal boundary;
they remain candidates, not accepted exceptions. SourceRenderOpts findings counts,
API factory primitive parameters and the server service/Found contracts remain open.
No project-wide measurement or verified model-fix credit is claimed here.
