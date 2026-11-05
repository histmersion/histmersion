# Pagination purpose review 1

This bounded source review establishes eleven confirmed production primitive leaves
and their proposed types. It changes no application code or detector. The full
inventory and unresolved evidence remain intact. The review is development evidence,
not an independent quality sample and not proof of project-wide precision or recall.

Seven row-offset positions share `api.Offset`: OffsetPage.offset and next,
SearchResult.offset and next, GeoHitPage.offset and next, and Filter.offset. These
represent rows skipped from zero. Their wire representation should remain Int over
Pure. The existing kernel Offset constrains nonnegative domain values; reuse that
domain concept at the parse boundary, without importing its constraint into clients.

OffsetPage.total and shown share proposed `api.RowCount`, including zero. A count of
returned rows is not a requested positive page size. These nine leaves are classified
for bulk rewriting, subject to compiling the complete definition/caller closure.
The reviewed positions are seeds, not a complete transformation plan or a claim that
other row counts should be omitted. Discover that closure semantically before applying.

Filter.limit requires design work: zero (and in the findings boundary nonpositive
values) requests the configured default. The proposed wire owner is PageSize.Request;
its representation must retain that request behavior. Existing kernel PageSize is
positive and cannot represent it directly. Resolve defaults and caps at the server
boundary before constructing the final domain PageSize. Do not silently change zero
to an error during migration.

PassageHit.offset is a separate confirmed primitive-model violation, but a negative
example for row-offset equivalence. PassageFinder supplies a range.start used with
String.substring: its unit is UTF-16 code units in the concatenated source text.
Proposed owner PassageHit.Offset reflects the entity property; its rewrite is targeted.
No reviewed use establishes a cross-entity error that PageSize[A] or Offset[A] would
prevent. Retain shared scalar concepts; revisit typed cursors or result containers
when their concrete entity-sensitive semantics are reviewed.

Review.scala joins exact semantic position and type-leaf route against the existing
inventory, requires public production members and current source hashes, and freezes
the decision ledger and supporting sources. IDs hash position plus route, never line
numbers. An initial run failed because the no-argument next method's primitive sits
under ByNameType, unlike a constructor field; the route was corrected from the
actual inventory without weakening the exact-leaf assertion. No ledger was written
by that failed run. Option wrapper unresolved records remain unresolved even though
their nested Int leaves are confirmed.

Run with the Scalafix CLI using `--rules file:refactor-audit/models/pagination/Review.scala`
and `--files refactor-audit/models/pagination/Review.scala`. Re-running refuses changed
decisions or supporting evidence. Future source changes need explicit review events.
The name-based pagination queue misses next, shown and total; this is a discovery
queue limitation, not absence from the full primitive inventory. Keep the missing
positions visible before spending a detector refinement round. Next: build the
symbol-use closure for these concepts, establish boundary conversions and freeze
positive/negative rewrite fixtures before generating a multi-module patch.

## Cross-module impact and AST evidence

Impact.scala checked all 3,140 current source/test hashes and the exact source set.
The five reviewed record owners have 1,734 reference rows across 129 files, including
115 direct reviewed-member references and 235 one-hop caller candidates. These are
impact evidence, not additional confirmed violations or a complete dataflow closure.
Production and test counts are separated in impact-1/counts.tsv. No denominator was
shrunk to the initially reviewed library records.

The subsequent Shapes.scala semantic scan covers the same 3,140 files and retains
2,370 matching names with their real ASTs, symbols and available signatures. Unlike
the existing uses table it retains symbols without source declarations. In particular,
Filter.copy and generated argument symbols can have a resolved symbol but no
SymbolInformation. ModelInventory filters its uses to symbols in its source position
and type catalogues, excluding generated members even when their signatures resolve.
The old use inventory's omission of these must not be mistaken
for zero construction/copy callers. shapes-1/generated-members.tsv quantifies the
gap. This observation does not alter the original inventory or spend a refinement
round; the reusable rewrite must handle this compiler shape explicitly.

