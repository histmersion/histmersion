package com.histmersion
package util.ocr

import io.github.iltotore.iron.constraint.any.Pure

/** One indexable slice of a source body. `heading` is the breadcrumb of the Markdown headings the slice sits under
  * ("Introduction > Chronology"), `body` the text without those heading lines.
  */
final case class Chunk(ord: Chunk.Ord, heading: Option[Chunk.Heading], body: Chunk.Body)

object Chunk:
  type Ord = Ord.T
  object Ord extends Newtype[Int, Pure]
  type Heading = Heading.T
  object Heading extends Newtype[String, Pure]
  type Body = Body.T
  object Body extends Newtype[String, Pure]
