# Pagination measurement after d34cea90

The project assessment completed across 3,143 files and 28 source modules. Its
source fingerprint equals the full passing compile/Test/compile observation build-v7:
`78db33f2f08af46ffb64b28baf76f907859717a9c961cf8a0a85587a4411a7e6`.
The previous assessment and baseline were archived before measurement. The original
aggregate baseline files were not changed. Policy fingerprints are incomparable;
the enabled identity gates, rather than aggregate count comparison, establish zero
new/reopened capability and isolation findings within their documented scopes.

The original review-1.tsv retains eleven confirmed production findings. In
review-history/after-pagination-1/findings.tsv, eight now have verified newtype leaves:
OffsetPage total/shown/offset/next, SearchResult offset/next, and GeoHitPage offset/next.
All original IDs and treatments are retained. Three remain outstanding:

| Finding | Treatment | Reason |
|---|---|---|
| Filter.limit | Manual/design | Zero means configured default; preserve default/cap semantics before choosing the request type |
| Filter.offset | Bulk rewrite | Still primitive; propagate the row offset through filter transformations, factories and consumers |
| PassageHit.offset | Targeted rewrite | UTF-16 text position, distinct from the row-offset type |

VerifyMeasurement is a post-measurement audit rule kept with this evidence, not an
additional detector or a changed detection policy. It checks source hashes, matching
compile/measurement universes, all 115 API regression identities and unchanged failure
evidence, then reconciles original finding IDs against the fresh semantic leaves.
The relevant leaves are frozen in evidence.tsv, including alias paths. A missing or
unsupported position becomes unresolved or fails verification; it cannot disappear
from the denominator. Parent Option metadata remains unresolved in the full inventory
even where the reviewed contained type has positive newtype evidence.

With `VerifyMeasurement.previous` pointing to an earlier findings.tsv and `report`
pointing to a fresh directory, the rule rejects reopened fixed IDs and changes to the
reviewed ID set. The self-test verifies both an unchanged ledger and an identity swap
with equal aggregate fixed counts: the latter fails with one reopened finding.
Its deliberately failing observation and log are retained in review-history/self-test-1.
This gate does not detect all newly introduced model violations outside these eleven
reviewed IDs. Broader candidates and independent detector-quality acceptance remain open.

The full inventory now has 30,387 production member primitive leaves, previously
30,437, and 62,211 primitive leaves across all scopes, previously 62,271. These are
candidate counts, not 50 or 60 verified repairs. Unresolved leaves remain 46,873.
Newly typed caller state and domain evidence outside the original review are recorded
in the rewrite plans; they are not silently added to its confirmed denominator.

Runtime evidence remains in runtime-checkpoint.md: 113 passing original API tests,
two exactly unchanged pre-existing failures, 59 additional passing tests, and the
earlier 11 passing web tests. DB-backed/HTTP runtime suites were not executed here.
No claim of whole-migration 90% automation or 95% detector precision/recall follows.

Other explicit residuals include API factory parameter types, Autopilot DraftFacts.hits,
EventLead.List.total, raw map/unread count models and the broader model inventory.
Copy automation remains paused; capability, transaction and isolation unknowns retain
their earlier classifications and must not be silently accepted.
