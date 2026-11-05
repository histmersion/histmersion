package com.histmersion.api

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.catalogue.{PresetRegion, SkippedPeriod}
import com.histmersion.api.catalogue.PeriodO
final case class PeriodODataset(
    authorities: List[PeriodO.Authority],
    parents: Map[PeriodODataset.ParentsKey, PeriodODataset.ParentsValue] = Map.empty,
    skipped: List[SkippedPeriod] = Nil,
    presets: Map[PeriodODataset.PresetsKey, PresetRegion] = Map.empty,
    version: Option[PeriodODataset.Version] = None
):
  def size: Int = authorities.map(_.periods.size).sum

  def curated: Boolean = parents.nonEmpty || presets.nonEmpty

  /** A PeriodO period id begins with its authority's id. */
  def only(ids: Set[String]): PeriodODataset =
    if ids.isEmpty then this
    else
      this.modify(
        _.authorities).setTo(authorities.filter(a => ids.contains(a.id))).modify(_.skipped).setTo(skipped.filter(s => ids.exists(s.id.startsWith))
      )

object PeriodODataset:
  type ParentsKey = ParentsKey.T
  object ParentsKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParentsValue = ParentsValue.T
  object ParentsValue extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PresetsKey = PresetsKey.T
  object PresetsKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Version = Version.T
  object Version extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
