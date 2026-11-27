package com.histmersion
package api

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.catalogue.{PresetRegion, SkippedPeriod}
import com.histmersion.api.catalogue.PeriodO
import com.histmersion.api.catalogue.HistoricalPeriod
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{movesTo, FeaturePlace}
import com.histmersion.lint.{tolerated, LintRule}
@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
@movesTo(FeaturePlace.Kernel)
final case class PeriodODataset(
    authorities: List[PeriodO.Authority],
    parents: Map[PeriodODataset.ParentsKey, PeriodODataset.ParentsValue] = Map.empty,
    skipped: List[SkippedPeriod] = Nil,
    presets: Map[PeriodODataset.PresetsKey, PresetRegion] = Map.empty,
    version: Option[PeriodODataset.Version] = None
):
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def size: PeriodODataset.PeriodCount = PeriodODataset.PeriodCount(authorities.map(_.periods.size).sum)

  def curated: Boolean = (parents.nonEmpty || presets.nonEmpty)

  /** A PeriodO period id begins with its authority's id. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def only(ids: Set[HistoricalPeriod.Id]): PeriodODataset =
    if ids.isEmpty then this
    else
      this
        .modify(_.authorities)
        .setTo(authorities.filter(a => ids.contains(a.id)))
        .modify(_.skipped)
        .setTo(skipped.filter(s => ids.exists(i => s.id.value.startsWith(i.value))))

object PeriodODataset:
  type PeriodCount = PeriodCount.T
  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  object PeriodCount extends Newtype[Int, Pure]

  type ParentsKey = ParentsKey.T
  object ParentsKey extends Newtype[String, Pure]
  type ParentsValue = ParentsValue.T
  object ParentsValue extends Newtype[String, Pure]
  type PresetsKey = PresetsKey.T
  object PresetsKey extends Newtype[String, Pure]
  type Version = Version.T
  object Version extends Newtype[Int, Pure]
