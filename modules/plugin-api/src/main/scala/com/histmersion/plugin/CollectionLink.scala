package com.histmersion.plugin

/** A source of the host linked to an item of a collection, with what the host would send there: one row of
  * [[SourceCollection.updateAll]].
  */
final case class CollectionLink(id: CollectionId, metadata: CollectionMetadata)
