package com.histmersion.util.ocr

/** One indexable slice of a source body. `heading` is the breadcrumb of the Markdown headings the slice sits under
  * ("Introduction > Chronology"), `body` the text without those heading lines.
  */
final case class Chunk(ord: Chunk.Ord, heading: Option[Chunk.Heading], body: Chunk.Body)

object Chunk:
  type Ord = Ord.T
  object Ord extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Heading = Heading.T
  object Heading extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Body = Body.T
  object Body extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
