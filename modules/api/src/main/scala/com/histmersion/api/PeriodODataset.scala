package com.histmersion.api

import com.histmersion.api.catalogue.{PresetRegion, SkippedPeriod}
import com.histmersion.api.catalogue.PeriodO
final case class PeriodODataset(
    authorities: List[PeriodO.Authority],
    parents: Map[String, String] = Map.empty,
    skipped: List[SkippedPeriod] = Nil,
    presets: Map[String, PresetRegion] = Map.empty,
    version: Option[Int] = None
):
  def size: Int = authorities.map(_.periods.size).sum

  def curated: Boolean = parents.nonEmpty || presets.nonEmpty

  /** A PeriodO period id begins with its authority's id. */
  def only(ids: Set[String]): PeriodODataset =
    if ids.isEmpty then this
    else
      copy(
        authorities = authorities.filter(a => ids.contains(a.id)),
        skipped = skipped.filter(s => ids.exists(s.id.startsWith))
      )
