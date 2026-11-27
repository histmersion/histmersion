package com.histmersion
package domain.media.assets

import com.histmersion.api.media.AssetKind
import com.histmersion.domain.media.MediaRole
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}
import com.histmersion.domain.catalogue.events.EventSelection

/** A provider's picture to store. `bytes` are the capped rendition, `thumb` the card's thumbnail. */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
final case class ProviderAsset(
    provider: ProviderAsset.Provider,
    key: ProviderAsset.Key,
    name: ProviderAsset.Name,
    mime: ProviderAsset.Mime,
    bytes: ProviderAsset.Bytes,
    width: Option[ProviderAsset.Width],
    height: Option[ProviderAsset.Height],
    thumb: Option[ProviderAsset.Thumb],
    thumbMime: Option[ProviderAsset.ThumbMime],
    thumbWidth: Option[ProviderAsset.ThumbWidth],
    thumbHeight: Option[ProviderAsset.ThumbHeight],
    license: ProviderAsset.License,
    licenseUrl: Option[ProviderAsset.LicenseUrl],
    author: Option[ProviderAsset.Author],
    credit: ProviderAsset.Credit,
    sourceUrl: ProviderAsset.SourceUrl,
    originalUrl: Option[ProviderAsset.OriginalUrl],
    kind: Option[ProviderAsset.Kind]
)

object ProviderAsset:

  /** Pure: the media kind (the layer's colour) of a picture shown in `role`. */

  type Bytes = Bytes.T
  object Bytes extends Newtype[Array[Byte], Pure]
  type Thumb = Thumb.T
  object Thumb extends Newtype[Array[Byte], Pure]

  type Provider = Provider.T
  object Provider extends Newtype[String, Pure]
  type Key = Key.T
  object Key extends Newtype[String, Pure]
  type Name = Name.T
  object Name extends Newtype[String, Pure]
  export AssetBytesRow.Mime
  export AssetDetailRow.Width
  export AssetDetailRow.Height
  type ThumbMime = ThumbMime.T
  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  object ThumbMime extends Newtype[String, Pure]
  type ThumbWidth = ThumbWidth.T
  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  object ThumbWidth extends Newtype[Int, Pure]
  type ThumbHeight = ThumbHeight.T
  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  object ThumbHeight extends Newtype[Int, Pure]
  export AssetDetailRow.License
  export MediaGeoRow.LicenseUrl
  export MediaGeoRow.Author
  export MediaGeoRow.Credit
  export MediaGeoRow.SourceUrl
  export MediaItemRow.OriginalUrl
  export EventSelection.Kind

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def kindOf(role: MediaRole.Wire): Kind = role.value match
    case MediaRole.LocatorMap | MediaRole.RouteMap | MediaRole.Map => Kind(AssetKind.Map)
    case MediaRole.Plan                                            => Kind(AssetKind.Plan)
    case _                                                         => Kind(AssetKind.Photo)
