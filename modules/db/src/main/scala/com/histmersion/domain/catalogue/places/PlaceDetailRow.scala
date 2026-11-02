package com.histmersion.domain.catalogue.places

import com.histmersion.util.database.given
import com.augustnagro.magnum.*
import com.histmersion.domain.catalogue.Place

/** One row of [[PlaceRepo.get]] — a [[PlaceSummary]] plus the geometry and the finding count. */
final private[places] case class PlaceDetailRow(
    id: String,
    label: String,
    kind: String,
    periodStart: Option[Long],
    periodEnd: Option[Long],
    source: Option[String],
    names: String,
    contested: Boolean,
    dating: com.histmersion.api.catalogue.Place.Dating,
    importSource: Option[String],
    geometry: Option[String],
    findings: Int,
    hasBoundary: Boolean,
    timeBasis: com.histmersion.api.catalogue.Place.TimeBasis,
    precision: Place.Precision
) derives DbCodec:
  def toSummary: com.histmersion.api.catalogue.Place.Summary =
    com.histmersion.api.catalogue.Place.Summary(
      id,
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
