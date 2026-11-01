package com.histmersion.domain.catalogue.events

import com.augustnagro.magnum.DbCodec

/** A catalogue's row of one event, as its card lists it. */
final private[events] case class CatalogueRefRow(
    provider: String,
    providerId: String,
    url: Option[String],
    vei: Option[Int],
    magnitude: Option[Double],
    deaths: Option[Long],
    retrievedAt: String
) derives DbCodec
