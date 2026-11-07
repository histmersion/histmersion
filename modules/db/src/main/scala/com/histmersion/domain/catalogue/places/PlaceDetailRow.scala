package com.histmersion.domain.catalogue.places

import com.histmersion.domain.catalogue.places.PlaceColumns.given

import com.histmersion.util.database.given
import com.augustnagro.magnum.*
import com.histmersion.domain.catalogue.Place

/** One row of [[PlaceRepo.get]] — a [[PlaceSummary]] plus the geometry and the finding count. */
final private[places] case class PlaceDetailRow(
    id: Place.Id,
    label: com.histmersion.api.catalogue.Place.Summary.Label,
    kind: com.histmersion.api.catalogue.Place.Summary.Kind,
    periodStart: Option[com.histmersion.Year],
    periodEnd: Option[com.histmersion.Year],
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
  object Geometry extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Findings = Findings.T
  object Findings extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HasBoundary = HasBoundary.T
  object HasBoundary extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
