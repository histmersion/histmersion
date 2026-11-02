package com.histmersion.api.catalogue
import com.histmersion.api.given
final case class PeriodODefinition(
    id: String,
    label: String,
    period: Period,
    region: Option[String],
    broader: Option[String]
)
