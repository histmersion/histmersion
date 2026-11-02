package com.histmersion.domain.catalogue.places

import com.histmersion.api.*
import com.histmersion.util.database.given
import com.augustnagro.magnum.*

final private[places] case class FootprintRow(
    id: Int,
    placeId: String,
    role: String,
    periodStart: Option[Long],
    periodEnd: Option[Long],
    certainty: Option[String],
    accuracyM: Option[Float],
    citekey: Option[String]
) derives DbCodec:
  def toDto: PlaceFootprint =
    PlaceFootprint(
      id,
      placeId,
      role,
      for s <- periodStart; e <- periodEnd yield Period(s, e),
      certainty,
      accuracyM,
      citekey
    )
