package com.histmersion
package plugin

/** What a [[SourceCollection]] lets the host do. Every collection is read; a collection that declares [[Add]] takes new
  * items ([[SourceCollection.add]], [[SourceCollection.replace]]), one that declares [[Update]] has its records
  * rewritten ([[SourceCollection.update]], [[SourceCollection.updateAll]], [[SourceCollection.set]]). The host never
  * calls a writing member whose capability is not declared.
  */
enum CollectionCapability derives CanEqual:
  case Read, Add, Update
