package com.histmersion.util.text

/** Where a passage is in a text: `[start, end)` character offsets. */
final case class Anchor(start: Anchor.Start, end: Anchor.End)

object Anchor:
  type Start = Start.T
  object Start extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type End = End.T
  object End extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
