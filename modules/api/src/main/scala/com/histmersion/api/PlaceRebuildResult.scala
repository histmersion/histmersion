package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

final case class PlaceRebuildResult(
    links: Int,
    byGeometry: Int,
    byName: Int,
    located: Int,
    unlocated: Int,
    places: Int
) derives KindlingsCodecAsObject,
      KindlingsSchema
object PlaceRebuildResult:
  given Schema[PlaceRebuildResult] = summon[KindlingsSchema[PlaceRebuildResult]].schema
