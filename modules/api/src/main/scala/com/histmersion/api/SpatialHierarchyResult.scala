package com.histmersion
package api

import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{movesTo, FeaturePlace}
import com.histmersion.lint.{tolerated, LintRule}

@movesTo(FeaturePlace.Kernel)
final case class SpatialHierarchyResult(
    edgesCreated: SpatialHierarchyResult.EdgesCreated,
    polygonPlaces: SpatialHierarchyResult.PolygonPlaces,
    pointPlaces: SpatialHierarchyResult.PointPlaces,
    job: Option[SpatialHierarchyResult.Job.Id] = None
) derives JsonCodec,
      KindlingsSchema,
      json.Schema

object SpatialHierarchyResult:
  object Job:
    type Id = Id.T
    object Id extends Newtype[String, Pure]
  type EdgesCreated = EdgesCreated.T
  object EdgesCreated extends Newtype[Int, Pure]
  type PolygonPlaces = PolygonPlaces.T
  object PolygonPlaces extends Newtype[Int, Pure]
  type PointPlaces = PointPlaces.T
  object PointPlaces extends Newtype[Int, Pure]
