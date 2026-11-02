package com.histmersion.domain.catalogue.places

import com.histmersion.util.database.given
import com.augustnagro.magnum.*
import com.histmersion.api.catalogue.{PlaceDating, PlaceSummary, PlaceTimeBasis}
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
    dating: PlaceDating,
    importSource: Option[String],
    geometry: Option[String],
    findings: Int,
    hasBoundary: Boolean,
    timeBasis: PlaceTimeBasis,
    precision: Place.Precision
) derives DbCodec:
  def toSummary: PlaceSummary =
    PlaceSummary(id, label, kind, periodStart, periodEnd, source, names, contested, dating, importSource)
