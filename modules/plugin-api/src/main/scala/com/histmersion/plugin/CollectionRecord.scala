package com.histmersion.plugin

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
  object Key extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Title = Title.T
  object Title extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Authors = Authors.T
  object Authors extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
