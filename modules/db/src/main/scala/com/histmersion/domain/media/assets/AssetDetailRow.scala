package com.histmersion.domain.media.assets

import com.augustnagro.magnum.DbCodec

final private[media] case class AssetDetailRow(
    name: String,
    mime: String,
    bytes: Int,
    sha256: String,
    width: Option[Int],
    height: Option[Int],
    kind: Option[String],
    caption: Option[String],
    license: Option[String],
    lat: Option[Double],
    lon: Option[Double],
    periodStart: Option[Long],
    periodEnd: Option[Long]
) derives DbCodec
