package com.histmersion.domain.media.assets

import java.time.Instant

/** A picture as the in-memory store holds it: a row of `assets` — a source's figure (its `citekey`) or a provider's
  * picture (its `provider` and key) — with its point and inclusive span when it has them.
  */
final case class HeldAsset(
    uid: HeldAsset.Uid,
    citekey: Option[com.histmersion.domain.library.Citekey],
    name: HeldAsset.Name,
    mime: HeldAsset.Mime,
    bytes: Array[HeldAsset.Bytes],
    sha256: com.histmersion.Sha256,
    width: Option[HeldAsset.Width],
    height: Option[HeldAsset.Height],
    kind: Option[HeldAsset.Kind] = None,
    caption: Option[HeldAsset.Caption] = None,
    license: Option[HeldAsset.License] = None,
    licenseUrl: Option[HeldAsset.LicenseUrl] = None,
    author: Option[HeldAsset.Author] = None,
    credit: Option[HeldAsset.Credit] = None,
    provider: Option[HeldAsset.Provider] = None,
    providerKey: Option[HeldAsset.ProviderKey] = None,
    sourceUrl: Option[HeldAsset.SourceUrl] = None,
    originalUrl: Option[HeldAsset.OriginalUrl] = None,
    retrievedAt: Option[Instant] = None,
    thumb: Option[Array[HeldAsset.Thumb]] = None,
    thumbMime: Option[HeldAsset.ThumbMime] = None,
    thumbWidth: Option[HeldAsset.ThumbWidth] = None,
    thumbHeight: Option[HeldAsset.ThumbHeight] = None,
    point: Option[(HeldAsset.PointPart1, HeldAsset.PointPart2)] = None,
    period: Option[(Option[HeldAsset.PeriodPart1], Option[HeldAsset.PeriodPart2])] = None
)

object HeldAsset:
  type Uid = Uid.T
  object Uid extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Name = Name.T
  object Name extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Mime = Mime.T
  object Mime extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Bytes = Bytes.T
  object Bytes extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Byte, _root_.io.github.iltotore.iron.constraint.any.Pure]
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
  type Provider = Provider.T
  object Provider extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ProviderKey = ProviderKey.T
  object ProviderKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SourceUrl = SourceUrl.T
  object SourceUrl extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OriginalUrl = OriginalUrl.T
  object OriginalUrl extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Thumb = Thumb.T
  object Thumb extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Byte, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ThumbMime = ThumbMime.T
  object ThumbMime extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ThumbWidth = ThumbWidth.T
  object ThumbWidth extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ThumbHeight = ThumbHeight.T
  object ThumbHeight extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PointPart1 = PointPart1.T
  object PointPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PointPart2 = PointPart2.T
  object PointPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PeriodPart1 = PeriodPart1.T
  object PeriodPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PeriodPart2 = PeriodPart2.T
  object PeriodPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
