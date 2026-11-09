package com.histmersion
package api.media
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.exchange.Bundle

final case class MediaThumb(
    uid: MediaItem.Uid,
    role: MediaThumb.Role,
    url: MediaThumb.Url,
    credit: MediaItem.Credit,
    license: Option[MediaItem.License] = None,
    licenseUrl: Option[MediaItem.LicenseUrl] = None,
    sourceUrl: Option[MediaItem.SourceUrl] = None,
    width: Option[ImageSize.Width] = None,
    height: Option[ImageSize.Height] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema,
      com.histmersion.api.json.Schema:

  /** The picture's line on an event's or a place's page: its role, credit, licence, source and address. */
  def markdownLine: MediaThumb.MarkdownLine =
    val lic = license.fold("no licence")(l => licenseUrl.fold(s"`$l`")(u => s"[`$l`]($u)"))
    val from = sourceUrl.fold("")(u => s" · [source]($u)")
    MediaThumb.MarkdownLine(s"- _${role}_ — $credit · $lic$from · `$url`")

object MediaThumb:
  type FilePath = FilePath.T
  object FilePath extends Newtype[String, Pure]
  type MarkdownLine = MarkdownLine.T
  object MarkdownLine extends Newtype[String, Pure]
  type ThumbPath = ThumbPath.T
  object ThumbPath extends Newtype[String, Pure]

  type Role = Role.T
  object Role extends Newtype[String, Pure]
  type Url = Url.T
  object Url extends Newtype[String, Pure]

  def thumbPath(uid: Bundle.Asset.Uid): ThumbPath = ThumbPath(s"/media/$uid/thumb")
  def filePath(uid: Bundle.Asset.Uid): FilePath = FilePath(s"/media/$uid/file")
