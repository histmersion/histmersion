package com.histmersion.domain.catalogue.places

import com.histmersion.db.given
import com.augustnagro.magnum.DbCodec

/** One of a place's parents, as its bundle line reads it. */
final private[places] case class BPlaceRelRow(
    childId: String,
    parentId: String,
    kind: String,
    pNull: Boolean,
    pLo: Option[Long],
    pHi: Option[Long]
) derives DbCodec
