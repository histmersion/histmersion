package com.histmersion
package plugin

import io.github.iltotore.iron.constraint.any.Pure

/** A source of the host as an audit sees it: its key, the item it is linked to, and what the host says of it. */
final case class CollectionRecord(
    key: CollectionRecord.Key,
    id: CollectionId,
    title: CollectionRecord.Title,
    authors: List[CollectionRecord.Authors],
    year: Option[Year]
)

object CollectionRecord:
  type Key = Key.T
  object Key extends Newtype[String, Pure]
  type Title = Title.T
  object Title extends Newtype[String, Pure]
  type Authors = Authors.T
  object Authors extends Newtype[String, Pure]
