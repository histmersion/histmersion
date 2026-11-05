package com.histmersion.plugin

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
    keys: List[String],
    issues: List[CollectionIssue],
    item: Option[CollectionItem],
    record: CollectionRecord,
    fixable: Boolean
)
