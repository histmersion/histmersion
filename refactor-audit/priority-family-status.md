# Priority-family status

## Definition sweep applied: research API

The reusable case-class transformation in AstRecipe converted 207 explicit
primitive leaves across 44 models in 17 production research API files. The
existing ApplyFrozenPreview rule applied definitions-research-2 with source-hash
checks. No caller or test repair was included. Existing changes remain applied.
Configuration contains scope, concept reuse and two collision-resolving names;
it contains no before/after expressions. Local companion ownership is the default.
Shared source citekeys, bibliographic types and article properties are reused.

The fixture exercises existing and absent companions, nested records, Option,
Map key/value roles, Boolean, shared-type reuse, unchanged nonprimitive fields
and a blocked name collision. Generated output matches the expected file exactly;
the expected definitions compile with Scala 3.10.0 and Iron 3.4.1. The final rule
reports six converted fixture leaves and one blocked collision. Research preview
1 exposed Article.Kind and Article.Summary collisions; preview 2 resolves them
through explicit KindValue/SummaryText names and preserves the existing members.

The post-application run definitions-research-remaining-1 validates the full source
hash chain and produces zero patches and zero blocked explicit primitive leaves
in this scope. This is idempotence and syntactic coverage evidence, not independent
semantic coverage or behavioral verification. Recognition covers explicit primitive
names, scala-qualified primitives, applied type arguments and tuples. It does not
resolve aliases/import renames or inspect all possible type syntax, enum cases,
non-case-class models, method signatures or inferred types. Those are not compliant
by implication. Defaults, methods, callers and tests may now fail compilation;
whole-project compilation was deliberately not run as a gate for this sweep.

The 207 converted leaves are not added to the historical repaired-ID count and
are not assumed disjoint from existing inventory entries. Historical identities,
denominators and observations remain intact. The new evidence records owner,
field and container role alongside original source/line/AST and frozen hashes.
Next apply this same transformation to further model scopes, with semantic reuse
decisions in configuration. Compiler repair remains after the definition sweep.

## Execution correction: model definitions before caller repair

The user's revised sequence supersedes the caller-closure work below. Stop
adding expression-specific before/after recipes. The pending acquisition preview
is not applied. Preserve the already applied model changes and their history.

First traverse production case-class model definitions across modules, using the
existing inventory as an index and reconciling it with current source. Replace
primitive model fields, including primitive leaves inside containers, with Iron
newtypes. Default ownership is the record's companion; reuse an established
shared or other-record-owned type when its meaning is known. Equal primitive
representations or equal field names alone do not establish shared meaning.
Uncertain equivalences may remain distinct initially and be consolidated later.

Use a reusable definition transformation, with configuration limited to ownership,
concept reuse and explicit exclusions. Do not configure individual expressions
or caller patches. Preserve containers and distinguish their independent leaf
roles. Detect name collisions, aliases and unsupported shapes explicitly rather
than counting them as migrated. Preserve existing newtypes and constraints;
new types initially use Pure unless the constraint is already established.

Whole-project compilation is deliberately not a gate for applying these model
definition changes. Validate the transformation on representative definitions,
then apply it to all supported eligible definitions. Do not restore changes for
low coverage or expected caller failures. The first milestone is the definition
sweep with a current-source account of converted, remaining and unresolved model
fields, linked to existing identities. It is not completed behavioral repair.

After the definition sweep, repair compilation, move existing construction and
validation rules into the owning newtypes, consolidate duplicate concepts and
remove redundant validation-only objects when their behavior is preserved.
Then repair and run tests, investigate behavioral failures, and remeasure stable
findings. Keep client types unrefined under the existing layer policy. Report
definition conversion separately from verified repairs and automation coverage.

The semantic inventory is a historical index, not current exhaustive evidence:
its snapshot predates recent edits and includes unresolved types. Retain it and
its denominators; reconcile changes without requiring a compiling project or
silently treating unresolved cases as compliant. No new repair credit is claimed
by this execution correction. The historical observations below remain intact.

## Current model batch: seventeen concepts reviewed together

Citation and place caller adaptations are applied through the existing AST
pipeline: caller-server-citations-1 has fifteen matches and
caller-server-places-1 eighteen, across seven production files together. Citation
edges retain typed equality and touched-source sets; legacy graph/contradiction
records receive explicit primitive projections. Place import suppression and
membership checks account for typed input IDs, including both names and parent
relations. HeldPlace and the remaining primitive service/wire records are still
legacy modeling obligations, not accepted exceptions or additional repair credit.
The combined server build reports 211 diagnostics versus 252 previously
(applied-server-build-5). Compilation still fails, so no downstream tests ran.
All 3167 source hashes remained stable during compilation; the cohort's 87 IDs
remain applied-unverified, with identities, denominators and history retained.

Server callback adaptations are applied through caller-server-callbacks-1:
twenty-two reviewed AST matches across eight production files. Event-candidate
comparisons and optics retain typed identifiers, rename callbacks adapt their
input/output types, acquisition forwarding no longer double-wraps typed titles
and authors, and legacy callback boundaries extract explicitly. These are
targeted adaptations, not a newly proven general rewrite. The server compiler
reports 252 diagnostics versus the prior 284 (applied-server-build-4), with none
reported in the eight changed files. Compilation still fails; later db/server
test commands did not execute. Counts are compiler diagnostics, not repaired
policy findings, and do not prove all failures in those files are exhausted.
The original 87 cohort IDs remain applied-unverified with no repair credit.

Database production compilation now passes. caller-db-1 adapts thirteen ASTs
across five files, including both database and in-memory source-alias tuple
callbacks. caller-db-codecs-1 adds existing generic scalar codecs at seven
repository boundaries and a shared List[newtype] codec over the base list codec.
caller-db-codec-tests-1 supplies the remaining candidate codec import, replaces
five raw/refined job-kind SQL constants with typed registry values and adds array
round-trip/constraint tests. The last database compile reports no codec fallback
diagnostics. Raw repository parameter models remain legacy obligations.

The scoped database codec check passes ten tests with none ignored, including
four NewtypeColumns tests on the existing disposable PostgreSQL at port 55573
(applied-db-codec-check-2). Array tests preserve order and duplicates, empty/null
base semantics and constrained-element validation. Its six-source test selection
is explicit in arguments.txt: this is NOT full database test compilation. The
full db test compile remains failed with 135 diagnostics (applied-db-build-2).
Server compilation reached 284 diagnostics after db production compiled
(applied-server-build-3); server tests have not run. Earlier failed observations,
including the sandbox boot-lock failure and missing scoped ColumnProbe dependency,
are retained. These diagnostics are not deduplicated policy findings.

All 87 cohort IDs remain applied-unverified pending full caller closure and
stable-ID remeasurement. No repair credit, denominator change or new accepted
exception is recorded. The existing uncommitted ModelTypeRewrite type-site draft
was not used for these adaptations.

Web production and test compilation now pass, followed by 149 selected behavioral
tests with zero failures or ignored tests (applied-web-build-7). Coverage includes
event playback/markers, map point rendering and tiles, preview cards, routes,
list filters, pagination/polling, activity controls and source editing. Expected
values and ordering remain unchanged; expected IDs/coordinates now use their
corresponding newtypes. All 3167 source hashes stayed stable during verification.
The existing recipe pipeline applied twenty production key/caller matches, two
activity ID adaptations, and twenty-eight test matches. Inline optics errors were
exposed progressively; the final source review included the remaining relevant
web test optics sites, excluding unrelated primitive model fields. Failed build
observations 3 through 6 are retained beside the passing build 7.

These are targeted caller adaptations, not new general automation coverage.
Raw Route/WindowKey/Preview identifiers, list cursor keys, queue fulfilment and
Activity.Entry fields remain legacy modeling obligations, not accepted exceptions.
The 87 original cohort identities remain applied-unverified pending downstream
server/db closure and stable-ID remeasurement. No confirmed findings were removed
from the ledger and no source rollback occurred.

Web caller closure advanced without reverting the applied cohort. The existing
AST recipe pipeline applied thirteen reviewed matches for typed job-kind equality
and display/coordinate boundaries, then four matches carrying JobInfo.Id through
both polling helpers and ApiClient.job. Raw route input is wrapped when entering
the typed client; extraction stays at the existing HTTP endpoint boundary.
The two actual builds preserve all 3167 source hashes and report 22 then 20 web
production diagnostics, against the prior 36 (applied-web-build-1 and -2).
Production compilation still fails, so web test compilation and behavioral tests
did not execute. Remaining diagnostics concern route/window/preview identifiers,
heterogeneous list keys, source fulfilment and activity presentation. These are
targeted caller adaptations, not additional general rewrite coverage or verified
policy repairs. All 87 cohort IDs remain applied-unverified; denominators,
exceptions and historical findings are unchanged. No source rollback occurred.

Core production and test compilation now pass, followed by 115 selected behavioral
tests with zero failures or ignored tests (applied-core-build-2). The run covers
acquisition candidates, empty answers, gazetteer parsing, event matching, seed
classification, browser downloads, enrichment, sweep rules and acquisition text.
caller-core-tests-1 applies seventeen AST matches across eight test files with
exact hash verification. Expected values/order and the existing seed dataset
checks are retained; author/year optics updates were included in the same pass.
Unrelated same-named model fields were excluded after source review.

