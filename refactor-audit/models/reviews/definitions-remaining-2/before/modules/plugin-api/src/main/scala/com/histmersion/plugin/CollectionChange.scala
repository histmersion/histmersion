package com.histmersion.plugin

/** One field a collection changes (or would change) in an item: its name in the collection, what it holds now and what
  * it is written to.
  */
final case class CollectionChange(field: String, from: String, to: String)
