package com.histmersion.domain.catalogue.places

import com.histmersion.api.*
import com.histmersion.db.given
import com.augustnagro.magnum.*

final private[places] case class PlaceTypeRow(
    placeId: String,
    typeLabel: String,
    periodStart: Option[Long],
    periodEnd: Option[Long],
    citekey: Option[String]
) derives DbCodec:
  def toDto: PlaceType =
    PlaceType(placeId, typeLabel, for s <- periodStart; e <- periodEnd yield Period(s, e), citekey)
