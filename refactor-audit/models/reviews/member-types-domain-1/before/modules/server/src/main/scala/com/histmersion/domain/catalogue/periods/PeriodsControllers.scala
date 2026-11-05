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

  def period(id: String): Either[Api.Error, Period.Detail] =
    periods.get(id).toRight(Api.Error(s"No period '$id'", Some(Write.CodeNotFound))).map { p =>
      Period.Detail(p, p.parentId.flatMap(periods.get), periods.children(id))
    }

  def search(q: Option[String], region: Option[String], from: Option[Long], to: Option[Long]): List[HistoricalPeriod] =
    periods.search(PeriodSearch(q, region, from, to))

  /** `POST /api/periods/seed?replace=` — the presets bundled with this server. */
  def seed(caller: Caller, replace: Boolean): Either[Api.Error, Period.ImportResult] =
    maintenance.guard("periods/seed")(imports.applySeed(replace).left.map(Api.Error(_)))

  /** `POST /api/periods/import` — a PeriodO dataset sent by the client (a dump, or a subset of one). */
  def importDataset(
      caller: Caller,
      collections: List[String],
      dataset: Option[String],
      body: String
  ): Either[Api.Error, Period.ImportResult] =
    maintenance.guard("periods/import"):
      for
        set <- Dataset.ofParam(dataset)
        d <- PeriodO.parse(body).left.map(Api.Error(_))
      yield imports.write(d.only(collections.map(_.trim).toSet), set)

  /** `POST /api/periods/fetch?source=periodo` — the live PeriodO dump, the named collections only. */
  def fetch(caller: Caller, source: String, collections: List[String]): Either[Api.Error, Period.ImportResult] =
    maintenance.guard("periods/fetch"):
      if source.trim.toLowerCase != PeriodImports.PeriodOSource then
        Left(Api.Error(s"unknown period source '$source' (available: ${PeriodImports.PeriodOSource})"))
      else imports.fetch(collections).left.map(Api.Error(_))
