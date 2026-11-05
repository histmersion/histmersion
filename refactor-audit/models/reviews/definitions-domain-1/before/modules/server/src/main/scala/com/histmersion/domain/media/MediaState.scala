package com.histmersion.domain.media

import com.histmersion.api.media.AssetLink
import com.histmersion.domain.media.assets.{HeldAsset, HeldMediaLink}
import com.histmersion.domain.media.imports.HeldCheck

/** Media's rows held in memory, one record the `InMemory` services of its features share (each reads and changes its
  * own part, through lenses): the pictures, where they are shown, the figures' links and the last id given one, and
  * what each provider was asked about.
  */
final case class MediaState(
    assets: Vector[HeldAsset],
    mediaLinks: Vector[HeldMediaLink],
    assetLinks: Vector[AssetLink],
    lastAssetLinkId: Long,
    checks: Vector[HeldCheck]
)

object MediaState:
  val empty: MediaState = MediaState(Vector.empty, Vector.empty, Vector.empty, 0L, Vector.empty)
