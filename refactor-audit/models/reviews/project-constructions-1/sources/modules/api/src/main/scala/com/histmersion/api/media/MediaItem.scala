package com.histmersion.api.media
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class MediaItem(
    uid: MediaItem.Uid,
    name: com.histmersion.api.media.AssetInfo.Name,
    mime: com.histmersion.api.media.AssetInfo.Mime,
    bytes: com.histmersion.api.ByteCount,
    citekey: Option[com.histmersion.api.library.Citekey] = None,
    provider: Option[MediaItem.Provider] = None,
    providerKey: Option[MediaItem.ProviderKey] = None,
    width: Option[com.histmersion.api.media.AssetInfo.Width] = None,
    height: Option[com.histmersion.api.media.AssetInfo.Height] = None,
    kind: Option[MediaItem.Kind] = None,
    caption: Option[MediaItem.Caption] = None,
    license: Option[MediaItem.License] = None,
    licenseUrl: Option[MediaItem.LicenseUrl] = None,
    author: Option[MediaItem.Author] = None,
    credit: Option[MediaItem.Credit] = None,
    sourceUrl: Option[MediaItem.SourceUrl] = None,
    originalUrl: Option[MediaItem.OriginalUrl] = None,
    retrievedAt: Option[MediaItem.RetrievedAt] = None,
    thumbUrl: Option[MediaItem.ThumbUrl] = None,
    fileUrl: MediaItem.FileUrl = _root_.com.histmersion.api.media.MediaItem.FileUrl(""),
    targets: List[MediaTarget] = Nil
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object MediaItem:
  type Uid = Uid.T
  object Uid extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Provider = Provider.T
  object Provider extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ProviderKey = ProviderKey.T
  object ProviderKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
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
  type RetrievedAt = RetrievedAt.T
  object RetrievedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ThumbUrl = ThumbUrl.T
  object ThumbUrl extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FileUrl = FileUrl.T
  object FileUrl extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
