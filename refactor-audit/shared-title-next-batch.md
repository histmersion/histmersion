# Shared Title ownership: next bounded batch

This is a proposed treatment, not a verified fix or an accepted exception.
Copy automation remains paused. Do not broaden the detector to complete this batch.

## Evidence and intended ownership

`modules/values/src/main/scala/com/histmersion/domain/library/Title.scala`
defines an opaque Iron String/Pure type. Its documented meaning already includes
source/article titles and event/place labels. The shared kernel's `Described` and
`Referable`, personal records, and the supporting `RecordTable` use that same type.
This supports moving the existing domain type to `com.histmersion.domain.Title`,
without introducing a second equivalent domain type or changing its constraint.
The API's representation must be reviewed separately; it is not implicitly merged.

Freeze the fresh isolation observation before selecting the affected relationships.
Retain its existing IDs, including ambiguous owners, and distinguish confirmed
ownership violations from incidental references. A changed import is not a separate
verified architectural repair. Keep production and test evidence separate.

## Reusable rewrite and boundaries

The existing FeatureOwners rule is restricted to test-file moves and constructs
ordinary class/object symbols. It cannot safely be configured for this production
newtype: Scala 3 also uses a generated `Title$package` owner. Do not remove those
guards and assume coverage. Inspect actual SemanticDB references first.

Use a configured owner relocation rule with explicit source/destination paths,
declaration symbols, generated package-owner symbols and target qualified names.
Support the alias and companion together; split grouped imports, preserve renamed
imports, update qualified references, and supply imports for previously implicit
same-package or wildcard references. Preserve unrelated import entries and types.
Reject destination collisions and changed source hashes before applying any patch.
All application changes, including file relocation, go through Scalafix.

Freeze expected fixtures covering those shapes, plus a same-named unrelated Title,
an API Title and a renamed import. Compile input and expected output, check exact
patch output, compile the result and verify a second application makes no changes.
An unsupported reference must stop this batch with evidence, not disappear.

## Verification and stopping conditions

1. Freeze the complete semantic reference inventory and the affected isolation IDs.
2. Preview one definition-and-all-callers batch. Do not rewrite references singly.
3. Compile all production/test modules and run the existing title/identity/codec
   contract tests. Preserve assertions, wire representations and SQL behavior.
4. Remeasure isolation and model inventories. Reconcile the original affected IDs;
   require no new or reopened supported isolation IDs, retaining ambiguous evidence.
5. Publish the verified repair count, unchanged candidates and unsupported cases.

Finish or explicitly defer this bounded move before expanding to another ownership
pattern. Domain-specific DB codecs in `util.database.Codecs` are a separate candidate
batch: their bodies belong to the DB layer, but their feature ownership and implicit
scope need a separate plan. No broad utility-to-domain exception follows from this
review. Remaining pagination findings and capability candidates retain their ledgers.
