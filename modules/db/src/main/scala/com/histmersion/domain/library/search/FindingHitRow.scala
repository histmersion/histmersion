package com.histmersion.domain.library.search

import com.histmersion.api.*
import com.histmersion.util.database.given

import com.augustnagro.magnum.*

/** One row of `FilterSql.findingsQuery`. */
final private[search] case class FindingHitRow(
    citekey: String,
    id: String,
    claim: String,
    regionName: Option[String],
    lon: Option[Double],
    lat: Option[Double],
    periodStart: Option[Long],
    periodEnd: Option[Long],
    periodName: Option[String],
    tags: List[String],
    /** The gazetteer labels / ids this finding was materialised into (plan §1.6a). */
    places: List[String] = Nil,
    placeIds: List[String] = Nil
) derives DbCodec:
  def toMatch: FindingMatch =
    FindingMatch(id, claim, regionName, lon, lat, periodStart, periodEnd, periodName, tags, Nil, places)
  def assignedPlaceIds: List[String] = placeIds
