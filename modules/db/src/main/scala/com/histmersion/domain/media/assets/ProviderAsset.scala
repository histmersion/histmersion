package com.histmersion
package domain.media.assets

import com.histmersion.api.media.AssetKind
import com.histmersion.domain.media.MediaRole
import io.github.iltotore.iron.constraint.any.Pure

/** A provider's picture to store. `bytes` are the capped rendition, `thumb` the card's thumbnail. */
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
  type Mime = Mime.T
  object Mime extends Newtype[String, Pure]
  type Width = Width.T
  object Width extends Newtype[Int, Pure]
  type Height = Height.T
  object Height extends Newtype[Int, Pure]
  type ThumbMime = ThumbMime.T
  object ThumbMime extends Newtype[String, Pure]
  type ThumbWidth = ThumbWidth.T
  object ThumbWidth extends Newtype[Int, Pure]
  type ThumbHeight = ThumbHeight.T
  object ThumbHeight extends Newtype[Int, Pure]
  type License = License.T
  object License extends Newtype[String, Pure]
  type LicenseUrl = LicenseUrl.T
  object LicenseUrl extends Newtype[String, Pure]
  type Author = Author.T
  object Author extends Newtype[String, Pure]
  type Credit = Credit.T
  object Credit extends Newtype[String, Pure]
  type SourceUrl = SourceUrl.T
  object SourceUrl extends Newtype[String, Pure]
  type OriginalUrl = OriginalUrl.T
  object OriginalUrl extends Newtype[String, Pure]
  type Kind = Kind.T
  object Kind extends Newtype[String, Pure]

  def kindOf(role: MediaRole.Wire): Kind = role.value match
    case MediaRole.LocatorMap | MediaRole.RouteMap | MediaRole.Map => Kind(AssetKind.Map)
    case MediaRole.Plan                                            => Kind(AssetKind.Plan)
    case _                                                         => Kind(AssetKind.Photo)
