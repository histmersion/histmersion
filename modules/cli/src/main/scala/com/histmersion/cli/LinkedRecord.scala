package com.histmersion
package cli

import scala.jdk.CollectionConverters.*
import io.github.iltotore.iron.constraint.any.Pure

/** A source linked to a Calibre entry, with what the corpus would send there. */
final case class LinkedRecord(citekey: LinkedRecord.Citekey, calibreId: LinkedRecord.CalibreId, meta: Calibre.Push)

object LinkedRecord:
  type Citekey = Citekey.T
  object Citekey extends Newtype[String, Pure]
  type CalibreId = CalibreId.T
  object CalibreId extends Newtype[Int, Pure]
