package com.histmersion
package plugin

import io.github.iltotore.iron.constraint.any.Pure

/** A place's id in one gazetteer, as the pair the media SPI hands around: the scheme (`pleiades`, `geonames`,
  * `wikidata`) and the id there (`697725`, `Q5788`).
  */
object GazetteerRef:
  type Scheme = Scheme.T
  object Scheme extends Newtype[String, Pure]
  export GazetteerPlace.Id
