package com.histmersion
package util.ocr

import io.github.iltotore.iron.constraint.any.Pure

final case class Chunk(ord: Chunk.Ord, heading: Option[Chunk.Heading])

object Chunk:
  type Ord = Ord.T
  object Ord extends Newtype[Int, Pure]
  type Heading = Heading.T
  object Heading extends Newtype[String, Pure]:
    def empty: Heading = Heading("")
