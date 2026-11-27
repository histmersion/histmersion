package com.histmersion
package domain.catalogue.periods

import com.histmersion.api.Api
import com.histmersion.api.catalogue.HistoricalPeriod
import com.histmersion.domain.Caller
import sttp.shared.Identity
import sttp.tapir.server.ServerEndpoint
import com.histmersion.domain.Creator
import com.histmersion.lint.{tolerated, LintRule}

/** The periods' endpoints bound to their controllers, in the order route matching needs: `periods/{id}` after the
  * search, the writes after both.
  */
object PeriodsRoutes:
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  def apply(periods: PeriodsControllers): List[ServerEndpoint[Any, Identity]] =
    given Creator.New = Creator.New.Unattributed(Creator.Surface.Rest)
    val me = Caller.Local
    List(
      Api.periods.serverLogicSuccess[Identity]((q, region, from, to) =>
        periods.search(
          q.map(t => PeriodSearch.Text(t.value)),
          region.map(g => PeriodSearch.Region(g.value)),
          from.map(y => PeriodSearch.From(y.value)),
          to.map(y => PeriodSearch.To(y.value))
        )
      ),
      Api.period.serverLogic[Identity](id => periods.period(HistoricalPeriod.Id(id.value))),
      Api.seedPeriods.serverLogic[Identity](replace => periods.seed(me, PeriodImports.Replace(replace.value))),
      Api.importPeriods.serverLogic[Identity]((cols, dataset, body) =>
        periods.importDataset(
          me,
          cols.map(c => PeriodSources.Collections(c.value)),
          dataset.map(d => PeriodImports.Dataset(d.value)),
          PeriodImports.DatasetBody(body.value)
        )
      ),
      Api.fetchPeriods.serverLogic[Identity]((source, cols) =>
        periods.fetch(me, PeriodSources.PeriodoUrl(source.value), cols.map(c => PeriodSources.Collections(c.value)))
      )
    )
