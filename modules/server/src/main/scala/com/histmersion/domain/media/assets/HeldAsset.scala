package com.histmersion.domain.media.assets

import java.time.Instant

/** A picture as the in-memory store holds it: a row of `assets` — a source's figure (its `citekey`) or a provider's
  * picture (its `provider` and key) — with its point and inclusive span when it has them.
  */
final case class HeldAsset(
    uid: AssetService.AssetUid,
    citekey: Option[com.histmersion.domain.library.Citekey],
    name: AssetService.AssetName,
    mime: AssetService.Mime,
    bytes: AssetService.Bytes,
    sha256: com.histmersion.Sha256,
    width: Option[HeldAsset.Width],
    height: Option[HeldAsset.Height],
    kind: Option[HeldAsset.Kind] = None,
    caption: Option[HeldAsset.Caption] = None,
    license: Option[HeldAsset.License] = None,
    licenseUrl: Option[HeldAsset.LicenseUrl] = None,
    author: Option[HeldAsset.Author] = None,
    credit: Option[HeldAsset.Credit] = None,
    provider: Option[AssetService.Provider] = None,
    providerKey: Option[AssetService.ProviderKey] = None,
    sourceUrl: Option[HeldAsset.SourceUrl] = None,
    originalUrl: Option[HeldAsset.OriginalUrl] = None,
    retrievedAt: Option[Instant] = None,
    thumb: Option[ProviderAsset.Thumb] = None,
    thumbMime: Option[ProviderAsset.ThumbMime] = None,
    thumbWidth: Option[ProviderAsset.ThumbWidth] = None,
    thumbHeight: Option[ProviderAsset.ThumbHeight] = None,
    point: Option[(HeldAsset.Latitude, HeldAsset.Longitude)] = None,
    period: Option[(Option[com.histmersion.domain.DeepTime.SignedYear], Option[com.histmersion.domain.DeepTime.SignedYear])] = None
)

object HeldAsset:
  type Width = Width.T
  object Width extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Height = Height.T
  object Height extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Kind = Kind.T
  object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Caption = Caption.T
  object Caption extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
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
  type Latitude = Latitude.T
  object Latitude extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Longitude = Longitude.T
  object Longitude extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
