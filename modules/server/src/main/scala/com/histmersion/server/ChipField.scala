package com.histmersion
package server

import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.catalogue.Place
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{movesTo, FeaturePlace}

/** A metadata dimension a chip filters the web UI by. `hashKey` is the web UI's URL-hash key, so a chip is a link that
  * works in the modal, on a standalone page and pasted anywhere.
  */
@movesTo(FeaturePlace.Kernel)
enum ChipField(val hashKey: ChipField.HashKey):
  case Topic extends ChipField(ChipField.HashKey("topic"))
  case Place extends ChipField(ChipField.HashKey("place"))
  case Tag extends ChipField(ChipField.HashKey("tag"))
  case Author extends ChipField(ChipField.HashKey("author"))

object ChipField:
  type HashKey = HashKey.T
  object HashKey extends Newtype[String, Pure]
