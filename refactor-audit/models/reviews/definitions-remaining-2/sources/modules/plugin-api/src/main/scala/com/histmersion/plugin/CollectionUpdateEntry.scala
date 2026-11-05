package com.histmersion.plugin

/** One item of a [[SourceCollection.updateAll]]: its id, the keys of the sources linked to it, and its outcome. */
final case class CollectionUpdateEntry(id: CollectionId, keys: List[CollectionUpdateEntry.Keys], outcome: CollectionUpdateOutcome)

object CollectionUpdateEntry:
  type Keys = Keys.T
  object Keys extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
