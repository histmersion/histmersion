package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** A named historical period following the PeriodO model — a concept with a temporal extent, an authority, a geographic
  * scope and an optional parent (e.g. "Iron Age I" under "Iron Age"). `preset` is set on the definitions the web
  * timeline offers, and names the region heading they are listed under ([[PresetRegion]]); `region` stays PeriodO's own
  * statement of where it applies.
  */
final case class HistoricalPeriod(
    id: String,
    label: String,
    authority: Option[String] = None,
    period: Period,
    region: Option[String] = None,
    parentId: Option[String] = None,
    preset: Option[PresetRegion] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema
object HistoricalPeriod:
  given Schema[HistoricalPeriod] = summon[KindlingsSchema[HistoricalPeriod]].schema
