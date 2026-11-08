package com.histmersion
package util.text

import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** Where a passage is in a text: `[start, end)` character offsets. */
final case class Anchor(start: Anchor.Start, end: Anchor.End)

object Anchor:
  type Start = Start.T
  object Start extends RefinedType[Int, Pure]
  type End = End.T
  object End extends RefinedType[Int, Pure]
