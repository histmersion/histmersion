package com.histmersion
package plugins.calibre

import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}

final case class UpdateEntry(
    calibreId: UpdateEntry.CalibreId,
    citekeys: List[UpdateEntry.Citekeys],
    outcome: UpdateOutcome
)

object UpdateEntry:
  export LinkedRecord.CalibreId
  type Citekeys = Citekeys.T
  object Citekeys extends Newtype[String, Pure]
