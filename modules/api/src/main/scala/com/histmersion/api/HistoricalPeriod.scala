package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** Follows the PeriodO model. `region` is PeriodO's own statement of where the period applies; `preset` only names the
  * heading the timeline lists it under.
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
