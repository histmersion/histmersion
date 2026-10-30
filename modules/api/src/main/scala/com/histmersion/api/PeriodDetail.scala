package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

final case class PeriodDetail(
    period: HistoricalPeriod,
    parent: Option[HistoricalPeriod] = None,
    children: List[HistoricalPeriod] = Nil
) derives KindlingsCodecAsObject,
      KindlingsSchema
object PeriodDetail:
  given Schema[PeriodDetail] = summon[KindlingsSchema[PeriodDetail]].schema