The concrete batch extends to Goal.List, Attestation.List, EventList, AssetLink.List,
MediaList and Article.List: their factory methods forward OffsetPage.next into
Option[Int] fields. Their offsets and factory parameters need the same concept review
and signature propagation. Merely unwrapping next back to Int at each factory would
leave those model primitives unfixed and is not the intended migration.

| Observed shape | Required rewrite behavior |
|---|---|
| Constructor field and default | Replace the exact approved type leaf; type numeric defaults with the selected Pure wire newtype. |
| Named and positional constructor arguments | Resolve constructor parameter order/name, transform approved arguments and preserve omitted defaults. |
| Generated copy arguments | Adjust only explicitly supplied changed fields; omitted fields retain their typed defaults. This is type propagation, not expansion of copy-to-optics automation. |
| OffsetPage.next forwarded into result records | Propagate Offset through result fields and factory signatures across domains. |
| Offset plus shown, comparisons and pattern match on zero | Provide typed arithmetic or unwrap at the pagination mechanism; do not erase types throughout callers. |
| SQL parameters and HTTP/JSON query values | Use owner-local codecs or explicit conversion at the boundary; keep wire JSON numbers unchanged. |
| PassageHit text offset | Keep separate from row offsets even though both currently use Int. |
| Filter.limit default sentinel | Design unresolved; preserve existing behavior and report it independently of supported offset/count work. |

The AST evidence includes declarations and imports, so totals cannot be interpreted
as calls. Enclosing operations can include unrelated arguments. Exact argument
mapping, compiler synthetics and implicit conversions remain required before claiming
complete rewrite coverage. Next is a compiled fixture covering these shapes and a
guarded multi-module preview; do not expand general primitive discovery first.

## Reusable rewrite development

`tools/refactor/ModelTypeRewrite.scala` introduces a configurable semantic rewrite
for reviewed direct Int fields and explicit method results. Configuration identifies
record symbols, the complete constructor parameter order, selected fields and their
replacement types, and selected result symbols. It changes type tokens and numeric
defaults, wraps raw arguments at positional/named constructor and generated copy
calls, and preserves forwarding of already-migrated fields. A look-alike text offset
on another record is unchanged. It checks constructor parameter order before patching.

The fixture separates opaque definitions into their own source file so that the
consumer compilation proves actual type separation. Baseline, expected and generated
sources are compiled independently; generated sources must match expected output
exactly. A wrong-order config must fail with the intended message and leave input
unchanged. Runtime checks cover retained values and compile-time rejection checks
cover Int/RowCount misuse as Offset. `rewrite-fixture.txt` records the completed run.

The first run failed compiling the config decoder; the second failed locating the
fixture SemanticDB because the source root was wrong. Both were corrected in tooling.
Before claiming success, fixture opacity was strengthened by separating definitions;
the expected constructor behavior was not weakened. Later runs added the constructor
order rejection and behavior checks. `rewrite-runs` preserves the run inputs/outputs
and logs, including failures; development fixture additions are not independent
detector samples.

Run v5 incorrectly compiled the post-migration typed assertions against the primitive
baseline and failed on Int versus opaque-type equality. Run v6 stages those unchanged
postconditions after rewriting; the original Models/Types baseline still compiles
first. No comparison assertion was weakened or removed. These postconditions verify
the target representation, not a claim of identical baseline test signatures.

This rule is not production-ready: Option leaves, arithmetic, arbitrary method
parameters, inferred return types, pre-existing typed arguments not named in the
configuration, multiple parameter clauses and transitive propagation remain outside
its tested support. An unsupported declaration type fails instead of silently
rewriting it. Do not run it directly on application sources; the next step is extending
these fixtures for optional offsets and generating a guarded preview of the reviewed
record family. Production/test module compilation and remeasurement are still required.
The overall automation target and independent detector-quality requirement remain open.
