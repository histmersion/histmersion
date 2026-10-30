package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** `POST /api/places/rebuild` — the materialisation of `finding_places`. `byGeometry` counts links made by containment,
  * `byName` those made by the `region_name` fallback (a finding can have both); `unlocated` is how many findings ended
  * up in no place at all.
  */
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
