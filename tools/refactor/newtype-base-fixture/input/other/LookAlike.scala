package com.histmersion
package lookalike

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.any.Pure

/** Not Iron's RefinedType: left as it is. */
trait RefinedLike[A, C]

object Local extends RefinedLike[String, Pure]

/** A subtype newtype is not rewritten (none in the codebase). */
object Sub extends RefinedSubtype[String, Pure]

/** A type alias to the newtype and its use stay. */
object Uses:
  def heading(h: com.histmersion.util.ocr.Chunk.Heading): String = h.value
