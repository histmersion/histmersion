package com.histmersion
package plugins.calibre

import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** A source linked to a Calibre entry, with what the corpus would send there. */
final case class LinkedRecord(citekey: LinkedRecord.Citekey, calibreId: LinkedRecord.CalibreId, meta: Calibre.Push)

object LinkedRecord:
  type Citekey = Citekey.T
  object Citekey extends RefinedType[String, Pure]
  type CalibreId = CalibreId.T
  object CalibreId extends RefinedType[Int, Pure]:
    given scala.math.Ordering[T] = scala.math.Ordering.by[T, Int](_.value)
