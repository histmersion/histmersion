package com.histmersion.api

/** One period definition as PeriodO states it, its years already converted to signed years. */
final case class PeriodODefinition(
    id: String,
    label: String,
    period: Period,
    region: Option[String],
    broader: Option[String]
)
