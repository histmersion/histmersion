package com.histmersion.domain.media.assets

import com.augustnagro.magnum.DbCodec

final private[media] case class MediaThumbRow(
    uid: String,
    role: String,
    credit: String,
    license: Option[String],
    licenseUrl: Option[String],
    sourceUrl: Option[String],
    width: Option[Int],
    height: Option[Int]
) derives DbCodec
