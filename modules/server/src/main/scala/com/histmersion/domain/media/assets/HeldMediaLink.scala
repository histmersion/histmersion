package com.histmersion.domain.media.assets

/** A picture shown on a place, an event or a source, in a role, as the in-memory store holds it (`media_links`). */
final case class HeldMediaLink(
    assetUid: AssetService.AssetUid,
    targetType: AssetService.TargetType,
    targetId: AssetService.TargetId,
    role: com.histmersion.domain.media.MediaRole.Wire,
    property: Option[AssetService.LinkProperty],
    item: Option[AssetService.LinkItem],
    rank: AssetService.LinkRank,
    basis: Option[com.histmersion.domain.media.imports.MediaMatch.Basis]
)
