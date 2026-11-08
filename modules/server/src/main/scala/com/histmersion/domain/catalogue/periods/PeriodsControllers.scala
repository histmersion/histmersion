package com.histmersion
package domain.catalogue.periods

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
    periods
      .get(id)
      .toRight(Api.Error(Problem(s"No period '$id'"), Some(Api.Error.Code(Write.CodeNotFound))))
      .map { p =>
        Period.Detail(
          p,
          p.parentId.flatMap(parent => periods.get(HistoricalPeriod.Id(parent.value))),
          periods.children(id)
        )
      }

  def search(
      q: Option[PeriodSearch.Text],
      region: Option[PeriodSearch.Region],
      from: Option[PeriodSearch.From],
      to: Option[PeriodSearch.To]
  ): List[HistoricalPeriod] =
    periods.search(PeriodSearch(q, region, from, to))

  /** `POST /api/periods/seed?replace=` — the presets bundled with this server. */
  def seed(caller: Caller, replace: PeriodImports.Replace): Either[Api.Error, Period.ImportResult] =
    maintenance.guard(WriteGate.OperationName("periods/seed"))(
      imports.applySeed(replace).left.map(e => Api.Error(Problem(e.value)))
    )

  /** `POST /api/periods/import` — a PeriodO dataset sent by the client (a dump, or a subset of one). */
  def importDataset(
      caller: Caller,
      collections: List[PeriodSources.Collections],
      dataset: Option[PeriodImports.Dataset],
      body: PeriodImports.DatasetBody
  ): Either[Api.Error, Period.ImportResult] =
    maintenance.guard(WriteGate.OperationName("periods/import")):
      for
        set <- Dataset.ofParam(dataset.map(d => Dataset.Wire(d.value)))
        d <- PeriodO.parse(body.value).left.map(e => Api.Error(Problem(e.value)))
      yield imports.write(d.only(collections.map(c => HistoricalPeriod.Id(c.value.trim)).toSet), set)

  /** `POST /api/periods/fetch?source=periodo` — the live PeriodO dump, the named collections only. */
  def fetch(
      caller: Caller,
      source: PeriodSources.PeriodoUrl,
      collections: List[PeriodSources.Collections]
  ): Either[Api.Error, Period.ImportResult] =
    maintenance.guard(WriteGate.OperationName("periods/fetch")):
      if source.value.trim.toLowerCase != PeriodImports.PeriodOSource then
        Left(
          Api.Error(Problem(s"unknown period source '$source' (available: ${PeriodImports.PeriodOSource})"))
        )
      else imports.fetch(collections).left.map(e => Api.Error(Problem(e.value)))
