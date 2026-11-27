package com.histmersion
package api.media
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.exchange.Bundle
import com.histmersion.lint.{tolerated, LintRule}

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
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema:

  /** The picture's line on an event's or a place's page: its role, credit, licence, source and address. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def markdownLine: MediaThumb.MarkdownLine =
    val lic = license.fold("no licence")(l => licenseUrl.fold(s"`$l`")(u => s"[`$l`]($u)"))
    val from = sourceUrl.fold("")(u => s" · [source]($u)")
    MediaThumb.MarkdownLine(s"- _${role}_ — $credit · $lic$from · `$url`")

object MediaThumb:
  type FilePath = FilePath.T
  object FilePath extends Newtype[String, Pure]
  type MarkdownLine = MarkdownLine.T
  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  object MarkdownLine extends Newtype[String, Pure]
  type ThumbPath = ThumbPath.T
  object ThumbPath extends Newtype[String, Pure]

  type Role = Role.T
  object Role extends Newtype[String, Pure]
  type Url = Url.T
  object Url extends Newtype[String, Pure]

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def thumbPath(uid: Bundle.Asset.Uid): ThumbPath = ThumbPath(s"/media/$uid/thumb")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def filePath(uid: Bundle.Asset.Uid): FilePath = FilePath(s"/media/$uid/file")
