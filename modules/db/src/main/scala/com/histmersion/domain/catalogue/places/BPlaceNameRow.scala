package com.histmersion.domain.catalogue.places

import com.histmersion.util.database.given
import com.augustnagro.magnum.DbCodec

/** One of a place's names, as its bundle line reads it. */
final private[places] case class BPlaceNameRow(
    placeId: String,
    name: String,
    lang: String,
    pNull: Boolean,
    pLo: Option[Long],
    pHi: Option[Long]
) derives DbCodec
