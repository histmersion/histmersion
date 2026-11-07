package com.histmersion.domain.media.assets

import com.histmersion.api.media.AssetKind
import com.histmersion.domain.media.MediaRole

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
  object Bytes extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Thumb = Thumb.T
  object Thumb extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]

  type Provider = Provider.T
  object Provider extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Key = Key.T
  object Key extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Name = Name.T
  object Name extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Mime = Mime.T
  object Mime extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Width = Width.T
  object Width extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Height = Height.T
  object Height extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ThumbMime = ThumbMime.T
  object ThumbMime extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ThumbWidth = ThumbWidth.T
  object ThumbWidth extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ThumbHeight = ThumbHeight.T
  object ThumbHeight extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type License = License.T
  object License extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LicenseUrl = LicenseUrl.T
  object LicenseUrl extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Author = Author.T
  object Author extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Credit = Credit.T
  object Credit extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SourceUrl = SourceUrl.T
  object SourceUrl extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OriginalUrl = OriginalUrl.T
  object OriginalUrl extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Kind = Kind.T
  object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def kindOf(role: com.histmersion.domain.media.MediaRole.Wire): com.histmersion.domain.media.assets.ProviderAsset.Kind = role.value match
    case MediaRole.LocatorMap | MediaRole.RouteMap | MediaRole.Map => Kind(AssetKind.Map)
    case MediaRole.Plan                                            => Kind(AssetKind.Plan)
    case _                                                         => Kind(AssetKind.Photo)
