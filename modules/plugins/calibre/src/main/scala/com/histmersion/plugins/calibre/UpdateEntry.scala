package com.histmersion
package plugins.calibre

import io.github.iltotore.iron.constraint.any.Pure

final case class UpdateEntry(calibreId: UpdateEntry.CalibreId, citekeys: List[UpdateEntry.Citekeys], outcome: UpdateOutcome)

object UpdateEntry:
  type CalibreId = CalibreId.T
  object CalibreId extends Newtype[Int, Pure]
  type Citekeys = Citekeys.T
  object Citekeys extends Newtype[String, Pure]
