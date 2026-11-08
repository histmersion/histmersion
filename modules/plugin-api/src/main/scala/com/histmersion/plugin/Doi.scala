package com.histmersion
package plugin

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.any.Pure

/** A DOI as a plugin states it, the part after `https://doi.org/` (`10.1234/abc`). Unrefined: the host checks it where
  * it becomes the model's DOI and leaves out one that is none (with a warning), keeping the rest of the record.
  */
type Doi = Doi.T

object Doi extends RefinedType[String, Pure]
