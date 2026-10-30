package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** `POST /api/places/build-hierarchy` — compute parent-child edges from spatial containment. */
final case class SpatialHierarchyResult(
    edgesCreated: Int,
    polygonPlaces: Int,
    pointPlaces: Int,
    /** The build runs as a background job (#1946): its id, the counts then being zero — the finished job's summary has
      * them.
      */
    job: Option[String] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema
object SpatialHierarchyResult:
  given Schema[SpatialHierarchyResult] = summon[KindlingsSchema[SpatialHierarchyResult]].schema
