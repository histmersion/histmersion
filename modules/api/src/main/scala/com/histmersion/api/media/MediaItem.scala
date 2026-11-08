package com.histmersion
package api.media
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.exchange.Bundle
import com.histmersion.api.ByteCount
import com.histmersion.api.library.Citekey
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

final case class MediaItem(
    uid: MediaItem.Uid,
    name: Bundle.Asset.FileName,
    mime: Bundle.Asset.MimeText,
    bytes: ByteCount,
    citekey: Option[Citekey] = None,
    provider: Option[MediaItem.Provider] = None,
    providerKey: Option[MediaItem.ProviderKey] = None,
    width: Option[ImageSize.Width] = None,
    height: Option[ImageSize.Height] = None,
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
    fileUrl: MediaItem.FileUrl = MediaItem.FileUrl(""),
    targets: List[MediaTarget] = Nil
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object MediaItem:
  type Uid = Uid.T
  object Uid extends RefinedType[String, Pure]
  type Provider = Provider.T
  object Provider extends RefinedType[String, Pure]
  type ProviderKey = ProviderKey.T
  object ProviderKey extends RefinedType[String, Pure]
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
  type RetrievedAt = RetrievedAt.T
  object RetrievedAt extends RefinedType[String, Pure]
  type ThumbUrl = ThumbUrl.T
  object ThumbUrl extends RefinedType[String, Pure]
  type FileUrl = FileUrl.T
  object FileUrl extends RefinedType[String, Pure]
