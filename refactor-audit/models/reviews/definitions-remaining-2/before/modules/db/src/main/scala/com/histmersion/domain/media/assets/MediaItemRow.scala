package com.histmersion.domain.media.assets

import com.augustnagro.magnum.DbCodec

final private[assets] case class MediaItemRow(
    uid: String,
    name: String,
    mime: String,
    bytes: Int,
    citekey: Option[String],
    provider: Option[String],
    providerKey: Option[String],
    width: Option[Int],
    height: Option[Int],
    kind: Option[String],
    caption: Option[String],
    license: Option[String],
    licenseUrl: Option[String],
    author: Option[String],
    credit: Option[String],
    sourceUrl: Option[String],
    originalUrl: Option[String],
    retrievedAt: Option[String],
    hasThumb: Boolean
) derives DbCodec
