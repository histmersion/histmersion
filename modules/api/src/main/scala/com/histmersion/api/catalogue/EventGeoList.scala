package com.histmersion.api.catalogue
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class EventGeoList(events: List[EventGeo], unplaced: Int = 0, imprecise: Int = 0)
    derives KindlingsCodecAsObject,
      KindlingsSchema
object EventGeoList:
  given Schema[EventGeoList] = summon[KindlingsSchema[EventGeoList]].schema
