package com.histmersion
package domain.catalogue.places

import com.histmersion.domain.catalogue.places.PlaceColumns.given

import com.histmersion.util.database.given
import com.augustnagro.magnum.*
import com.histmersion.domain.catalogue.Place
import com.histmersion.util.database.NewtypeColumns.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}

/** One row of [[PlaceRepo.get]] — a [[PlaceSummary]] plus the geometry and the finding count. */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
@tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
final private[places] case class PlaceDetailRow(
    id: Place.Id,
    label: com.histmersion.api.catalogue.Place.Summary.Label,
    kind: com.histmersion.api.catalogue.Place.Summary.Kind,
    periodStart: Option[Year],
    periodEnd: Option[Year],
    source: Option[com.histmersion.api.catalogue.Place.Summary.Source],
    names: com.histmersion.api.catalogue.Place.Summary.Names,
    contested: com.histmersion.api.catalogue.Place.Summary.Contested,
    dating: com.histmersion.api.catalogue.Place.Dating,
    importSource: Option[com.histmersion.api.catalogue.Place.Summary.ImportSource],
    geometry: Option[PlaceDetailRow.Geometry],
    findings: PlaceDetailRow.Findings,
    hasBoundary: PlaceDetailRow.HasBoundary,
    timeBasis: com.histmersion.api.catalogue.Place.TimeBasis,
    precision: Place.Precision
) derives DbCodec:
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
  def toSummary: com.histmersion.api.catalogue.Place.Summary =
    com.histmersion.api.catalogue.Place.Summary(
      com.histmersion.api.catalogue.Place.Id(id.value),
      label,
      kind,
      periodStart.map(y => com.histmersion.api.catalogue.Year(y.value.toLong)),
      periodEnd.map(y => com.histmersion.api.catalogue.Year(y.value.toLong)),
      source,
      names,
      contested,
      dating,
      importSource
    )

object PlaceDetailRow:
  type Geometry = Geometry.T
  object Geometry extends Newtype[String, Pure]
  type Findings = Findings.T
  object Findings extends Newtype[Int, Pure]
  type HasBoundary = HasBoundary.T
  object HasBoundary extends Newtype[Boolean, Pure]
