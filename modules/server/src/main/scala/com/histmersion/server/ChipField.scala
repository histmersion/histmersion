package com.histmersion.server

import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.catalogue.Place

/** A metadata dimension a chip filters the web UI by. `hashKey` is the web UI's URL-hash key, so a chip is a link that
  * works in the modal, on a standalone page and pasted anywhere.
  */
enum ChipField(val hashKey: ChipField.HashKey):
  case Topic extends ChipField("topic")
  case Place extends ChipField("place")
  case Tag extends ChipField("tag")
  case Author extends ChipField("author")

object ChipField:
  type HashKey = HashKey.T
  object HashKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
