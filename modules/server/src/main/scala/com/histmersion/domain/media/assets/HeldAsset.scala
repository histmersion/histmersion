package com.histmersion
package domain.media.assets

import java.time.Instant
import com.histmersion.domain.library.Citekey
import com.histmersion.domain.DeepTime
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}

/** A picture as the in-memory store holds it: a row of `assets` — a source's figure (its `citekey`) or a provider's
  * picture (its `provider` and key) — with its point and inclusive span when it has them.
  */
@tolerated(LintRule.Direction, "pending G12 (a domain names only lower places)")
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
  object Width extends Newtype[Int, Pure]
  type Height = Height.T
  object Height extends Newtype[Int, Pure]
  type Kind = Kind.T
  object Kind extends Newtype[String, Pure]
  type Caption = Caption.T
  object Caption extends Newtype[String, Pure]
  type License = License.T
  object License extends Newtype[String, Pure]
  type LicenseUrl = LicenseUrl.T
  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  object LicenseUrl extends Newtype[String, Pure]
  type Author = Author.T
  object Author extends Newtype[String, Pure]
  type Credit = Credit.T
  object Credit extends Newtype[String, Pure]
  type SourceUrl = SourceUrl.T
  object SourceUrl extends Newtype[String, Pure]
  type OriginalUrl = OriginalUrl.T
  object OriginalUrl extends Newtype[String, Pure]
  type Latitude = Latitude.T
  object Latitude extends Newtype[Double, Pure]
  type Longitude = Longitude.T
  object Longitude extends Newtype[Double, Pure]
