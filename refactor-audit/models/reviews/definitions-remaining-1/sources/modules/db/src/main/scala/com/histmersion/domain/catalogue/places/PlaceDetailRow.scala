package com.histmersion.domain.catalogue.places

import com.histmersion.domain.catalogue.places.PlaceColumns.given

import com.histmersion.util.database.given
import com.augustnagro.magnum.*
import com.histmersion.domain.catalogue.Place

/** One row of [[PlaceRepo.get]] — a [[PlaceSummary]] plus the geometry and the finding count. */
final private[places] case class PlaceDetailRow(
    id: PlaceDetailRow.Id,
    label: PlaceDetailRow.Label,
    kind: PlaceDetailRow.Kind,
    periodStart: Option[com.histmersion.Year],
    periodEnd: Option[com.histmersion.Year],
    source: Option[PlaceDetailRow.Source],
    names: PlaceDetailRow.Names,
    contested: PlaceDetailRow.Contested,
    dating: com.histmersion.api.catalogue.Place.Dating,
    importSource: Option[PlaceDetailRow.ImportSource],
    geometry: Option[PlaceDetailRow.Geometry],
    findings: PlaceDetailRow.Findings,
    hasBoundary: PlaceDetailRow.HasBoundary,
    timeBasis: com.histmersion.api.catalogue.Place.TimeBasis,
    precision: Place.Precision
) derives DbCodec:
  def toSummary: com.histmersion.api.catalogue.Place.Summary =
    com.histmersion.api.catalogue.Place.Summary(
      com.histmersion.api.catalogue.Place.Id(id),
      label,
      kind,
      periodStart,
      periodEnd,
      source,
      names,
      contested,
      dating,
      importSource
    )

object PlaceDetailRow:
  type Id = Id.T
  object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Label = Label.T
  object Label extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Kind = Kind.T
  object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Source = Source.T
  object Source extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Names = Names.T
  object Names extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Contested = Contested.T
  object Contested extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ImportSource = ImportSource.T
  object ImportSource extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Geometry = Geometry.T
  object Geometry extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Findings = Findings.T
  object Findings extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HasBoundary = HasBoundary.T
  object HasBoundary extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
