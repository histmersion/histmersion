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
