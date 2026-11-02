package com.histmersion.api.catalogue
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

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
