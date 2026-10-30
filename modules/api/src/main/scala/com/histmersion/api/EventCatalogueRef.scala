package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

// ── an event's catalogue records, as its card shows them ────────────────────────────────────────

/** One catalogue's record of an event, with what its licence asks to be shown. */
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
