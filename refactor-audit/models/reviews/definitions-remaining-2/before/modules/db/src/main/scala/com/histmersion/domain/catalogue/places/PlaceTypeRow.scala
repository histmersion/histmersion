package com.histmersion.domain.catalogue.places

import com.histmersion.util.database.given
import com.augustnagro.magnum.*
import com.histmersion.api.catalogue.Period
import com.histmersion.api.catalogue.Place

final private[places] case class PlaceTypeRow(
    placeId: String,
    typeLabel: String,
    periodStart: Option[Long],
    periodEnd: Option[Long],
    citekey: Option[String]
) derives DbCodec:
  def toDto: Place.Type =
    Place.Type(com.histmersion.api.catalogue.Place.Id(placeId), typeLabel, for s <- periodStart; e <- periodEnd yield Period(s, e), citekey)
