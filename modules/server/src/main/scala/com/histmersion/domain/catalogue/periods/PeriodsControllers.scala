package com.histmersion.domain.catalogue.periods

import com.histmersion.api.{Dataset, Write}
import com.histmersion.api.catalogue.{HistoricalPeriod, PeriodO}
import com.histmersion.domain.Caller
import com.histmersion.util.data.maintenance.WriteGate
import com.histmersion.api.Api
import com.histmersion.api.catalogue.Period

/** The periods' operations as REST, MCP and the CLI reach them: one period with what it is part of and how it is
  * divided, the search, and the seed, a sent dataset and the live dump written in.
  */
final class PeriodsControllers(periods: PeriodService, imports: PeriodImports, maintenance: WriteGate):

  def period(id: HistoricalPeriod.Id): Either[Api.Error, Period.Detail] =
    periods.get(id).toRight(Api.Error(Api.Error.Message(s"No period '$id'"), Some(Api.Error.Code(Write.CodeNotFound)))).map { p =>
      Period.Detail(p, p.parentId.flatMap(parent => periods.get(HistoricalPeriod.Id(parent.value))), periods.children(id))
    }

  def search(q: Option[com.histmersion.domain.catalogue.periods.PeriodSearch.Text], region: Option[PeriodSearch.Region], from: Option[PeriodSearch.From], to: Option[PeriodSearch.To]): List[HistoricalPeriod] =
    periods.search(PeriodSearch(q, region, from, to))

  /** `POST /api/periods/seed?replace=` — the presets bundled with this server. */
  def seed(caller: Caller, replace: com.histmersion.domain.catalogue.periods.PeriodImports.Replace): Either[Api.Error, Period.ImportResult] =
    maintenance.guard(com.histmersion.util.data.maintenance.WriteGate.OperationName("periods/seed"))(imports.applySeed(replace).left.map(e => Api.Error(Api.Error.Message(e.value))))

  /** `POST /api/periods/import` — a PeriodO dataset sent by the client (a dump, or a subset of one). */
  def importDataset(
      caller: Caller,
      collections: List[com.histmersion.domain.catalogue.periods.PeriodSources.Collections],
      dataset: Option[com.histmersion.domain.catalogue.periods.PeriodImports.Dataset],
      body: com.histmersion.domain.catalogue.periods.PeriodImports.DatasetBody
  ): Either[Api.Error, Period.ImportResult] =
    maintenance.guard(com.histmersion.util.data.maintenance.WriteGate.OperationName("periods/import")):
      for
        set <- Dataset.ofParam(dataset.map(d => Dataset.Wire(d.value)))
        d <- PeriodO.parse(body.value).left.map(e => Api.Error(Api.Error.Message(e.value)))
      yield imports.write(d.only(collections.map(c => HistoricalPeriod.Id(c.value.trim)).toSet), set)

  /** `POST /api/periods/fetch?source=periodo` — the live PeriodO dump, the named collections only. */
  def fetch(caller: Caller, source: com.histmersion.domain.catalogue.periods.PeriodSources.PeriodoUrl, collections: List[com.histmersion.domain.catalogue.periods.PeriodSources.Collections]): Either[Api.Error, Period.ImportResult] =
    maintenance.guard(com.histmersion.util.data.maintenance.WriteGate.OperationName("periods/fetch")):
      if source.value.trim.toLowerCase != PeriodImports.PeriodOSource then
        Left(Api.Error(Api.Error.Message(s"unknown period source '$source' (available: ${PeriodImports.PeriodOSource})")))
      else imports.fetch(collections).left.map(e => Api.Error(Api.Error.Message(e.value)))
