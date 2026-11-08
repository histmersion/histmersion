package com.histmersion
package plugin

import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** One linked item that disagrees with the host's record, judged against the record it matches best (an item shared by
  * a chapter and its volume is fine when it matches either).
  *
  * @param id
  *   the item's id
  * @param keys
  *   every source linked to it, sorted
  * @param issues
  *   what is wrong, worst first
  * @param item
  *   the item, `None` when the collection has no such item
  * @param record
  *   the record it was judged against
  * @param fixable
  *   whether rewriting the item from the record ([[SourceCollection.update]]) fixes it
  */
final case class CollectionFinding(
    id: CollectionId,
    keys: List[CollectionFinding.Keys],
    issues: List[CollectionIssue],
    item: Option[CollectionItem],
    record: CollectionRecord,
    fixable: CollectionFinding.Fixable
)

object CollectionFinding:
  type Keys = Keys.T
  object Keys extends RefinedType[String, Pure]
  type Fixable = Fixable.T
  object Fixable extends RefinedType[Boolean, Pure]
