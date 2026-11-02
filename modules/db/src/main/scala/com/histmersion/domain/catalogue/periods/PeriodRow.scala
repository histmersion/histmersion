package com.histmersion.domain.catalogue.periods

import com.histmersion.api.catalogue.{HistoricalPeriod, Period, PresetRegion}
import com.augustnagro.magnum.DbCodec

/** A `periods` row as the reads select it: the span's inclusive bounds read back. */
final private[periods] case class PeriodRow(
    id: String,
    label: String,
    authority: Option[String],
    periodStart: Long,
    periodEnd: Long,
    region: Option[String],
    parentId: Option[String],
    preset: Option[String]
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
