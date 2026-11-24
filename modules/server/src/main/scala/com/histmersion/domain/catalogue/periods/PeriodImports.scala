package com.histmersion
package domain.catalogue.periods
import com.histmersion.util.problem.{Legacy, Problem}

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.{Dataset, PeriodODataset}
import com.histmersion.api.catalogue.{HistoricalPeriod, PeriodO}
import com.histmersion.api.catalogue.Period
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.util.database.WritePermit
import com.histmersion.util.database.Unused
import com.histmersion.domain.Creator
import com.histmersion.lint.{LintRule, tolerated}

/** How periods come in: the bundled seed (its rows upserted, its presets made the only presets, its version recorded),
  * a PeriodO dataset a client sends, and the live PeriodO dump's configured collections. `seed` reads the bundled
  * file's text and `download` fetches a URL; both are the composition's.
  */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
final class PeriodImports(
    periods: PeriodService,
    sources: () => PeriodSources,
    seed: () => Either[Problem, PeriodImports.Document],
    download: PeriodSources.PeriodoUrl => Either[Problem, PeriodImports.Document]
):

  /** The bundled seed, parsed. */
  private def bundled: Either[Problem, PeriodODataset] = seed().flatMap(parsed)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def parsed(document: PeriodImports.Document): Either[Problem, PeriodODataset] =
    PeriodO.parse(document.value)

  /** Apply the bundled seed — with `replace`, the seed rows it no longer has are deleted as well. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def applySeed(
      replace: PeriodImports.Replace
  )(using WritePermit)(using Creator.New): Either[Problem, Period.ImportResult] =
    bundled.map { d =>
      val r = write(d, Dataset.Seed)
      val ids = d.authorities.flatMap(_.periods.map(_.id))
      Unused(periods.retainPresets(d.presets.keys.toList.map(k => HistoricalPeriod.Id(k.value))), Unused.Why.Reported)
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
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def seedOutdated: Boolean =
    bundled.toOption
      .flatMap(_.version)
      .exists(v => periods.seedVersion(PeriodService.SeedName(PeriodImports.SeedName)).forall(_.value < v.value))

  /** The live PeriodO dump, the named collections only (the configured ones when none is named). */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
  def fetch(collections: List[PeriodSources.Collections])(using
      WritePermit
  )(using Creator.New): Either[Problem, Period.ImportResult] =
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
          Left(Problem.Unclassified(Legacy.Text(s"no PeriodO collection ${wanted.mkString(", ")} in the dump")))
        else Right(())
    yield write(d, Dataset.Seed)

  /** Upsert a dataset. A raw PeriodO dump (no curated overlay) is grouped under one node per authority, and every
    * period already in the store keeps its place; a curated seed places its own rows ([[PeriodO.toPeriods]]).
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.UnnamedBoolean, "pending ADR 0027 (a Boolean argument is passed by name)")
  def write(d: PeriodODataset, dataset: Dataset)(using WritePermit)(using Creator.New): Period.ImportResult =
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
  object Dataset extends Newtype[String, Pure]
  type DatasetBody = DatasetBody.T
  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  object DatasetBody extends Newtype[String, Pure]

  /** The text of a PeriodO dataset: the bundled seed or a dump. */
  type Document = Document.T
  object Document extends Newtype[String, Pure]
  type Replace = Replace.T
  object Replace extends Newtype[Boolean, Pure]

  /** The `seed_versions` name of the bundled period seed. */

  val SeedName = "periods"

  /** How many skipped period definitions an import result or a job names one by one (all are counted). */
  val SkippedListed = 50

  /** The network source of periods, as `periods fetch` and `init.fetch-sources` name it. */
  val PeriodOSource = "periodo"
