package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

final case class EventFindingRef(
    id: String,
    citekey: String,
    claim: String,
    km: Double,
    yearGap: Long,
    periodStart: Option[Long] = None,
    periodEnd: Option[Long] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema
object EventFindingRef:
  given Schema[EventFindingRef] = summon[KindlingsSchema[EventFindingRef]].schema
