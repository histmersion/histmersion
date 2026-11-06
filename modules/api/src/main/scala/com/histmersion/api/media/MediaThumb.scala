package com.histmersion.api.media
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class MediaThumb(
    uid: com.histmersion.api.media.MediaItem.Uid,
    role: MediaThumb.Role,
    url: MediaThumb.Url,
    credit: com.histmersion.api.media.MediaItem.Credit,
    license: Option[com.histmersion.api.media.MediaItem.License] = None,
    licenseUrl: Option[com.histmersion.api.media.MediaItem.LicenseUrl] = None,
    sourceUrl: Option[com.histmersion.api.media.MediaItem.SourceUrl] = None,
    width: Option[com.histmersion.api.media.ImageSize.Width] = None,
    height: Option[com.histmersion.api.media.ImageSize.Height] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema:

  /** The picture's line on an event's or a place's page: its role, credit, licence, source and address. */
  def markdownLine: com.histmersion.api.media.MediaThumb.MarkdownLine =
    val lic = license.fold("no licence")(l => licenseUrl.fold(s"`$l`")(u => s"[`$l`]($u)"))
    val from = sourceUrl.fold("")(u => s" · [source]($u)")
    _root_.com.histmersion.api.media.MediaThumb.MarkdownLine(s"- _${role}_ — $credit · $lic$from · `$url`")

object MediaThumb:
  type FilePath = FilePath.T
  object FilePath extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MarkdownLine = MarkdownLine.T
  object MarkdownLine extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ThumbPath = ThumbPath.T
  object ThumbPath extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  type MarkdownLineResult = com.histmersion.api.media.MediaThumb.MarkdownLine
  val MarkdownLineResult: _root_.com.histmersion.api.media.MediaThumb.MarkdownLine.type = _root_.com.histmersion.api.media.MediaThumb.MarkdownLine

  type Role = Role.T
  object Role extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Url = Url.T
  object Url extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def thumbPath(uid: com.histmersion.api.exchange.Bundle.Asset.Uid): com.histmersion.api.media.MediaThumb.ThumbPath = _root_.com.histmersion.api.media.MediaThumb.ThumbPath(s"/media/$uid/thumb")
  def filePath(uid: com.histmersion.api.exchange.Bundle.Asset.Uid): com.histmersion.api.media.MediaThumb.FilePath = _root_.com.histmersion.api.media.MediaThumb.FilePath(s"/media/$uid/file")
