package com.histmersion
package api.catalogue.periodo

import hearth.kindlings.optics.syntax.*
import com.histmersion.api.given
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import com.histmersion.api.catalogue.periodo.Definition
import com.histmersion.api.catalogue.HistoricalPeriod
import com.histmersion.api.catalogue.Year

final case class Authority(
    id: HistoricalPeriod.Id,
    title: HistoricalPeriod.Label,
    year: Option[Year],
    periods: List[Definition]
):
  /** A PeriodO id resolves as `http://n2t.net/ark:/99152/<id>`. */
  def cite(periodId: HistoricalPeriod.Id): HistoricalPeriod.Authority =
    HistoricalPeriod.Authority(s"PeriodO $periodId · $title${year.fold("")(y => s" ($y)")}")
