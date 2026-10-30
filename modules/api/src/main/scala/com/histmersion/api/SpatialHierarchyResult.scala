package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

final case class SpatialHierarchyResult(
    edgesCreated: Int,
    polygonPlaces: Int,
    pointPlaces: Int,
    job: Option[String] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema
object SpatialHierarchyResult:
  given Schema[SpatialHierarchyResult] = summon[KindlingsSchema[SpatialHierarchyResult]].schema
