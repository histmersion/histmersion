package com.histmersion.domain.media.assets

/** A picture shown on a place, an event or a source, in a role, as the in-memory store holds it (`media_links`). */
final case class HeldMediaLink(
    assetUid: String,
    targetType: String,
    targetId: String,
    role: String,
    property: Option[String],
    item: Option[String],
    rank: Int,
    basis: Option[String]
)
