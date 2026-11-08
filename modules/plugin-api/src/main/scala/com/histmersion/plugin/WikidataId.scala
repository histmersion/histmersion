package com.histmersion
package plugin

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.any.Pure

/** A Wikidata item as a plugin states it (`Q5788`). Unrefined: the host checks it where it becomes the model's Wikidata
  * id and leaves out one that is none (with a warning), keeping the rest of the record.
  */
type WikidataId = WikidataId.T

object WikidataId extends RefinedType[String, Pure]
