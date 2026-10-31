package com.histmersion.plugin

/** One item of a [[SourceCollection.updateAll]]: its id, the keys of the sources linked to it, and its outcome. */
final case class CollectionUpdateEntry(id: CollectionId, keys: List[String], outcome: CollectionUpdateOutcome)
