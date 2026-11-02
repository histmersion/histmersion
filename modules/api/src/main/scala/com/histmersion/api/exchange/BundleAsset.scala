package com.histmersion.api.exchange
import hearth.kindlings.circederivation.KindlingsCodecAsObject

import java.util.UUID
import com.histmersion.api.given

final case class BundleAsset(
    uid: UUID,
    origin: UUID,
    sourceUid: UUID,
    name: String,
    mime: String,
    sha256: String,
    width: Option[Int],
    height: Option[Int],
    kind: Option[String],
    caption: Option[String],
    license: Option[String],
    /** Hex EWKB — exact, unlike WKT at a fixed number of decimals. */
    geom: Option[String],
    period: Option[BundleRange],
    updatedAt: String,
    rev: String = ""
) derives KindlingsCodecAsObject
