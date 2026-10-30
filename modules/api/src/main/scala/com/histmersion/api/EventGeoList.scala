package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** The map's events: those with a position, how many of the listed ones have none, and how many are too imprecisely
  * dated to be drawn at a year (`imprecise`: [[EventPrecision]] — the events list shows them).
  */
final case class EventGeoList(events: List[EventGeo], unplaced: Int = 0, imprecise: Int = 0)
    derives KindlingsCodecAsObject,
      KindlingsSchema
object EventGeoList:
  given Schema[EventGeoList] = summon[KindlingsSchema[EventGeoList]].schema
