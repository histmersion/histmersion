package com.histmersion
package api

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.catalogue.{PresetRegion, SkippedPeriod}
import com.histmersion.api.catalogue.PeriodO
import com.histmersion.api.catalogue.HistoricalPeriod
import io.github.iltotore.iron.constraint.any.Pure
final case class PeriodODataset(
    authorities: List[PeriodO.Authority],
    parents: Map[PeriodODataset.ParentsKey, PeriodODataset.ParentsValue] = Map.empty,
    skipped: List[SkippedPeriod] = Nil,
    presets: Map[PeriodODataset.PresetsKey, PresetRegion] = Map.empty,
    version: Option[PeriodODataset.Version] = None
):
  def size: PeriodODataset.PeriodCount = PeriodODataset.PeriodCount(authorities.map(_.periods.size).sum)

  def curated: Boolean = (parents.nonEmpty || presets.nonEmpty)

  /** A PeriodO period id begins with its authority's id. */
  def only(ids: Set[HistoricalPeriod.Id]): PeriodODataset =
    if ids.isEmpty then this
    else
      this.modify(
        _.authorities).setTo(authorities.filter(a => ids.contains(a.id))).modify(_.skipped).setTo(skipped.filter(s => ids.exists(i => s.id.value.startsWith(i.value)))
      )

object PeriodODataset:
  type PeriodCount = PeriodCount.T
  object PeriodCount extends Newtype[Int, Pure]

  type ParentsKey = ParentsKey.T
  object ParentsKey extends Newtype[String, Pure]
  type ParentsValue = ParentsValue.T
  object ParentsValue extends Newtype[String, Pure]
  type PresetsKey = PresetsKey.T
  object PresetsKey extends Newtype[String, Pure]
  type Version = Version.T
  object Version extends Newtype[Int, Pure]
