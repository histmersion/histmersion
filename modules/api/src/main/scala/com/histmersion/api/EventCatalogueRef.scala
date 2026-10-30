package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

final case class EventCatalogueRef(
    provider: String,
    providerId: String,
    url: String,
    title: String,
    licence: String,
    citation: String,
    vei: Option[Int] = None,
    magnitude: Option[Double] = None,
    deaths: Option[Long] = None,
    retrievedAt: Option[String] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema
object EventCatalogueRef:
  given Schema[EventCatalogueRef] = summon[KindlingsSchema[EventCatalogueRef]].schema
