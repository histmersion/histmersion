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
    seed: () => Either[String, String],
    download: String => Either[String, String]
):

  /** The bundled seed, parsed. */
  private def bundled: Either[com.histmersion.domain.catalogue.periods.PeriodImports.BundledResultPart1, PeriodODataset] = seed().flatMap(PeriodO.parse)

  /** Apply the bundled seed — with `replace`, the seed rows it no longer has are deleted as well. */
  def applySeed(replace: com.histmersion.domain.catalogue.periods.PeriodImports.ApplySeedReplace): Either[com.histmersion.domain.catalogue.periods.PeriodImports.ApplySeedResultPart1, Period.ImportResult] =
    bundled.map { d =>
      val r = write(d, Dataset.Seed)
      val ids = d.authorities.flatMap(_.periods.map(_.id))
      val _ = periods.retainPresets(d.presets.keys.toList)
      val removed = if replace then periods.deleteSeedExcept(ids) else 0
      d.version.foreach(periods.setSeedVersion(PeriodImports.SeedName, _))
      r.modify(_.presets).setTo(periods.presetCount()).modify(_.removed).setTo(removed)
    }

  /** Is the bundled seed newer than the one last applied here? */
  def seedOutdated: com.histmersion.domain.catalogue.periods.PeriodImports.SeedOutdatedResult =
    bundled.toOption
      .flatMap(_.version)
      .exists(v => periods.seedVersion(PeriodImports.SeedName).forall(_ < v))

  /** The live PeriodO dump, the named collections only (the configured ones when none is named). */
  def fetch(collections: List[com.histmersion.domain.catalogue.periods.PeriodImports.FetchCollections]): Either[com.histmersion.domain.catalogue.periods.PeriodImports.FetchResultPart1, Period.ImportResult] =
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
      if roots then d.authorities.count(_.periods.nonEmpty) else 0,
      periods.presetCount(),
      d.skipped.take(PeriodImports.SkippedListed),
      d.skipped.size
    )

object PeriodImports:
  /** The `seed_versions` name of the bundled period seed. */
  type BundledResultPart1 = BundledResultPart1.T
  object BundledResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApplySeedResultPart1 = ApplySeedResultPart1.T
  object ApplySeedResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApplySeedReplace = ApplySeedReplace.T
  object ApplySeedReplace extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SeedOutdatedResult = SeedOutdatedResult.T
  object SeedOutdatedResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FetchResultPart1 = FetchResultPart1.T
  object FetchResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FetchCollections = FetchCollections.T
  object FetchCollections extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val SeedName = "periods"

  /** How many skipped period definitions an import result or a job names one by one (all are counted). */
  val SkippedListed = 50

  /** The network source of periods, as `periods fetch` and `init.fetch-sources` name it. */
  val PeriodOSource = "periodo"
