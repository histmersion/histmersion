package com.histmersion
package api.media
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.exchange.Bundle
import com.histmersion.api.ByteCount
import com.histmersion.api.library.Citekey
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
) derives JsonCodec,
      KindlingsSchema,
      com.histmersion.api.json.Schema

object MediaItem:
  type Uid = Uid.T
  object Uid extends Newtype[String, Pure]
  type Provider = Provider.T
  object Provider extends Newtype[String, Pure]
  type ProviderKey = ProviderKey.T
  object ProviderKey extends Newtype[String, Pure]
  type Kind = Kind.T
  object Kind extends Newtype[String, Pure]
  type Caption = Caption.T
  object Caption extends Newtype[String, Pure]
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
  type RetrievedAt = RetrievedAt.T
  object RetrievedAt extends Newtype[String, Pure]
  type ThumbUrl = ThumbUrl.T
  object ThumbUrl extends Newtype[String, Pure]
  type FileUrl = FileUrl.T
  object FileUrl extends Newtype[String, Pure]