These targeted test adaptations do not add verified policy repairs or change the
87 confirmed cohort identities. PlaceReview.Evidence's raw identifier remains a
legacy boundary, explicitly supplied by the fixture. Core passing does not prove
server caller closure; the server candidate construction sites and the remaining
web failures still need migration and verification. The previous failed API/core
test observations below remain historical evidence. No source rollback occurred.

API JVM test compilation now passes, with 109 selected behavioral tests passing
and none ignored in applied-api-tests-build-3. These cover PlaceEdits, Places,
Storage, Write, AcquireMarkdown, WireSnapshot and JobKindsRegistry. The existing
wire snapshots and patch clear/retain/absent semantics pass without expected-value
changes. caller-api-tests-1 adapts fifteen AST occurrences; caller-api-optics-1
adapts eight and caller-api-optics-2 the missed lowFreeBytes threshold. These are
targeted test adaptations through the existing pipeline, with exact applied hash
verification. Assertion values, predicates, test counts and snapshots are retained.
Place.Hit's raw ID constructor remains an explicit unresolved modeling boundary.

The first two build observations retain later optics expansion failures, including
the lowFreeBytes update missed by the initial field-name search. They are not
independent detector refinement rounds or evidence of general optics coverage.
After API tests passed, core/Test/compile reached sixteen type mismatches in
CandidatesSuite, EmptyAnswerFieldsSuite, GazetteerSourcesSuite, EventLinksSuite,
PlaceSeedKindsSuite and AcquisitionSweepRulesSuite. Core behavior tests remain
blocked; web and server closure remain unresolved. No new confirmed finding IDs,
repair credit or overall automation percentage is inferred from test adaptation.

Core production compilation now passes in applied-core-build-1. caller-core-1
applies sixteen exact AST recipes across seven files using the existing pipeline;
applied hash verification passes. Acquisition candidates retain typed title,
year, author and venue values. Job control consults the typed registry entry.
DOI parsing, lexical title/author comparison, sorting and tile serialization
extract only the primitive inputs needed by those operations. Existing raw
comparison/helper signatures and the candidate's raw DOI remain outstanding
modeling work; these edits are not an exception or a compliance verdict for them.

The core test gate reaches api/Test/compile, which reports fifteen type mismatches
in PlaceEditsSuite, PlacesSuite, StorageSuite and WriteSuite. Core behavioral tests
therefore did not execute. Candidate constructors in downstream server/test code
also need propagation of the newly typed fields. The applied migration remains
in place. This is targeted caller repair, not new automatic-repair credit or a
new independent detector sample. The 87-ID cohort remains unverified. Web's last
observed 36 diagnostics remain unresolved; db/server closure has not been proved.

CLI now passes production compilation, test compilation and 95 selected behavioral
tests (zero failed/ignored), recorded in applied-cli-build-3. The selected suites
cover Calibre, job following, patch assembly, directory sizes and local storage.
caller-calibre-1 types the citekey in Linked/Replaced/Updated JSON records and
all raw constructor inputs; an already-typed source citekey passes through.
caller-cli-flags-1 types the metadata merge helper and wraps the title flag on
entry. caller-cli-tests-1 preserves the numeric predicates and exact 1234/100-byte
expectations while adapting assertions to ByteCount. All five changed production
and test files were edited through the existing AST recipe/application pipeline,
with exact applied-hash verification. These are targeted caller repairs, not a
new detector refinement or a general-purpose transformation claim.

applied-cli-build-1 retains the later optics expansion failure exposed after the
Calibre fix; applied-cli-build-2 retains production success followed by two test
type errors. No assertion was removed or relaxed. The whole cohort is still
unverified: core's last observed 22 diagnostics and web's 36 remain, and no fresh
stable-ID repair measurement has been credited. Production/test counts and the
87 confirmed cohort identities remain separate from these compiler/test counts.

Caller follow-up caller-state-1 is applied: ten exact AST recipes across
EventLayer, EventPlayback and MapLabels. Event identity collections/helpers now
use Event.Id; coordinate anchor keys remain String. slots is generic in its key
type, preserving the input key through its predicate and returned map. Primitive
extraction is limited to map geometry, marker coordinates and DOM label keys.
The existing AstRecipe and ApplyFrozenPreview tools generated/applied the edits;
hash verification passes. This is targeted rewrite treatment, not evidence of a
general-purpose collection rewrite or additional confirmed finding identities.

applied-build-2 reports 36 web diagnostics, down from 51, with none in those
three files. Core and CLI repeat cached compile failures; their previous 22/1
diagnostics remain unresolved, not freshly recounted. Compilation still fails
and behavioral tests have not run. EventPlaybackSuite contains stacking order,
visibility filtering and anchor checks, but needs the remaining web/test caller
repairs before it can validate this change. The 87-ID cohort remains applied and
unverified; no repair credit or overall automation percentage is added.

The 87-field / seventeen-concept batch is now APPLIED to this worktree as an
incomplete migration. ApplyFrozenPreview applied preview-13's frozen plan;
applied-verification.txt confirms all 233 files exactly match its hashes.
This includes production definitions/callers and test construction changes, not
233 confirmed defects. Twelve newtype definitions are generated and five existing
types reused. The 87 reviewed production finding IDs are preserved; no test
violation denominator or additional repair credit is inferred from changed files.

Compilation is a completion gate, not an application gate for this isolated
worktree. applied-build-1 records compilation against the actual applied sources.
It confirms API JVM, API JS and files compile; CLI reports one diagnostic, core
22 and web 51. Test/compile did not run after the failed compile. All 3,167 source
hashes stayed stable during compilation (eight newly created source files).
Two generated files retain an extra blank line at EOF (Place.scala and
Storage.scala); the whitespace check reports these, and frozen content is preserved.
The batch stays applied while reusable rewrites address remaining failures;
there will be no restoration of pre-migration sources or compiler products.
The broader inventory is not being expanded as a prerequisite. Existing histories
and denominators remain intact. The 87 findings are applied-but-unverified and
still outstanding until relevant tests and stable-ID remeasurement pass as well.
The uncommitted type-site extension to ModelTypeRewrite is unfinished tooling and
was not used to produce this applied checkpoint.

The following preview checkpoints are historical evidence.

