package com.histmersion
package api.catalogue.periodo

import hearth.kindlings.optics.syntax.*
import com.histmersion.api.given
import com.histmersion.api.catalogue.HistoricalPeriod
import com.histmersion.api.catalogue.Period

final case class Definition(
    id: HistoricalPeriod.Id,
    label: HistoricalPeriod.Label,
    period: Period,
    region: Option[HistoricalPeriod.Region],
    broader: Option[HistoricalPeriod.Id]
)
