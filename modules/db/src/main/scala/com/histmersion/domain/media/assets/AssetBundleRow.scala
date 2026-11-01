package com.histmersion.domain.media.assets

import com.augustnagro.magnum.DbCodec
import java.util.UUID

/** An asset as a bundle reads it: its line's fields, the size of its bytes, its period's inclusive bounds. */
final private[assets] case class AssetBundleRow(
    uid: UUID,
    origin: UUID,
    sourceUid: UUID,
    name: String,
    mime: String,
    sha: String,
    bytes: Long,
    width: Option[Int],
    height: Option[Int],
    kind: Option[String],
    caption: Option[String],
    license: Option[String],
    geom: Option[String],
    pNull: Boolean,
    pLo: Option[Long],
    pHi: Option[Long],
    updatedAt: String
) derives DbCodec
