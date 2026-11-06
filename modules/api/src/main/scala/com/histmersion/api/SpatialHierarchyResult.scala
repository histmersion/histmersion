package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

final case class SpatialHierarchyResult(
    edgesCreated: SpatialHierarchyResult.EdgesCreated,
    polygonPlaces: SpatialHierarchyResult.PolygonPlaces,
    pointPlaces: SpatialHierarchyResult.PointPlaces,
    job: Option[SpatialHierarchyResult.Job] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object SpatialHierarchyResult:
  object Job:
    type Id = Id.T
    object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EdgesCreated = EdgesCreated.T
  object EdgesCreated extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PolygonPlaces = PolygonPlaces.T
  object PolygonPlaces extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PointPlaces = PointPlaces.T
  object PointPlaces extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Job = Job.T
  object Job extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
