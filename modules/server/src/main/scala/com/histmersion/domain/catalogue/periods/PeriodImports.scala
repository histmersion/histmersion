package com.histmersion.domain.catalogue.periods

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.{Dataset, PeriodODataset}
import com.histmersion.api.catalogue.{HistoricalPeriod, PeriodO}
import com.histmersion.api.catalogue.Period

/** How periods come in: the bundled seed (its rows upserted, its presets made the only presets, its version recorded),
  * a PeriodO dataset a client sends, and the live PeriodO dump's configured collections. `seed` reads the bundled
  * file's text and `download` fetches a URL; both are the composition's.
  */
final class PeriodImports(
    periods: PeriodService,
    sources: () => PeriodSources,
    seed: () => Either[PeriodImports.Problem, PeriodImports.Document],
    download: PeriodSources.PeriodoUrl => Either[PeriodImports.Problem, PeriodImports.Document]
):

  /** The bundled seed, parsed. */
  private def bundled: Either[PeriodImports.Problem, PeriodODataset] = seed().flatMap(parsed)

  private def parsed(document: PeriodImports.Document): Either[PeriodImports.Problem, PeriodODataset] =
    PeriodO.parse(document.value).left.map(why => PeriodImports.Problem(why.value))

  /** Apply the bundled seed — with `replace`, the seed rows it no longer has are deleted as well. */
  def applySeed(replace: PeriodImports.Replace): Either[PeriodImports.Problem, Period.ImportResult] =
    bundled.map { d =>
      val r = write(d, Dataset.Seed)
      val ids = d.authorities.flatMap(_.periods.map(_.id))
      val _ = periods.retainPresets(d.presets.keys.toList.map(k => HistoricalPeriod.Id(k.value)))
      val removed = if replace.value then periods.deleteSeedExcept(ids).value else 0
      d.version.foreach(v =>
        periods.setSeedVersion(PeriodService.SeedName(PeriodImports.SeedName), PeriodService.SeedVersion(v.value))
      )
      r.modify(_.presets)
        .setTo(Period.ImportResult.Presets(periods.presetCount().value))
        .modify(_.removed)
        .setTo(Period.ImportResult.Removed(removed))
    }

  /** Is the bundled seed newer than the one last applied here? */
  def seedOutdated: Boolean =
    bundled.toOption
      .flatMap(_.version)
      .exists(v => periods.seedVersion(PeriodService.SeedName(PeriodImports.SeedName)).forall(_.value < v.value))

  /** The live PeriodO dump, the named collections only (the configured ones when none is named). */
  def fetch(collections: List[PeriodSources.Collections]): Either[PeriodImports.Problem, Period.ImportResult] =
    val s = sources()
    val wanted = collections.map(_.value.trim).filter(_.nonEmpty) match
      case Nil => s.collections.map(_.value)
      case cs  => cs
    for
      raw <- download(s.periodoUrl)
      all <- parsed(raw)
      d = all.only(wanted.map(HistoricalPeriod.Id(_)).toSet)
      _ <-
        if d.authorities.isEmpty then
          Left(PeriodImports.Problem(s"no PeriodO collection ${wanted.mkString(", ")} in the dump"))
        else Right(())
    yield write(d, Dataset.Seed)

  /** Upsert a dataset. A raw PeriodO dump (no curated overlay) is grouped under one node per authority, and every
    * period already in the store keeps its place; a curated seed places its own rows ([[PeriodO.toPeriods]]).
    */
  def write(d: PeriodODataset, dataset: Dataset): Period.ImportResult =
    val roots = !d.curated
    val ids = d.authorities.flatMap(a => a.id :: a.periods.map(_.id))
    val rows = PeriodO.toPeriods(d, periods.placement(ids), roots)
    val n = periods.write(rows, dataset)
    Period.ImportResult(
      Period.ImportResult.Periods(n.value),
      Period.ImportResult.Roots(if roots then d.authorities.count(_.periods.nonEmpty) else 0),
      Period.ImportResult.Presets(periods.presetCount().value),
      d.skipped.take(PeriodImports.SkippedListed),
      Period.ImportResult.SkippedCount(d.skipped.size)
    )

object PeriodImports:
  type Dataset = Dataset.T
  object Dataset extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DatasetBody = DatasetBody.T
  object DatasetBody extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  /** The text of a PeriodO dataset: the bundled seed or a dump. */
  type Document = Document.T
  object Document extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Replace = Replace.T
  object Replace extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  /** The `seed_versions` name of the bundled period seed. */


  val SeedName = "periods"

  /** How many skipped period definitions an import result or a job names one by one (all are counted). */
  val SkippedListed = 50

  /** The network source of periods, as `periods fetch` and `init.fetch-sources` name it. */
  val PeriodOSource = "periodo"
