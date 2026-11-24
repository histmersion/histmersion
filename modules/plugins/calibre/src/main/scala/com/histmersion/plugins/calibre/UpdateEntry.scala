package com.histmersion
package plugins.calibre

import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{LintRule, tolerated}

final case class UpdateEntry(
    calibreId: UpdateEntry.CalibreId,
    citekeys: List[UpdateEntry.Citekeys],
    outcome: UpdateOutcome
)

object UpdateEntry:
  type CalibreId = CalibreId.T
  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  object CalibreId extends Newtype[Int, Pure]
  type Citekeys = Citekeys.T
  object Citekeys extends Newtype[String, Pure]
