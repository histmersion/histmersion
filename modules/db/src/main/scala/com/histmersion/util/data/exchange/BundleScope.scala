package com.histmersion
package util.data.exchange

import java.util.UUID
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** Which rows a bundle read covers: every one, the ones changed after a moment (`--since`), or the ones with the given
  * uids (what an import compares the bundle against).
  */
enum BundleScope:
  case All
  case Since(at: BundleScope.Since.At)
  case Uids(uids: List[UUID])

object BundleScope:
  object Since:
    type At = At.T
    object At extends RefinedType[String, Pure]
