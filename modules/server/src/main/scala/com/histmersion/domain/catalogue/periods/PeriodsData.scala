package com.histmersion
package domain.catalogue.periods

import com.histmersion.util.data.diagnostics.DataCount
import com.augustnagro.magnum.DbCon
import com.histmersion.lint.{LintRule, tolerated}

/** The named periods' share of the catalogue's data contribution: how many there are, and how many the timeline offers
  * as presets.
  */
object PeriodsData:
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def figures(using DbCon): List[DataCount] =
    List(
      DataCount(DataCount.Noun("periods"), DataCount.Count(PeriodRepo.count.value.toLong)),
      DataCount(DataCount.Noun("presets"), DataCount.Count(PeriodRepo.presetCount.value.toLong))
    )
