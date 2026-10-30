package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** The relief deeper than the extract, fetched tile by tile from the provider when a page shows an area
  * (`basemap.terrain.on-demand`): to `maxZoom`, cached in `dir` up to `maxBytes` (least recently used dropped first).
  * `offlineUntil`: after a failed request, none is made before then (ISO instant).
  */
final case class BasemapOnDemand(
    enabled: Boolean,
    maxZoom: Int,
    dir: String,
    tiles: Long,
    bytes: Long,
    maxBytes: Long,
    offlineUntil: Option[String] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema

object BasemapOnDemand:
  given Schema[BasemapOnDemand] = summon[KindlingsSchema[BasemapOnDemand]].schema
