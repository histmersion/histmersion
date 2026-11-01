package com.histmersion.domain.media.assets

import com.augustnagro.magnum.DbCodec

final private[assets] case class MediaGeoRow(
    citekey: Option[String],
    name: String,
    kind: Option[String],
    lat: Double,
    lon: Double,
    periodStart: Option[Long],
    periodEnd: Option[Long],
    caption: Option[String],
    uid: String,
    provider: Option[String],
    license: Option[String],
    credit: Option[String],
    hasThumb: Boolean,
    sourceUrl: Option[String],
    licenseUrl: Option[String],
    author: Option[String]
) derives DbCodec
