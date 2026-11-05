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
    width: Option[com.histmersion.api.media.AssetInfo.Width] = None,
    height: Option[com.histmersion.api.media.AssetInfo.Height] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema:

  /** The picture's line on an event's or a place's page: its role, credit, licence, source and address. */
  def markdownLine: com.histmersion.api.media.MediaThumb.MarkdownLineResult =
    val lic = license.fold("no licence")(l => licenseUrl.fold(s"`$l`")(u => s"[`$l`]($u)"))
    val from = sourceUrl.fold("")(u => s" · [source]($u)")
    s"- _${role}_ — $credit · $lic$from · `$url`"

object MediaThumb:


  type MarkdownLineResult = MarkdownLineResult.T
  object MarkdownLineResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ThumbPathResult = ThumbPathResult.T
  object ThumbPathResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ThumbPathUid = ThumbPathUid.T
  object ThumbPathUid extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FilePathResult = FilePathResult.T
  object FilePathResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FilePathUid = FilePathUid.T
  object FilePathUid extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type Role = Role.T
  object Role extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Url = Url.T
  object Url extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def thumbPath(uid: com.histmersion.api.media.MediaThumb.ThumbPathUid): com.histmersion.api.media.MediaThumb.ThumbPathResult = s"/media/$uid/thumb"
  def filePath(uid: com.histmersion.api.media.MediaThumb.FilePathUid): com.histmersion.api.media.MediaThumb.FilePathResult = s"/media/$uid/file"
