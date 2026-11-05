package com.histmersion.domain.media.assets

import com.augustnagro.magnum.DbCodec

/** A figure's row as its listing reads it (Magnum maps a SELECT's columns positionally). */
final private[assets] case class AssetInfoRow(
    name: String,
    mime: String,
    bytes: Int,
    sha256: String,
    width: Option[Int],
    height: Option[Int]
) derives DbCodec
