package com.histmersion
package cli

import scala.jdk.CollectionConverters.*
import io.github.iltotore.iron.constraint.any.Pure

final case class UpdateEntry(
    calibreId: UpdateEntry.CalibreId,
    citekeys: List[UpdateEntry.Citekeys],
    outcome: UpdateOutcome
)

object UpdateEntry:
  export Calibre.Book.Id as CalibreId
  type Citekeys = Citekeys.T
  object Citekeys extends Newtype[String, Pure]
