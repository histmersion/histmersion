package com.histmersion
package domain.media.assets

import java.time.Instant
import com.histmersion.domain.library.Citekey
import com.histmersion.domain.DeepTime
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** A picture as the in-memory store holds it: a row of `assets` — a source's figure (its `citekey`) or a provider's
  * picture (its `provider` and key) — with its point and inclusive span when it has them.
  */
final case class HeldAsset(
    uid: AssetService.AssetUid,
    citekey: Option[Citekey],
    name: AssetService.AssetName,
    mime: AssetService.Mime,
    bytes: AssetService.Bytes,
    sha256: Sha256,
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
    period: Option[(Option[DeepTime.SignedYear], Option[DeepTime.SignedYear])] = None
)

object HeldAsset:
  type Width = Width.T
  object Width extends RefinedType[Int, Pure]
  type Height = Height.T
  object Height extends RefinedType[Int, Pure]
  type Kind = Kind.T
  object Kind extends RefinedType[String, Pure]
  type Caption = Caption.T
  object Caption extends RefinedType[String, Pure]
  type License = License.T
  object License extends RefinedType[String, Pure]
  type LicenseUrl = LicenseUrl.T
  object LicenseUrl extends RefinedType[String, Pure]
  type Author = Author.T
  object Author extends RefinedType[String, Pure]
  type Credit = Credit.T
  object Credit extends RefinedType[String, Pure]
  type SourceUrl = SourceUrl.T
  object SourceUrl extends RefinedType[String, Pure]
  type OriginalUrl = OriginalUrl.T
  object OriginalUrl extends RefinedType[String, Pure]
  type Latitude = Latitude.T
  object Latitude extends RefinedType[Double, Pure]
  type Longitude = Longitude.T
  object Longitude extends RefinedType[Double, Pure]
