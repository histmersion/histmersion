package com.histmersion
package domain.media

import com.histmersion.api.media.AssetLink
import com.histmersion.domain.media.assets.{HeldAsset, HeldMediaLink}
import com.histmersion.domain.media.imports.HeldCheck
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}

/** Media's rows held in memory, one record the `InMemory` services of its features share (each reads and changes its
  * own part, through lenses): the pictures, where they are shown, the figures' links and the last id given one, and
  * what each provider was asked about.
  */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
final case class MediaState(
    assets: Vector[HeldAsset],
    mediaLinks: Vector[HeldMediaLink],
    assetLinks: Vector[AssetLink],
    lastAssetLinkId: MediaState.LastAssetLinkId,
    checks: Vector[HeldCheck]
)

object MediaState:
  type LastAssetLinkId = LastAssetLinkId.T
  object LastAssetLinkId extends Newtype[Long, Pure]

  val empty: MediaState = MediaState(Vector.empty, Vector.empty, Vector.empty, LastAssetLinkId(0L), Vector.empty)
