package com.histmersion
package domain.catalogue.periods

import com.histmersion.util.data.diagnostics.DataCount
import com.augustnagro.magnum.DbCon

/** The named periods' share of the catalogue's data contribution: how many there are, and how many the timeline offers
  * as presets.
  */
object PeriodsData:
  def figures(using DbCon): List[DataCount] =
    List(DataCount(DataCount.Noun("periods"), DataCount.Count(PeriodRepo.count.value.toLong)), DataCount(DataCount.Noun("presets"), DataCount.Count(PeriodRepo.presetCount.value.toLong)))
