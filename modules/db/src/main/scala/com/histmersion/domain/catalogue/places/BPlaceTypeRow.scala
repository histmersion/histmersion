package com.histmersion.domain.catalogue.places

import com.histmersion.db.given
import com.augustnagro.magnum.DbCodec

/** One of a place's types, as its bundle line reads it. */
final private[places] case class BPlaceTypeRow(
    placeId: String,
    typeLabel: String,
    pNull: Boolean,
    pLo: Option[Long],
    pHi: Option[Long],
    citekey: Option[String]
) derives DbCodec
