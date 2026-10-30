package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

final case class EventGeoList(events: List[EventGeo], unplaced: Int = 0, imprecise: Int = 0)
    derives KindlingsCodecAsObject,
      KindlingsSchema
object EventGeoList:
  given Schema[EventGeoList] = summon[KindlingsSchema[EventGeoList]].schema
