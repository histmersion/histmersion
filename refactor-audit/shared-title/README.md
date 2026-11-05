# Shared Title moved to the kernel

The existing opaque `Title` now lives at `com.histmersion.domain.Title`. Its existing
meaning covers source/article titles and event/place labels. `Described`, `Referable`,
personal models and `RecordTable` therefore no longer depend on a library-owned type
to describe arbitrary records. The String/Pure representation is unchanged.

RelocateOwners applied one configured batch to the definition and fourteen consumer
files, across values, core and server production/tests. The source-hash-guarded preview
records all 3,149 files, the fifteen patches and the relocation. No application file
was manually edited. The rule matches exact configured alias/companion symbols,
including Scala 3's generated package owner. It preserves bound imported names,
splits grouped imports and qualifies otherwise unbound or qualified references.
It refuses stale sources, destination collisions and overlapping patches.

## Evidence

- `preview-1`: frozen before/after sources, AST shapes, source hashes and applied check.
  There are 38 references and two declarations; these are not forty policy defects.
- `fixture-2`: compiled input/expected/output, exact patch comparison, old-owner
  rejection before repair, successful check afterward and unchanged second application.
  Fixtures include grouped/renamed imports, same-package references, qualified uses,
  wildcard context, a local same-name object and an unrelated API Title. Fixture-1
  passed its output tests but overwrote repeated check logs; fixture-2 retains each
  stage separately. It supersedes that incomplete negative-test evidence.
- `build-1`: full production and test compilation; NewtypeCodecsSuite 5/5 and
  PersonalTypesSuite 9/9, no skips. All source hashes stayed stable during execution.
- `review-1`: original 390 isolation relationships retained; 36 production relationships
  confirmed for this ownership move, eight test references kept separate. Existing
  ambiguous owner classifications are preserved.
- `reconciliation-1`: the exact 44 reviewed relationships disappear, replacement
  references to the kernel type exist, all 346 other identities remain unchanged,
  and the newtype is still String/Pure. Production repair credit is 36 relationships
  representing **one ownership defect**, not 36 independent concept decisions.

The fresh isolation history observation is
`../isolation-history/observations/2026-11-05T04-09-21.771592Z`: 155 production and
191 test candidate relationships, 81 ambiguous owners, zero new/reopened identities.
The generic region detector labels shared-kernel targets unclassified; the documented
kernel policy, explicit purpose review, replacement references and unchanged newtype
representation establish this repair. No blanket acceptance of unclassified directions
was added. Their raw evidence count is 182,218 (previously 182,152).

The fresh model inventory still reports 30,387 production member primitive leaves,
62,218 all-scope primitive leaves and 46,893 unresolved leaves. No primitive repair
credit or denominator change follows from relocating an existing newtype.

## Limits and next work

This is a reusable configured rewrite, not automatic inference of a type's rightful
owner. Independent detector precision/recall is still unestablished. This batch adds
no detector refinement round. DB-backed identity behavior and native images were not
executed. Capability/transaction and copy measurements retain the preceding full
assessment; only isolation and models were freshly scanned after this batch.

Remaining isolation candidates, including feature-specific DB codecs in
`util.database.Codecs`, still need treatment. The three pagination findings retain
their original ledger. Copy automation remains paused. The 90% migration-wide target
is not established by this batch.
