package com.histmersion.domain.catalogue.places

import com.histmersion.util.database.given
import com.augustnagro.magnum.DbCodec
import java.util.UUID

/** A place of this collection's own, as its bundle line reads it. */
final private[places] case class BPlaceRow(
    uid: UUID,
    origin: UUID,
    id: String,
    label: String,
    kind: String,
    geom: Option[String],
    pNull: Boolean,
    pLo: Option[Long],
    pHi: Option[Long],
    source: Option[String],
    properties: Option[String],
    updatedAt: String
) derives DbCodec
