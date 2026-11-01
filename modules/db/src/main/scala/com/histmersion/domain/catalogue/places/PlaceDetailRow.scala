package com.histmersion.domain.catalogue.places

import com.histmersion.api.*
import com.histmersion.db.given
import com.augustnagro.magnum.*
import com.histmersion.values.LocationPrecision

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
    precision: LocationPrecision
) derives DbCodec:
  def toSummary: PlaceSummary =
    PlaceSummary(id, label, kind, periodStart, periodEnd, source, names, contested, dating, importSource)
