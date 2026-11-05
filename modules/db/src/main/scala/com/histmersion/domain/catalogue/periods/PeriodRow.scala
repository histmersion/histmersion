package com.histmersion.domain.catalogue.periods

import com.histmersion.api.catalogue.{HistoricalPeriod, Period, PresetRegion}
import com.augustnagro.magnum.DbCodec

/** A `periods` row as the reads select it: the span's inclusive bounds read back. */
final private[periods] case class PeriodRow(
    id: PeriodRow.Id,
    label: PeriodRow.Label,
    authority: Option[PeriodRow.Authority],
    periodStart: com.histmersion.Year,
    periodEnd: com.histmersion.Year,
    region: Option[PeriodRow.Region],
    parentId: Option[PeriodRow.ParentId],
    preset: Option[PeriodRow.Preset]
) derives DbCodec:
  def toPeriod: HistoricalPeriod =
    HistoricalPeriod(
      id,
      label,
      authority,
      Period(periodStart, periodEnd),
      region,
      parentId,
      preset.flatMap(PresetRegion.fromWire)
    )

object PeriodRow:
  type Id = Id.T
  object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Label = Label.T
  object Label extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Authority = Authority.T
  object Authority extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Region = Region.T
  object Region extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParentId = ParentId.T
  object ParentId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Preset = Preset.T
  object Preset extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
