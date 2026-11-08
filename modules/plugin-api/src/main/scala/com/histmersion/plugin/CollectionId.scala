package com.histmersion
package plugin

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.any.Pure

/** An item's id in its collection, as the collection names it: a Calibre book id (`1693`), an archive.org identifier
  * (`racesofeurope031695mbp`).
  */
type CollectionId = CollectionId.T

object CollectionId extends Newtype[String, Pure]