Latest checkpoint: preview-13 retains 87 reviewed fields / seventeen concepts in
233 overlay files. build-preview-5 passes API JVM, API JS and files compilation;
CLI reports one diagnostic (Calibre JSON's raw citekey), core 22 and web 51.
The nine job-polling CLI diagnostics are absent after propagating JobInfo.Id
through Client.job, IngestCmd.pollJob and JobFollow.job's unary fetch callback.
That comparison is compiler evidence, not nine newly repaired policy findings.
The API request unwrap is scoped to Client.job; callback, recursive polling and
caller values stay typed. Two additional reviewed rendering boundaries cover
tile event serialization and article citation text. No new domain-wide unwrap
permissions were added.

The existing rule now handles a single ordinary parameter clause with leading
or trailing `using` clauses and configured unary callback inputs. Exact-output,
compilation and behavioral fixtures pass in rewrite-leading-context.txt and
rewrite-callback.txt. Explicit/implicit contexts, named calls, typed forwarding,
callback invocation and raw-value rejection are covered; prior checks remain.
Ordinary multiple value clauses, non-unary callbacks and arbitrary explicitly
typed lambda adaptation remain unsupported. Callback bodies are not implicitly
converted to primitives. This does not establish whole-project caller closure.
The 87 confirmed cohort findings remain outstanding and uncredited. Application
sources were unchanged; baseline-restore-3 passes compile and Test/compile for
all 3,159 original source files, restoring the pre-rewrite compiler state.
Remaining work includes typed collection state/factory arguments, model/helper
propagation and the Calibre output record. Tests and stable-ID remeasurement
still gate applying this batch. All other family statuses and denominators persist.

Current checkpoint: preview-12 retains all 87 reviewed fields / seventeen concepts
in 228 overlay files. build-preview-4 passes API JVM, API JS and files compilation;
CLI reports ten diagnostics, core 24 and web 51. The prior web count was 99;
the newly reached core/CLI failures prevent interpreting that reduction as overall
caller closure. Test compilation and downstream branches remain unverified.
Original application sources were unchanged throughout. baseline-restore-2 passes
compile and Test/compile for all 3,159 original source files.

The existing ModelTypeRewrite now supports a reviewed ordinary parameter clause
followed by `using` clauses. It rewrites the value arguments once, preserves
explicit context arguments, and rejects ordinary additional parameter clauses.
rewrite-context.txt records exact-output, baseline/expected/rewritten compilation,
runtime, raw-value rejection and unchanged-source rejection checks. The fixture
adds implicit/explicit contexts, named arguments and typed forwarding without
weakening prior assertions. This is shared rewrite capability, not repair credit.

Next caller-closure work is grouped by transformation: typed helper parameters
and callback signatures (including leading `using` clauses); typed collection
state and factory type arguments; and reviewed rendering/serialization boundaries.
JobFollow's callback signature and leading context clause remain unsupported,
so IngestCmd.pollJob is not yet configured alone. EventLayer's coordinate anchor
keys must remain distinct from its Event.Id values and identity-indexed maps.
Domain model comparisons/normalization are not blanket primitive boundaries.
These exclusions are outstanding work, not accepted policy exceptions. The same
87-ID cohort must pass compile, relevant behavior tests and stable-ID remeasurement
before it receives repair credit. No production or test repairs are credited here;
the reviewed denominator and all other family statuses remain unchanged.

Earlier checkpoints follow; their failed observations are retained.

Preview-11 passes the 487-source API production overlay compile. It contains 87
reviewed fields across seventeen concepts and changes 220 overlay files. Nested
Option/List/Set field paths and configured optional normalization now have exact
output, compilation and behavioral fixtures. Tests distinguish None from Some(Nil),
preserve author order, reject raw nested values, and preserve trim/blank/omitted
patch behavior. This is not a whole-project pass or production repair credit.

The metadata review adds five IDs to the prior 82 without changing those rows:
MetadataPatch title, authors, year, venue and DOI. metadata-closure-2 is current;
metadata-closure retains an erroneous duplicate concept label (venue versus the
existing publication-venue), corrected without changing field identities. Review
now rejects conflicting concept labels/base types for one proposed type.
The reviewed model subset is 98 original findings including the eleven pagination
IDs: eight historical fixes and 90 outstanding. The broader denominator remains
unestablished; test findings remain separate and unadjudicated.

The existing CompileBatch verifier now accepts an immutable source overlay and
runs the actual sbt tasks. Attempts build-preview-1 and -2 failed in settings
setup, before source compilation, due to Global scope cycles. build-preview-3
uses explicit project scopes from the evaluated 30-project inventory. Its status
and build.log are authoritative for the downstream build; API overlay success
does not imply JVM/JS, dependent-module or Test/compile success. Behavioral tests
and stable-ID remeasurement remain required before applying/crediting this batch.
build-preview-3 completed: both API JVM and JS compilations passed, then files
reported two diagnostics and web reported 99. Dependent build stages and
Test/compile did not pass. These are compiler diagnostics, including cascades,
not 101 additional confirmed policy violations. Baseline compilation is restored
through the same verifier without an overlay before further semantic rewrites;
baseline-restore-1 records that result. Next repairs must address rendering,
string-based editor state and typed helper propagation across these consumers.
baseline-restore-1 passes compile and Test/compile for the unchanged 3,159-source
universe, restoring the pre-rewrite compiler state. This is baseline verification,
not a pass for the preview. No build process remains running at this checkpoint.

Latest observation: preview-9 retains all 82 reviewed fields and seventeen concepts
in 217 overlay files. The shared rule now extracts configured scalar and collection
primitives without leaking Iron refinements into generic sort/sum inference, and
handles typed method results at explicit renderer calls. Fixtures pass exact
output, compilation, runtime results, typed forwarding and rejection checks.
CitationLint's two public citekey sets are typed; its prefix/suggestion routines
use configured lexical boundaries. Storage.total returns ByteCount and existing
Long arithmetic is preserved inside its configured implementation boundary.

API compilation has moved past the eleven ordinary diagnostics in preview-7 and
now reports one later Kindlings macro error in Source.applyPatch: MetadataPatch
still supplies primitive metadata to typed Source fields. Next is propagation of
the same concepts into MetadataPatch, including Option[List[AuthorName]], with
nested-container support and typed normalization. This newly exposed work is not
an accepted exception. One reported diagnostic does not mean only one remaining
change: further macro failures may be masked. Full JVM/JS/dependent compilation,
behavioral tests and stable-ID remeasurement remain outstanding. Originals are
unchanged and no production repair is credited. See preview-8, preview-9,
rewrite-extraction.txt and rewrite-results.txt under models/reviews/multi-concept-1.

Latest observation: preview-7 extends the same cohort from 79 to 82 confirmed
fields by adding Edge.src, Edge.dst and Finding.citekey. The original 79 rows and
IDs are unchanged (zero-context diff shows only the three additions). With the
eleven original pagination IDs, the reviewed subset is now 93 original findings,
eight historically fixed and 85 outstanding. No broader denominator is claimed.

The shared rule now propagates immutable Set elements through configured parameters,
results, defaults and calls. Its exact-output, compile and behavior fixture proves
duplicate collapse, membership, defaults, typed forwarding and raw-set rejection.
Seven Source.Write helper signatures migrate together, including the alias set;
edgeOf remains the explicit raw-input normalization boundary. This is necessary
caller adaptation, not additional repair credit for unadjudicated parameters.

Preview-7 rewrites 215 overlay files and compiles all 487 API production sources.
The two earlier Source.Write errors disappear, but seven newly exposed citation
uses remain in CitationLint and Source.Put.of, alongside four unchanged storage
arithmetic diagnostics: eleven total. Compilation fails with exit code 1; all
original source hashes remain unchanged. Next: carry typed citation identity
through lint membership and string algorithms, convert deliberately raw input
records at their boundary, and implement typed storage arithmetic. Then run the
full dependent-module/test/stable-ID contract. These are preview findings, not
application repairs. See models/reviews/multi-concept-1/{citation-closure,preview-7,
rewrite-sets.txt}. Historical checkpoints below retain their original denominators.

The existing configured model-review tool verified 79 distinct confirmed primitive
leaves across 17 concepts: bibliography (six), place/event identities (two),
coordinates (two), storage quantities/durations (four), SHA-256 (one) and job
identity/kind (two). Five target types already exist; twelve require definitions.
The source/AST/identity evidence is models/reviews/multi-concept-1/review-1.tsv;
audited-examples.md records parent corrections and detection limits.

These are one multi-concept batch's reviewed seeds, not seventeen separate repair
jobs and not the complete eligible call-site closure. The original eleven
pagination findings are disjoint and retained: this reviewed model subset now
contains ninety original findings, eight historically fixed and eighty-two
outstanding. There is still no migration-wide denominator or percentage.

ModelTypeRewrite now supports configured String/Int/Long/Double bases and scalar,
Option and List shapes for reviewed fields, parameters and results. One shared
seven-concept Iron fixture passes exact expected-output comparison, compilation,
runtime value/evaluation checks and compile-time concept separation. Wrong base
and constructor order are rejected without source changes; the original Int fixture
also passes unchanged. This is shared rewrite support, not production repair credit.

The existing Impact rule now reads this review configuration. It verifies all 3,159
inventory source hashes and finds 27 reviewed records referenced in 493 files across
nine modules: 9,685 reference rows, including 1,058 reviewed-member references and
964 one-hop caller candidates. These are overlapping evidence units, not new
violations or a proven transitive dataflow closure. The first impact observation is
retained; impact-2 deterministically selects the most specific nested owner instead
of an arbitrary parent prefix, without changing the source/reference denominator.

The configured Preview rule now generates all twelve missing Pure definitions,
including companion-owned types, and rewrites all seventeen concepts together.
Preview-2 changed 196 overlay files; compiling all 487 API production sources
reported 42 errors. Preview-3 changes 201 overlay files and reports 24 errors after
adding configured Option/List rendering boundaries to the shared rewrite. All
3,159 original source hashes remained unchanged. These compiler diagnostic counts
are not violation counts or verified repairs; subsequent errors can emerge after
earlier failures are resolved. Both failed compile observations are retained.

The extended fixture passes exact output, compilation and behavior, including
container order, absent values, one evaluation of the receiver and typed forwarding
inside a configured boundary. Scalar and container boundary permissions are
separate. No blanket implicit primitive conversion was introduced.

Next: resolve the remaining normalization, typed helper propagation, storage
arithmetic and rendering shapes for the whole cohort, then verify dependent
modules and tests through the existing pipeline. The preview-3 AssetLinks boundary
key incorrectly names library instead of media; it matched nothing and remains
uncorrected in that frozen observation. API overlay compilation does not verify
Scala.js or dependent modules. PreviewCompile records compiler failure but its
current wrapper exits zero; it is diagnostic evidence, not a pipeline success gate.
Nested containers remain unsupported. See models/reviews/multi-concept-1/
{rewrite-boundaries.txt,preview-2,preview-3} and the versioned configurations.
Subsequent boundary review and configuration reduced diagnostics to seven in
preview-4. Numeric widening initially exposed a placeholder-grouping defect in
preview-5; the new comparison fixture reproduces that shape and the corrected
same-base extraction passes exact-output, compile and behavior checks.
Preview-6 now reports six diagnostics across Source.Write's citation helpers and
Storage.total/onVolume's arithmetic. It retains all 79 fields / 17 concepts in
207 overlay files; all original hashes remain unchanged. This supersedes the
next-action list above: remaining work starts with typed helper propagation and
typed arithmetic, followed by the full downstream compile/test/identity contract.
PreviewCompile now propagates failures as exit code 1 (verified in previews 4–6),
correcting the historical wrapper limitation recorded above. See
models/reviews/multi-concept-1/{preview-4,preview-5,preview-6,
rewrite-numeric-placeholders.txt}. No application repairs are credited.
Application fixes in this purpose-review step: zero. Detector refinement rounds
consumed: zero. Uncertain concepts and all other policy families remain tracked.

## Latest verified repair: method-body clock propagation

The configurable ProjectAssessment pipeline completed its first repair. One
configured transformation changed five production and two test files, propagating
the clock through thirteen callers and removing two confirmed production capability
IDs (DiskLru and JavaRangeHttp). All 3,159 source/test files compile; the same 47
selected tests pass before and after with no skips. Exact reconciliation permits
only those two removals. Capability/isolation histories report zero new/reopened
findings. See server-clock/README.md and body-pipeline-1 for original IDs and evidence.

Current capability measurement: 77 anchors / 94 occurrences. Confirmed outstanding:
15 anchors / 16 occurrences. Candidates: 48 anchors / 64 occurrences. Unresolved:
13 adapter operations. Non-violation: one. Two plugin-design obligations and eight
timing-permission occurrences remain separate; test policy remains unmeasured.
No exception was added, no denominator was reduced, and no detector refinement
round was consumed. Inventory acceptance and overall automation coverage remain
unestablished. Copy automation stays paused.

The subsequent model cohort and shared rewrite extension are recorded above.
Candidate totals are not a ready batch or a confirmed denominator.

## Current bounded acceptance disposition (5 November, a06cb734)

This acceptance snapshot predates the two verified repairs above; its limits and
sample results remain applicable. migration-rounds.tsv carries current residuals.

The bounded review has assigned an acceptance disposition to all fourteen policy
families covering the original 43 goals and twelve slices. **Inventory acceptance
is not complete.** Small independent samples, missing semantic classifiers and
unmeasured scopes prevent that claim. No detector refinement was performed in this
pass. Insufficient scopes are frozen with limits; copy remains at its two-round
limit and further copy automation is paused.

`migration-rounds.tsv` is the consolidated family ledger. Original CAP, ISO,
transaction, pagination and repair histories retain their identities and evidence;
`audited-examples.md` records the independent samples and source adjudications.
The table below summarizes dispositions, not a second finding register. Counts in
different units must not be added.

| Family | Confirmed outstanding / candidate and unresolved scope | Acceptance and treatment |
|---|---|---|
| Model concepts/invariants | Three original pagination IDs; 30,393 production member primitive candidates, 62,229 all-scope leaves, 46,914 unresolved leaves | Partial, frozen. Two fresh compliant examples, no independent positive. Pagination retains bulk, targeted and design treatments; purpose equivalence remains unresolved. |
| Layer boundaries | Zero confirmed in the reviewed production direction set; 111 accepted contributions; tests: 85 non-violations, 130 candidates; 75 ambiguous owners and 182,326 unclassified direction rows | Partial, frozen. Reviewed directions do not establish whole-model isolation or unwrap compliance. |
| Type/feature ownership | ApprovalModes planned operation confirmed; 645 plan rows, 1,392 objects, 5,903 owner rows | Partial, frozen. Targeted owner mapping needed; stale destination names are unresolved, not repair instructions. |
| Repository visibility | Previously confirmed public GoalRepo remains; eight configured diagnostics are not eight adjudicated defects; 490 role candidates | Partial, frozen. One earlier positive and one fresh negative; consumers and generated roles limit coverage. |
| Transaction ownership | MediaJobs DataSource ownership confirmed; zero supported repository diagnostics; two dynamic SQL cases unresolved | Partial, frozen. Caller policy has a missed positive; manual/design treatment. Original 85% sampled recall remains recorded. |
| Capability/plugin access | 17 confirmed anchors / 18 occurrences; 48 candidate anchors / 64 occurrences; 13 unresolved adapter operations; one non-violation. Upstreams dissolution and host AI extraction are two separate design obligations | Partial, frozen. Production scope only; tests unmeasured. Supplied capabilities are compliant only in the inspected expressions. Eight timing-permission occurrences remain separately recorded. |
| Service implementations/tests | BasemapService is one confirmed aggregate design obligation; 348 obligation rows are not independent defects | Partial, frozen. Structural sample: one TP, one TN; behavioral parity and backend isolation unproven. |
| Derivation | Zero plain schema bridges; production: 151 Kindlings, 42 DB, 19 other derivation calls, 539 givens; tests: two Kindlings, 31 other calls, 141 givens | Partial, frozen. These are candidates, not confirmed violations. No fresh independent pair; colocation and custom semantics unresolved. |
| Record updates | 107 confirmed production copies; 58 resolved test matches; unresolved owners: 52 production, 97 test | Production sample accepted with limits (100% sampled precision/recall); test recall 86.9565%, insufficient at 2/2 rounds. Automation paused. |
| Accumulator algorithms | Visible allocation candidates: 154 production, six test; indirect effects unresolved: 1,348 production, 764 test | Candidate-only, frozen. No independent classifiable pair; no repair eligibility inferred. |
| Comments/naming | One confirmed MergeFold comment block; 15,960 comment candidates | Candidate-only, frozen. Targeted treatment for that block; no justified-negative sample, no production/test split in the summary. |
| Build/config isolation | One confirmed Sloth placement obligation; zero configured forbidden edges; 49 config resources / 782 values | Partial, frozen. Targeted placement treatment; test and published-plugin policy unresolved. Evaluated coverage: 30 projects, 1,504 Compile/Runtime entries. |
| Vocabulary/rendering | MediaMarkdown ownership and DataCredit replacement confirmed; 1,377 string rows, 347 enums, 50 Markdown-signature rows | Candidate-only, frozen. Renderer targeted; Attribution design work. Display semantics and callers unresolved. |
| Process/enforcement | External compatibility/live-data evidence unresolved; 100 legacy exception rows are not approved exceptions | Frozen awaiting external evidence. No new exceptions or live-data access. |

**Confirmed violations repaired in this acceptance pass: zero.** The preceding
clock batch repaired eight IDs / nine production occurrences, verified below.
Historical reporting corrections restore eight pagination fixes and 523 schema
bridge fixes (521 production, two tests); they are not new repair credit. Their
original test failures and scope limitations remain in their histories.

**Remaining confirmed violations:** listed by family above and by original identity
in the ledger's linked evidence. The pass confirms nine existing obligations across
ownership, transactions, services, comments, build and plugins/models; these have
different units and are not added to occurrence counts. Discovery is not a newly
introduced regression. No migration-wide confirmed denominator is established.

**Automation coverage:** no overall percentage established; the 90% target remains.
Source traversal covers 3,159 files in 28 modules, but traversal is not detector
acceptance. Independent samples were bounded to 12 model, nine structural and 12
effects/build files. Several families lack a usable positive/negative pair. No new
blanket exception was accepted and no unsupported case was marked compliant.

ProjectAssessment now composes CompileBatch, BatchTestVerification and existing
Scalafix rewrite/history tools through one configurable pipeline. Configuration-only
validation and rejection of reused observations pass; CompileBatch now propagates
failed sbt exits, verified with a deliberately invalid task and unchanged hashes for
all 3,159 application sources. The first complete fresh repair through the pipeline
is now verified above. The pipeline tooling itself receives no repair credit.
Use compilation, selected behavioral tests and stable-ID remeasurement as the
standard contract. New batch-specific runners, verifiers or report frameworks
require explicit justification and user approval. Select a reusable transformation
across all eligible confirmed findings; record exclusions before applying it.

## Closed server-clock batch and preserved execution gate

The following is the closeout recorded before the bounded review above. Its pending
acceptance-pass instruction is historical; the current disposition and remaining
pipeline gate are stated above.

The active server-clock batch is closed. Existing verification confirms eight
original finding IDs / nine production occurrences repaired by one configured
semantic rewrite across five production files, including the changed constructor's
caller. Full compilation covers 3,159 source/test files; 38 selected test identities
pass with no skips. The generated fixed-clock metadata probe fails before the
production rewrite and passes after it. See `server-clock/verification-1/status.txt`.

Fresh capability measurement is 79 anchors / 96 occurrences, with no new/reopened
IDs or multiplicity changes. Review-3's remaining confirmed set is 17 anchors /
18 occurrences; 48 candidate anchors / 64 occurrences, 13 unresolved adapter
operations and one pure separator non-violation remain. Earlier verified repairs
and their original IDs remain in their existing histories. No blanket exception
was introduced. Primitive counts remain 30,393 production member leaves, 62,229
all-scope leaves and 46,914 unresolved leaves.

**Inventory acceptance completed:** no. The readiness register still marks 42 of
43 goals as partial/unmeasured. Full source traversal is not accepted semantic
coverage or independent precision/recall evidence.

**Confirmed violations repaired:** this batch's eight anchors / nine occurrences
are verified. Historical family repair counts remain separately recorded in
`migration-rounds.tsv` and linked original-ID evidence; incompatible units are not
summed.

**Remaining confirmed violations:** the capability subset above remains outstanding;
the migration-wide deduplicated confirmed inventory is not yet consolidated.

**Automation coverage:** no overall percentage is established. Tooling, artifacts
and repeated scans do not count as repairs. The 90% automatic-repair target remains.

No further repairs start before a bounded acceptance pass for all fourteen families
in migration-rounds.tsv, spanning the original 43 goals and twelve historical slices.
Consolidate into that existing ledger and this report, retaining original identities
and append-only evidence. Freeze insufficient detectors with explicit limits within
the existing two-round budget. Consolidate existing tools into one configurable
pipeline; additional batch-specific runners/verifiers/report frameworks require
explicit user approval. Current batch tools below predate this execution correction
and are not a template for continuing the proliferation.

## Historical observations (superseded counts retained)

### Whole-project refresh and capability treatment

The integrated assessment passes on 3,159 source/test files in 28 modules.
Capability and isolation identity gates have zero new/reopened findings; capability
multiplicities are unchanged. A stale SourceRepo source-example line was corrected
only after verifying the method's complete AST unchanged; failed and corrected
observations remain preserved. No assertion count or original baseline changed.

Capability production retains all 87 anchors / 105 occurrences. The bounded current
review records 25 confirmed outstanding anchors / 27 occurrences, 48 candidate anchors
/ 64 occurrences, 13 unresolved recording-adapter operations and one known pure
separator non-violation. No repairs, new exceptions or independent precision/recall
acceptance are claimed. Tests remain outside capability policy measurement.
See `capability-treatment/review-3/README.md` for the grouped repair queue and evidence.

Isolation remains 111 production / 215 test relationships: the preceding reviewed
treatment is unchanged (111 accepted production contributions; 85 test non-violations,
130 test candidates), with 75 ambiguous owners and 182,318 unclassified direction rows.
Transaction detection reports zero repository ownership matches, with two previously
unresolved dynamic SQL expressions still outstanding. Primitive measurement remains
30,393 production member leaves, 62,229 all-scope leaves and 46,914 unresolved leaves.
Copy detection remains 108 production / 58 test resolved occurrences plus 52 / 97
unresolved owners; copy automation is paused. These units must not be summed.

The 90% migration-wide automation target and independent detector-quality acceptance
remain unestablished. The aggregate baseline is policy-incomparable; the separate
identity gates, source decisions and verified repair histories retain their meaning.

## Import registry ownership repaired

The library contribution now captures SourceReferrers and supplies it to its own
importer. Generic ImportOptions and BundleService.Wiring no longer transport a
library type. One frozen 12-file AST/token batch updated definitions, composition
and all callers. Both rename paths, their writer transaction and referrer order are
structurally unchanged. All 39 database tests pass before/after, seven supplementary
server registry/mirror tests pass, none skip, and all 3,159 sources compile.

Original-ID reconciliation verifies four production relationships fixed after an
explicit unresolved-to-confirmed adjudication. A test value-to-method identity
replacement has no repair credit; three new test-composition relationships have
exact reviews. The final gate has zero new/reopened findings, with its earlier
unreviewed failure preserved. Current reviewed production: 111 accepted contributions,
zero outstanding confirmed/unresolved. Tests: 85 non-violations, 130 candidates.
Seventy-five relationships have ambiguous owners and 182,318 direction rows remain
unclassified. Primitive counts are unchanged: 30,393 production member leaves,
62,229 all-scope leaves, 46,914 unresolved. Other families retain older checkpoints.

This is manual/design treatment, without bulk-automation credit or independent
detector-quality acceptance. See `import-registry/OWNERSHIP.md` and
`import-registry/ownership-verification/current-treatment.tsv`. Broader module
isolation and the overall 90% target remain unproven; copy automation stays paused.

## Import-registry baseline repaired before ownership changes

Real PostgreSQL import/merge/rename tests exposed ten failures in 39 tests before
the planned ownership rewrite. Two tests failed in API-status decoding; the other
eight used mismatched API/domain enum identities in observations or merge-resolution
selection. Repairing citation decoding exposed another instance in ArticleRepo.
Two frozen AST batches now share the API-status codec in the DB module and correct
test enum references without changing expected assertion values. All 39 test
identities pass, zero skipped; full production/test compilation passes on 3,159
sources. Failed and intermediate 38/39 observations remain recorded.

This is prerequisite regression repair, with **no isolation or 90% automation
credit**. The four import-registry relationships remain outstanding, and no detector
quality/refinement claim is made. See `import-registry/README.md` for the patch chain,
verification and bounded next-batch checklist. The fresh architecture/model scan
covers all 3,159 compiled source hashes in 28 modules. Isolation retains 115 production
and 213 test relationships, zero new or reopened, 77 ambiguous owners and 182,320
unclassified direction rows. Production member primitive leaves remain 30,393
(62,229 all scopes); 46,914 unresolved leaves remain. The one-leaf decrease is not
credited as a model repair. Other family measurements remain historical checkpoints.

## Unread projection and shared AuthorName verified

Personal now uses the shared kernel AuthorName and its own typed display Status.
Library interprets its status enum in SourceReadable and converts to the receiver's
projection. The first duplicate-author-type design was rejected despite passing
mechanical checks; its evidence is preserved as superseded. The final semantic
relocation reuses the existing String/Pure type and updates its codec-test caller.

Full production/test compilation passes on 3,158 sources in 28 modules. Twenty
projection/controller/type tests pass before and after with unchanged bodies; five
codec tests also pass after relocation, all without skips. Fresh scans and patch-chain
reconciliation verify seven original production relationships repaired. Two identity
replacements and two test caller relocations receive no repair credit. Three exact
contributor identities and 25 test non-violations have explicit review records;
the original baseline and failed unreviewed observations remain. No unreviewed new
or reopened finding remains.

The reviewed isolation set now has zero confirmed outstanding production findings,
111 accepted contributions and four unresolved registry relationships. Tests retain
82 adjudicated non-violations and 131 candidates. Seventy-seven relationships have
ambiguous owners and 182,326 direction rows remain unclassified. Primitive candidates
are 30,393 production member leaves, 62,229 all-scope leaves and 46,915 unresolved
leaves; extraction exposes seven storage parameters as member signatures, with no
denominator suppression. Other families retain their prior measurement checkpoints.
This remains manual/design treatment, without bulk-automation credit or independent
detector-quality acceptance. See `unread-projection/README.md` and `reconciliation-2`.

## Media source boundary verified

The library adapter now owns source validation and lookup. Media receives a typed
key or an owned invalid/missing error through MediaSources; its controller no longer
imports library types. One configured AST batch updated the interface, implementation,
controller and all three test doubles. This remains a manual/design treatment applied
mechanically, with no bulk-automation credit.

Full production/test compilation passes. Fifteen tests pass before and after without
skips and with unchanged assertion bodies. Fresh scans cover 3,157 sources in 28
modules; original-ID reconciliation verifies three production relationships repaired,
all other 311 preserved and zero new/reopened IDs.

Production isolation is now seven confirmed (unread projection), 109 accepted exact
contributions and four unresolved registry cases. Tests retain 59 prior non-violations
and 132 candidates. Sixty-nine relationships have ambiguous owners; 182,287 direction
rows remain unclassified. Model candidates are 30,386 production member primitive
leaves, 62,219 all-scope primitive leaves and 46,908 unresolved leaves; no confirmed
model-repair inference is made from the count changes. Other families retain earlier
measurement checkpoints. See `media-source-boundary/README.md` for evidence and limits.

## Acquisition spool ownership verified

The existing relocation rule moved Spool and five caller files into library acquisition
as one configured batch. Full production/test compilation passes on 3,156 files;
all three traversal/layout tests pass without skips. The definition AST is unchanged.
Fresh architecture/model scans and original-ID reconciliation verify three production
relationships repaired, with no unexpected removals and zero new/reopened identities.

Isolation now retains 123 production / 191 test relationships: ten confirmed
production, 109 accepted contributions, four unresolved; tests remain 59 prior
non-violations and 132 candidates. There are 70 ambiguous owners and 182,261
unclassified direction-evidence rows. Model counts are unchanged from the codec
checkpoint. Other families retain their previous evidence dates and limits.
See `spool-ownership/README.md`; no independent detector-quality or migration-wide
percentage claim follows from this relocation.

## Feature codec extraction verified

One configured Scalafix batch repairs 29 reviewed production isolation relationships:
three feature-owned codecs and their five generated consumers move together; the
unused edge codec and reviewed imports are removed. Full production/test compilation
passes, nine codec probes pass before and after with unchanged assertions, and all
89 generic list-codec consumers remain unchanged.

Fresh scans cover 3,156 source files in 28 modules. Original-ID reconciliation records
126 production / 191 test relationships remaining, zero new/reopened IDs and no
unexpected removal. Production treatment is now 13 confirmed (three targeted,
ten manual/design), 109 accepted exact contributions and four unresolved registry
relationships. Tests remain 59 prior non-violations and 132 unreviewed candidates.
Seventy-one relationships have ambiguous owners; 182,252 direction-evidence rows
remain unclassified. These limits remain visible rather than counted as compliant.

Primitive production member candidates remain 30,387; all-scope leaves are 62,221,
with 46,903 unresolved. Transaction, capability and copy retain their preceding
assessment checkpoints below, not a claim of fresh execution in this batch.
Independent precision/recall, live database and native-image behavior remain outside
this evidence. See `codec-ownership/reconciliation-1` and its README.

Next isolation work is the three-relationship Spool ownership move. The ten confirmed
design relationships concern the personal unread projection (seven) and media's source
parse boundary (three); four import-registry relationships still require an ownership
decision. Keep these and other policy families measured while copy automation stays paused.

## Remaining isolation treatment reviewed

All eighteen production files in the current 155-relationship observation now have
bounded source decisions: 42 confirmed outstanding (29 bulk rewrite, three targeted,
ten manual/design), 109 explicitly accepted contribution-interface relationships,
and four unresolved import-registry cases. The accepted set includes the prior 21
AttestationSourceReferrer decisions; no detector findings were removed or suppressed.
Tests retain 59 earlier exact non-violations and 132 unreviewed candidates. All 346
original IDs and their identity-confidence labels remain in the review evidence.

The next codec batch has a 3,149-file source/synthetic consumer scan. It observes
generic array-codec consumers but none for the four domain codecs. A bounded scan
of 9,889 JVM class files then found five generated consumers of three domain codecs,
proving that the source/synthetic scan alone cannot justify their removal. Extract
those definitions with their five derivation sites; the edge codec has no observed
external compiled use and needs a guarded removal probe. No repair or independent
detector-quality credit is claimed by this review. See
`isolation-triage/review-2/README.md` and `codec-ownership/README.md`.

## Shared Title ownership repaired

One configured semantic relocation batch moved the existing domain Title to the
shared kernel and updated fourteen consumer files. Full production/test compilation
passes; all fourteen selected codec/personal-model tests pass without skips.
Fresh isolation/model scans cover the same 3,149 source files as the build.

Reconciliation verifies 36 production dependency relationships repaired, representing
one ownership defect; eight test relationships are caller propagation without repair
credit. All other 346 identities remain, with zero new/reopened findings. Isolation
candidates are now 155 production / 191 test, with 81 ambiguous owners. Kernel-target
references are explicitly reviewed, not silently accepted by the generic detector;
unclassified-direction evidence increases from 182,152 to 182,218 rows.

Model counts are unchanged: 30,387 production member primitive leaves, 62,218 across
all scopes and 46,893 unresolved leaves. Transaction/capability/copy evidence retains
the preceding full assessment below. No new exception, detector refinement round,
independent quality acceptance or migration-wide percentage is claimed. See
`shared-title/README.md` and its original-ID reconciliation. DB runtime/native-image
checks remain outside this batch's evidence.

## Full assessment after clock repairs

The assessment completed on 3,149 source/test files in 28 modules after full
production/test compilation. All fixture and inventory-coverage checks passed;
source, policy and resource fingerprints remained stable during the run. The
original aggregate baseline remains unchanged and policy-incomparable. The default
`no-regression-gate=true` status is not an enabled aggregate comparison; the two
explicit identity gates below provide the bounded regression evidence.

| Family | Fresh measurement | Repair history and limits |
|---|---|---|
| Transaction ownership | Zero supported repository ownership/factory diagnostics; two unresolved SQL expressions | TX-0001–TX-0004 repair evidence retained; no new DB/runtime proof or independent recall result |
| Capability access | 2,377/2,377 production files; 105 occurrences in 87 anchors; 31 ambiguous occurrences; zero new/reopened anchors or multiplicity changes | Confirms the 147 → 105 reduction reconciled by the two clock batches; 42 reviewed occurrences fixed. Tests, callbacks and dynamic dispatch remain outside this gate |
| Module isolation | 191 production / 199 test candidate relationships; 106 ambiguous owners; zero new/reopened identities | Previous 54 production repairs and explicit classifications retained. Unclassified-direction evidence is now 182,152 rows, previously 181,915; no reduction or compliance claim |
| Primitive models | 30,387 production member primitive candidate leaves; 62,218 across all scopes; 46,893 unresolved leaves | Production count unchanged; all-scope primitives increased by seven and unresolved leaves by twenty. Pagination's eight fixed / three outstanding remains a preceding verified checkpoint, not a newly executed eleven-ID repair check |
| Copy updates | 108 production / 58 test resolved occurrences; unresolved owner evidence retained | No copy rewrite or detector refinement; automation remains paused |

Capability and isolation observations are retained at
`capability-history/observations/2026-11-05T03-56-34.487304Z` and
`isolation-history/observations/2026-11-05T03-56-36.979728Z`.
The preceding assessment is archived at
`assessment/history/2026-11-05T03-48-36.887295Z`.
No denominator reset, new exception, detector-quality acceptance or migration-wide
automation percentage is claimed. The next bounded ownership proposal is in
`shared-title-next-batch.md`: review the existing shared Title concept, then relocate
its definition and callers together through a tested semantic rule.

## Monotonic and polling batch wired

All thirteen remaining reviewed timing occurrences are replaced through supplied
Timer/Pause capabilities. Full production/test compilation and 24 core timer/passage
tests pass. The production capability scan covers 2,377 files: 118 → 105 occurrences.
Identity reconciliation finds the exact reviewed removals, no other additions and
no changed residual multiplicities. All 42 reviewed clock occurrences are repaired.

Two adapter clock reads and six composition selections remain explicit allowed
boundary evidence. A new factory restriction rejects direct concrete adapter access
in domain fixtures. Its prefix-prototype false positive and exact-symbol correction
are preserved. Capability refinement round 1/2 is consumed; independent acceptance
remains unestablished. Database/query/backup runtime and the full multi-family history
gate have not been rerun. See monotonic/README.md and monotonic/reconciliation-1.

## Wall-clock batch verified

Scalafix repaired all 29 reviewed production wall-clock occurrences (28 anchors)
and propagated their required dependencies through callers. Full production/test
compilation and 21 focused tests pass, with no test skips. A fresh capability-only
scan covers 2,373 production files: 147 → 118 occurrences. Identity reconciliation
finds exactly the reviewed removals, no new identities and no changed residual
multiplicities. All 31 current source assertions and the unchanged clock fixtures
pass. The repaired default's assertion correction is documented and chained into
history; no ban or permission changed.

Thirteen reviewed clock occurrences remain confirmed design work. Other families
retain preceding measurements; the full multi-family assessment/history gate has
not been rerun. Independent family acceptance and test capability policy remain
unestablished. See wall-clock/README.md and wall-clock/reconciliation-1.

## Domain clock treatment checkpoint

Source/AST review confirms 42 existing production clock occurrences in 38 anchors
across 15 domain files: 29 wall-clock sites for a bulk rewrite, 11 monotonic timer
sites requiring a supplied timer design, and two backup-polling deadline reads.
These are newly adjudicated existing candidates, not new code regressions.
No fixes, exceptions, independent quality acceptance or detector refinement rounds
are claimed. All other 87 anchors from the frozen 125-anchor observation remain
preserved. See capability-treatment/review-2/README.md for evidence and batch
requirements. Prior review and repair histories remain unchanged.

## Transaction source acceptance checkpoint

A source-only audit plus parent counterexample found a fourth confirmed transaction
guard defect: nested SQL comments could hide COMMIT from Query.check. The guarded
Scalafix repair passes all 29 Query tests; all three added tests failed before it.
TX-0004 and its discovery/repair events are added without resetting TX-0001–TX-0003.
This is a verified lexical guard fix, not database execution proof.
Full production and test compilation pass across 3,143 source files with unchanged
hashes during the build.

The independent negative sample contains ten distinct production methods in six
unchanged files, with zero transaction diagnostics in the frozen preceding scan.
No independent positives means recall remains undefined. The auditor's initial
incorrect Query judgment and subsequent correction are preserved. The two dynamic
SQL cases stay unresolved; no exception or detector acceptance is inferred.
See transaction-query-comments/README.md. Other families retain the measurements
below from before the two-file Query repair; no fresh aggregate scan is claimed.

## Fresh measurement after pagination migration

The assessment completed on 3,143 files in 28 modules. Compilation and measurement
source fingerprints agree. The original aggregate baseline is unchanged; its policy
fingerprint is now incomparable, so this is **not** a passing aggregate no-regression
comparison. The capability and isolation identity gates separately pass with zero
new/reopened identities. No detector refinement round was spent on this checkpoint.

| Family | Current bounded evidence | Verified repair / outstanding | Limits |
|---|---|---|---|
| Transactions | No supported ownership diagnostics; two dynamic SQL evidence cases remain | Three previously verified ownership repairs retained | Dispatch, callbacks, test policy and independent acceptance remain unestablished |
| Capabilities | 147 production occurrences, 125 anchors; no new/reopened identities or multiplicity changes | Prior repairs retained; this batch grants no new exceptions | 43 identity-ambiguous occurrences; tests and dynamic effects outside this gate |
| Isolation | 191 production and 199 test candidate relationships, no new/reopened identities | Prior 54 production repairs retained; prior classifications/exceptions unchanged | 106 ambiguous-owner relationships; 181,915 unclassified-direction evidence rows |
| Primitive models | 30,387 production member primitive candidate leaves; 46,873 unresolved leaves across all scopes | Original pagination review: **8 of 11 fixed, 3 outstanding**, production only | Candidate-count reduction is not repair credit; independent family precision/recall still unestablished |
| Copy updates | 108 production / 58 test resolved occurrences; 52 production / 97 test unresolved owners | Prior 334 production / 1,529 test repairs retained; 107 confirmed production residuals plus one non-violation | Automation remains paused at 2/2 rounds; failed test recall is retained |

The pagination ledger retains original IDs and direct newtype evidence, and rejects
a reopened ID even when a substituted ID keeps aggregate fixed counts equal. Its gate
covers only the eleven reviewed findings. See models/pagination/measurement-1.md.
This is not 90% migration completion or whole-family detector acceptance.

## Pagination definitions and callers compile project-wide

Build-v7 passes both full production and test compilation across 3,143 source files,
with source hashes unchanged during compilation. The shared domain RowCount now
flows through article Found/Evidence/evidence(...) and the lead repository/service
tuple. The reusable ModelTypeRewrite handles reviewed method parameters, declarations,
defaults and callers; exact-output, runtime and type-separation fixtures pass. An
invalid reviewed parameter list fails before rewriting. The rejected cross-layer
conversion preview is preserved and its patch-composition defect is covered by a fixture.

The original API regression set retains all 115 identities: 113 pass, the same two
ArticlesSuite failures have exactly unchanged failure text, and no tests skip. The
additional CompileArticleSuite, EmptyAnswerFieldsSuite, VocabularyWireSuite and
LeadServiceInMemorySuite contribute 31 passing tests, no failures or skips. DB-backed
runtime and HTTP tests have compiled but have not been run in this checkpoint.
Seven additional in-memory service/controller suites pass all 28 tests without skips;
see models/pagination/runtime-checkpoint.md for the exact suite counts and command.

The assertions were adapted through Scalafix, preserving numeric expectations and
test identities. The new domain/API conversion points preserve the distinction
between layers. Autopilot DraftFacts.hits, EventLead.List.total, API factory parameters,
Filter.limit/offset and PassageHit.Offset remain explicit migration work. Compilation
success does not make them compliant. Project-wide remeasurement is complete above;
no new detector-quality or whole-migration automation claim is made here.

## Web count state propagated; server compilation remains open

Eight production files now propagate RowCount through UI signals, private state and
combined source/article counts. The source-hash-guarded Scalafix preview records 25
AST changes and matches the applied files exactly. Rendering/arithmetic unwraps are
local; raw map-list and unread-list counts have explicit adapters and remain migration
candidates. These are targeted rewrites, not evidence of generic dataflow inference.

Build-v3 compiles web production successfully; three server errors remain in LeadService
and CompileArticlePipeline. The first two web-only test observations retain five then
two assertion type errors; their follow-up is web-tests-v3. Existing test expected
values and identities are retained. Web-tests-v3 passes all 11 selected tests (eight
FilterTabSuite, three ListPageSuite), with zero failures or skips; web test compilation
also passes. Full production/test verification, factory parameter
propagation and remeasurement remain required. No migration-wide verified-fix count,
detector quality result or baseline denominator changes in this checkpoint. Transaction,
capability, isolation and copy-family statuses below remain unchanged.

Earlier checkpoints below are historical.

## Web pagination records propagated

A second six-file Scalafix batch is applied and hash-verified: TabPage, TabState and
ListPage now carry RowCount/Offset through their records, defaults and constructors,
with FilterTab's collection-size boundary and affected test constructors adjusted.
Explicit generic applications and already-typed external values are supported. A
two-stage compiled fixture verifies forwarding without stale-source bypass, including
forwarding inside a configured arithmetic boundary. The first faulty preview is
retained unapplied; its patch-order interaction was fixed without weakening expected
output.

Actual build-v2 still fails: seven reported web errors and the same three server
errors. Test compilation has not run; subsequent compiler phases may reveal more.
Remaining work includes signal/private-variable count types, presentation adapters,
combined counts, and server tuple/Found contracts. No verified-fix count or new
project-wide measurement is claimed while compilation is incomplete.

## Applied pagination types: dependent compilation in progress

The frozen nineteen-file Scalafix batch is applied and hash-verified. The generic
rule now handles configured arithmetic/rendering boundaries and imports the existing
newtype codecs for deriving records. A real-Iron boundary fixture passes exact-output,
compilation and runtime checks. Preview v3's 479-source API overlay compiles; the
actual build also compiled API JVM/JS and reached dependent modules.

Actual compile currently fails with fourteen errors: eleven in web pagination state
and three in server result handling. Test/compile did not execute. The next required
step is typed propagation into web ListPage/TabPage and result state, with explicit
rendering/mechanism conversions; no new fixes are credited yet. Factory parameter
typing remains outstanding independently of these compiler errors. See
`models/pagination/build-v1/build.log` and the residual checklist.

The pre-application six-suite baseline retains all 115 test identities: 113 passed,
two ArticlesSuite tests failed on client slug normalization/validation, no skips.
Those failures are preserved, not attributed to this rewrite or silently waived.
Production/test verification and fresh project-wide measurement remain required;
the preceding inventory is historical while the batch is incomplete.

## Optional type propagation and real-code preview

The configurable rewrite now covers optional fields/results and typed forwarding
between records. Both the new optional fixture and the original regression fixture
pass compilation, exact-output comparison and runtime/type-separation assertions.

An isolated 18-file preview spans nine pagination result records, Pure wire types
and constructor callers/tests. All original source hashes remain unchanged. Its full
479-source API production overlay fails with eleven retained compiler errors in
pagination arithmetic/comparison/rendering and media count rendering. No production
fix is claimed. The next pass has concrete compiler evidence, rather than expanding
the inventory again. See `models/pagination/preview-v1`; its sources and rule are
sealed. Factory signatures, dependent modules, JS and tests still require validation.
Confirmed findings remain outstanding; propagated fields remain candidates until
reviewed. Copy automation remains paused and no detector refinement round was spent.

## Primitive rewrite fixture checkpoint

ModelTypeRewrite now performs a configurable semantic rewrite of direct Int record
fields, explicit method results, defaults and positional/named constructor/copy
arguments. The compiled fixture verifies exact output, real opaque separation across
files, unchanged look-alikes and forwarded typed fields, preserved runtime values,
and rejection of constructor-order drift. Failed development runs remain preserved.

No production primitive fixes are claimed. Optional leaves, arithmetic, inferred
results and transitive propagation still need support before the pagination preview.
The eleven confirmed leaves and all other candidates/unresolved evidence remain
outstanding. This is rewrite development, not an independent detector quality sample
or a new detector refinement round. The preceding full-project assessment belongs
to the earlier tool fingerprint; this fixture does not replace a future batch's
production/test compilation and project-wide remeasurement.

## First primitive purpose decisions

`models/pagination` now freezes eleven confirmed public production primitive leaves:
seven row-offset positions, two row counts, one requested-page-size sentinel and one
text offset. Nine are assigned bulk rewrite, one targeted rewrite and one design work;
none is fixed yet. Stable symbol-plus-leaf identities, source hashes and AST evidence
are checked against the full inventory. Tests have no new adjudications in this batch.

The pagination discovery queue omitted five reviewed leaves named next, shown or
total; the complete primitive inventory contains all eleven. PassageHit.offset is
a text position, not a pagination offset, and Filter.limit cannot directly become
positive PageSize because zero selects configured defaults. These distinctions are
retained rather than using spelling as equivalence. No detector refinement round or
independent quality result is claimed. Next is the complete semantic caller closure
and conversion plan for the reviewed concept groups, before a reusable AST rewrite.

## Approval ownership repair completed

The 14-file Scalafix ownership batch and separate one-file codec prerequisite are
applied. Full production/test compilation and 38 unchanged test identities pass.
All 54 confirmed production relationships in the source-proposal coupling are
verified removed; every other production relationship is retained. These are
relationships within one design problem, not 54 independent operations. Original
codec failures, rejected gate runs and immutable baselines remain preserved.

The integrated assessment passes across 3,140 source/test files. Ten new test
relationships are individually adjudicated non-violations through a hashed additive
event; identity regression checks still reject substitution and reappearance.
Isolation now retains 170 production candidates, 21 accepted production exceptions,
138 test candidates and 61 test non-violations. Unclassified directions remain
unresolved. See `approval-isolation/README.md` for repair and verification evidence.

Capability measurement remains 147 occurrences in 125 anchors, with no new/reopened
identities or multiplicity changes. Transaction ownership retains three verified
repairs and two unresolved SQL expressions. Primitive modeling now records 30,437
production member primitive leaves and 46,873 unresolved leaves across all scopes;
these are not confirmed violations. Purpose/owner/equivalence decisions for modeling
remain next. Independent detector quality and the project-wide 90% automation target
remain unestablished. Copy automation stays paused. No detector refinement round was
consumed. The checkpoints below retain historical states.

## Approval extraction development checkpoint

The source-proposal ownership repair now has a generated four-file definition/storage
preview that compiles, and a frozen eight-test baseline covering database and memory
implementations with no skips. The preview retains typed library inputs and shares
the original atomic insertion operation; its service reads through the engine's
connection-context method inside the same transaction. Two generator errors were
caught by compilation and corrected before application; their plans remain preserved.

Application sources are unchanged. Caller wiring, removal of old source-specific
engine methods, full module compilation, behavior comparison and identity-based
remeasurement remain required. All 54 confirmed relationships remain outstanding.
See `approval-isolation/extraction/README.md` for the exact next AST pass. The test
cluster was stopped after the baseline. No new detector refinement round was consumed.

## Integrated isolation gate and expanded repair scope

ProjectAssessment now executes the isolation identity gate. A full assessment of
3,138 source/test files passed it with zero new/reopened relationships. Capability
history also passed unchanged at 147 occurrences. No application code changed in
this integration; the existing compiled SemanticDB remained the source evidence.

Tracing the confirmed source-proposal coupling through Approvals and AgentRepo
confirmed 22 additional relationships. The current isolation treatment ledger is
54 confirmed production relationships (one design problem), 21 explicit production
exceptions, 170 production candidates, 138 test candidates and 51 test non-violations.
The review reconciler checks exact source hashes and relationship presence. Earlier
observations and review decisions remain preserved; no fixes are inferred from this
classification work. `approval-isolation/design.md` records the required contract,
repository, caller and test scope, including persisted deduplication semantics.

## Isolation identity and treatment checkpoint

The existing architecture scan now has a separate frozen relationship baseline and
append-only observations in `isolation-history`. Its 788 forbidden-direction evidence
rows represent 434 symbol relationships plus 130 package-qualifier rows. Scope totals
are 245 production and 189 test relationships; 110 have ambiguous owners. All 181,340
unclassified-direction evidence rows remain unresolved. This is a different unit from
the evaluated build's dependency edges, which retain their own evidence.

A bounded source review confirms 32 production relationships representing one
ApprovalService engine/library coupling requiring redesign and repair. It explicitly
accepts 21 relationships implementing the documented SourceReferrer contribution
contract and identifies 51 legitimate codec-test relationships. Remaining candidates:
192 production and 138 test. No isolation fixes or independent quality result claimed.
The CLI gate passes unchanged input and rejects equal-count substitution and reopening
with exact identity evidence; automatic assessment integration remains outstanding.
The initial local-owner confidence error and correction are both retained. No detector
refinement round was consumed, and no original findings or baselines were removed.

## Checkpoint after note/upload repair

The integrated assessment completed on 3,138 source/test files. Production
capability coverage is 2,368/2,368 files: 147 raw occurrences in 125 anchors,
zero new/reopened identities and zero multiplicity changes. Of those occurrences,
146 remain candidates and one is an adjudicated non-violation. Test capability
coverage and independent detector precision/recall are still unestablished.

The original capability treatment review's 58 confirmed occurrences in 48 anchors
are repaired across agent-storage, plugin-capabilities and note-upload; two additional
classloader gaps were repaired separately. Three clock occurrences have an explicit
exact-owner adapter exception. Note/upload accounts for four verified repairs and
one removed non-violation, with 29 unchanged passing test identities and full
production/test compilation. See each batch's frozen plan and verified ledger.
The earlier checkpoint below is retained as history, not current outstanding work.

Transaction ownership retains three repaired boundaries and two unresolved SQL
expressions. Module isolation retains zero forbidden configured evaluated build
edges, but semantic crossings and test/published-plugin policy need adjudication
and stable history. Primitive modeling now has 30,436 production member primitive
leaves in 28,248 positions (six more of each than the plugin checkpoint), and 46,867
unresolved leaves across all scopes. These remain candidates, not a confirmed repair
denominator. No primitive fixes are claimed. Copy automation remains paused.

The next bounded work is specified in `next-priority-batch.md`: identity history for
module isolation and purpose/owner/equivalence decisions for pagination modeling.
The 90% automation objective remains project-wide and is not established by finishing
the currently reviewed capability subset.

## Capability treatment checkpoint

The bounded source review in `capability-treatment/README.md` now confirms 58
supported occurrences in 48 existing anchors, plus one separately identified
resource-access detector gap. Two raw findings are adjudicated non-violations;
149 occurrences in 125 anchors remain candidates. No exceptions were accepted,
and no detector output or original baseline was removed. Independent sampled
precision/recall and test-policy coverage remain unestablished.

The next repair batch covers AgentTrees, PluginTree and Prompts: 29 supported
occurrences plus the resource gap. Plugin file/process capabilities remain a
required cross-module design batch; they must not acquire server dependencies.
No new application fixes are claimed by this treatment review. Transaction fixes
below remain verified; copy automation remains paused.

## Transaction repair checkpoint

TX-0001–TX-0003 are now verified fixed by a single guarded targeted Scalafix batch.
Project-wide production capability coverage is 2,358/2,358 files (one new feature
orchestrator); repository transaction diagnostics fall from 22 to zero, factory
diagnostics remain zero, and all 209 other capability occurrences retain their
identities and multiplicities. Two SQL expressions remain unresolved. No new
supported production capability identities appeared. This is bounded coverage,
not a claim of complete transaction recall or a test capability baseline.

The same 62 selected real-database test identities pass before and after, without
failures or skips. Production/test compilation and transaction lifecycle fixtures
pass. See `transaction-repair/README.md`, `transaction-findings.tsv`, and the
append-only `transaction-history.tsv`. The baseline retains the repaired findings.
One source assertion correction is recorded with both versions and policy contracts.

The final integrated assessment passes on 3,128 source/test files. The capability
identity gate retains 209 occurrences in 175 anchors, of which 65 occurrences have
ambiguous identity; new/reopened identities and multiplicity changes are zero.
Its 15 absent anchors map to the 22 occurrences verified by the transaction batch.
Tests and unsupported capability flows remain outside this regression claim.

Primitive member candidates now number 30,322 leaves in 28,149 production positions
(both up nine after the signature split). Full-inventory unresolved leaves are
46,650. No primitive fixes or semantic equivalence approvals are claimed. The old
counts below remain the preceding checkpoint; the increase is not hidden by resetting
the denominator. Module build inventory completes again; type-crossing candidate
counts changed and still require identity/adjudication work. Aggregate baseline
comparisons remain explicitly policy-incomparable rather than being recaptured.

Capability classification, module isolation history, and primitive/newtype semantic
grouping remain required work. Their previous evidence below is preserved, not
silently replaced by the transaction batch. Copy automation stays paused.

## Prior measurement checkpoints

Checkpoint after the completed current-batch assessment. Figures below are fresh
measurements with the same limitations as their preceding baseline. All four families
retain required work independently of the copy rewrite's progress.

Subsequent targeted transaction refinement 1/2 measures 231 capability diagnostics,
including 22 transaction diagnostics, and three unresolved SQL expressions across
the same 2,357 production files. All 28 frozen source assertions now pass. Its three
added occurrences were already present in TX-0001; they are not code regressions or
fixes. The table below preserves the preceding integrated-assessment checkpoint.
The additive history policy is now adopted without replacing the original baseline.
A new full integrated assessment passes under that policy: 231 occurrences in 190
anchors, 79 identity-ambiguous occurrences, no new/reopened anchors or multiplicity
changes. See transaction-round1.md. This pass does not prove detector completeness
or eliminate the outstanding transaction repair work.

| Family | Production evidence | Test evidence | Confirmed outstanding / verified fixes | Confidence and unresolved work |
|---|---|---|---|---|
| Transaction ownership | 19 configured diagnostics, including a helper witness; three reviewed ownership problems | Direct callers identified; no project-wide test transaction baseline | TX-0001–TX-0003 outstanding; no transaction fixes | Direct references and known-source helper paths; callbacks, dispatch and external helpers unresolved. Nineteen diagnostics are not nineteen boundaries. |
| Capability access | 2,357/2,357 main files analyzed; 209 non-transaction policy matches (228 including transactions) | Not yet measured under a separate test policy | Full adjudicated denominator unestablished; earlier console batches have separate historical validation | Symbol/config checks and regression examples, not measured whole-family recall. Missing-symbol records are currently collected only in best-effort mode: ordinary-mode zero is not evidence of resolution. Permissions have reasons/use counts but not retained occurrence identities. |
| Module isolation | 30 evaluated sbt projects, 1,504 compile/runtime classpath entries, 128 project relationships; zero configured forbidden relationships | Test dependency policy not established | No forbidden edge in the measured configuration; broader confirmed baseline unestablished | Evaluated closures include JVM/JS rows. Published plugin contract, dependency-policy completeness and type-level crossings still require evidence. Zero configured forbidden edges is not complete isolation proof. |
| Primitive/newtype modeling | 30,313 primitive leaves in 28,140 production member positions, excluding alias definitions | Included in full inventory; separately adjudicated test baseline absent | Confirmed denominator unestablished; no fixes claimed from candidate counts | 3,127 source/test files; 46,622 unresolved leaves across full inventory. Name/base proposal groups do not prove shared purpose. Owner, units, layers, invariants and phantom parameters require decisions. |

These figures use different units and must not be summed. Accepted exceptions remain
unestablished as a reconciled inventory; configured permissions and old suppression
ledgers are evidence to review, not an inferred exception approval.

Capability/transaction evidence now has a conservative identity gate, documented in
`capability-history/README.md`: 228 diagnostics, 188 anchors, 77 occurrences with
ambiguous identity. Equal-count replacement and reappearance are rejected; line-only
movement passes. This is evidence tracking, not blanket confirmation of every match.
New architectural violations remain unknown outside supported anchors and detector scope.
The transaction ledger assigns stable boundary IDs; automatic diagnostic-to-boundary
matching, classification/treatment events, permission occurrences and history for the
other families remain required work. Aggregate counts alone still do not protect them.

For each family, first reconcile existing evidence into confirmed/candidate/unresolved/
accepted classes and assign treatment without expanding detection indefinitely. At most
two detector refinement rounds remain available per migration-rounds.tsv. Copy has used
both rounds and is paused after batch validation. Remaining families use that same bounded
contract; missing evidence is reported explicitly rather than blocking these four priorities.
