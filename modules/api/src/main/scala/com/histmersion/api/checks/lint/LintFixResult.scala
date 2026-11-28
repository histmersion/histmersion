package com.histmersion
package api.checks.lint

import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.maintenance.ReindexResult

@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
final case class LintFixResult(
    applied: List[LintFixResult.Applied],
    edgesResolved: LintFixResult.EdgesResolved,
    sourcesReindexed: LintFixResult.SourcesReindexed,
    chunksWritten: LintFixResult.ChunksWritten,
    remaining: LintFixResult.Remaining,
    findingsRegeocoded: LintFixResult.FindingsRegeocoded = LintFixResult.FindingsRegeocoded(0),
    findingsPlaced: LintFixResult.FindingsPlaced = LintFixResult.FindingsPlaced(0),
    placesReclassified: LintFixResult.PlacesReclassified = LintFixResult.PlacesReclassified(0),
    placesMerged: LintFixResult.PlacesMerged = LintFixResult.PlacesMerged(0),
    placesRelocated: LintFixResult.PlacesRelocated = LintFixResult.PlacesRelocated(0),
    pointAreasRemoved: LintFixResult.PointAreasRemoved = LintFixResult.PointAreasRemoved(0),
    pointAreasRemaining: LintFixResult.PointAreasRemaining = LintFixResult.PointAreasRemaining(0)
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object LintFixResult:
  type Applied = Applied.T
  object Applied extends Newtype[String, Pure]
  type EdgesResolved = EdgesResolved.T
  object EdgesResolved extends Newtype[Int, Pure]
  type SourcesReindexed = SourcesReindexed.T
  object SourcesReindexed extends Newtype[Int, Pure]
  type ChunksWritten = ChunksWritten.T
  object ChunksWritten extends Newtype[Int, Pure]
  export ReindexResult.Remaining
  type FindingsRegeocoded = FindingsRegeocoded.T
  object FindingsRegeocoded extends Newtype[Int, Pure]
  type FindingsPlaced = FindingsPlaced.T
  object FindingsPlaced extends Newtype[Int, Pure]
  type PlacesReclassified = PlacesReclassified.T
  object PlacesReclassified extends Newtype[Int, Pure]
  type PlacesMerged = PlacesMerged.T
  object PlacesMerged extends Newtype[Int, Pure]
  type PlacesRelocated = PlacesRelocated.T
  object PlacesRelocated extends Newtype[Int, Pure]
  type PointAreasRemoved = PointAreasRemoved.T
  object PointAreasRemoved extends Newtype[Int, Pure]
  type PointAreasRemaining = PointAreasRemaining.T
  object PointAreasRemaining extends Newtype[Int, Pure]
