package com.histmersion.domain.catalogue.periods

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.{Dataset, PeriodODataset}
import com.histmersion.api.catalogue.PeriodO
import com.histmersion.api.catalogue.Period

/** How periods come in: the bundled seed (its rows upserted, its presets made the only presets, its version recorded),
  * a PeriodO dataset a client sends, and the live PeriodO dump's configured collections. `seed` reads the bundled
  * file's text and `download` fetches a URL; both are the composition's.
  */
final class PeriodImports(
    periods: PeriodService,
    sources: () => PeriodSources,
    seed: () => Either[PeriodImports.SeedResultPart1, PeriodImports.SeedResultPart2],
    download: PeriodImports.DownloadArg1 => Either[PeriodImports.DownloadResultPart1, PeriodImports.DownloadResultPart2]
):

  /** The bundled seed, parsed. */
  private def bundled: Either[com.histmersion.domain.catalogue.periods.PeriodImports.Problem, PeriodODataset] = seed().flatMap(PeriodO.parse)

  /** Apply the bundled seed — with `replace`, the seed rows it no longer has are deleted as well. */
  def applySeed(replace: com.histmersion.domain.catalogue.periods.PeriodImports.Replace): Either[com.histmersion.domain.catalogue.periods.PeriodImports.Problem, Period.ImportResult] =
    bundled.map { d =>
      val r = write(d, Dataset.Seed)
      val ids = d.authorities.flatMap(_.periods.map(_.id))
      val _ = periods.retainPresets(d.presets.keys.toList)
      val removed = if replace then periods.deleteSeedExcept(ids) else 0
      d.version.foreach(periods.setSeedVersion(PeriodImports.SeedName, _))
      r.modify(_.presets).setTo(periods.presetCount()).modify(_.removed).setTo(removed)
    }

  /** Is the bundled seed newer than the one last applied here? */
  def seedOutdated: Boolean =
    bundled.toOption
      .flatMap(_.version)
      .exists(v => periods.seedVersion(PeriodImports.SeedName).forall(_ < v))

  /** The live PeriodO dump, the named collections only (the configured ones when none is named). */
  def fetch(collections: List[com.histmersion.domain.catalogue.periods.PeriodSources.Collections]): Either[com.histmersion.domain.catalogue.periods.PeriodImports.Problem, Period.ImportResult] =
    val s = sources()
    val wanted = collections.map(_.trim).filter(_.nonEmpty) match
      case Nil => s.collections
      case cs  => cs
    for
      raw <- download(s.periodoUrl)
      all <- PeriodO.parse(raw)
      d = all.only(wanted.toSet)
      _ <-
        if d.authorities.isEmpty then Left(s"no PeriodO collection ${wanted.mkString(", ")} in the dump")
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
      n,
      if roots then d.authorities.count(_.periods.nonEmpty) else com.histmersion.api.catalogue.Period.ImportResult.Roots(0),
      periods.presetCount(),
      d.skipped.take(PeriodImports.SkippedListed),
      d.skipped.size
    )

object PeriodImports:
  type Dataset = Dataset.T
  object Dataset extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DatasetBody = DatasetBody.T
  object DatasetBody extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Replace = Replace.T
  object Replace extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  /** The `seed_versions` name of the bundled period seed. */
  type SeedResultPart1 = SeedResultPart1.T
  object SeedResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SeedResultPart2 = SeedResultPart2.T
  object SeedResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DownloadArg1 = DownloadArg1.T
  object DownloadArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DownloadResultPart1 = DownloadResultPart1.T
  object DownloadResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DownloadResultPart2 = DownloadResultPart2.T
  object DownloadResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  val SeedName = "periods"

  /** How many skipped period definitions an import result or a job names one by one (all are counted). */
  val SkippedListed = 50

  /** The network source of periods, as `periods fetch` and `init.fetch-sources` name it. */
  val PeriodOSource = "periodo"
