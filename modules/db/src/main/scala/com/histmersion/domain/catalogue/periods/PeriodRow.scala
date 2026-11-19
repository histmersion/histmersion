package com.histmersion
package domain.catalogue.periods

import com.histmersion.api.catalogue.{HistoricalPeriod, Period, PresetRegion}
import com.augustnagro.magnum.DbCodec
import com.histmersion.util.database.NewtypeColumns.given

/** A `periods` row as the reads select it: the span's inclusive bounds read back. */
final private[periods] case class PeriodRow(
    id: PeriodRow.Id,
    label: PeriodRow.Label,
    authority: Option[PeriodRow.Authority],
    periodStart: Year,
    periodEnd: Year,
    region: Option[PeriodRow.Region],
    parentId: Option[PeriodRow.ParentId],
    preset: Option[PeriodRow.Preset]
) derives DbCodec:
  def toPeriod: HistoricalPeriod =
    HistoricalPeriod(
      id,
      label,
      authority,
      Period(
        com.histmersion.api.catalogue.Year(periodStart.value.toLong),
        com.histmersion.api.catalogue.Year(periodEnd.value.toLong)
      ),
      region,
      parentId,
      preset.flatMap(PresetRegion.fromWire)
    )

object PeriodRow:
  export HistoricalPeriod.{Authority, Id, Label, ParentId, Region}
  export PresetRegion.Wire as Preset
