package com.histmersion.api.media
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class MediaThumb(
    uid: String,
    role: String,
    url: String,
    credit: String,
    license: Option[String] = None,
    licenseUrl: Option[String] = None,
    sourceUrl: Option[String] = None,
    width: Option[Int] = None,
    height: Option[Int] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema:

  /** The picture's line on an event's or a place's page: its role, credit, licence, source and address. */
  def markdownLine: String =
    val lic = license.fold("no licence")(l => licenseUrl.fold(s"`$l`")(u => s"[`$l`]($u)"))
    val from = sourceUrl.fold("")(u => s" · [source]($u)")
    s"- _${role}_ — $credit · $lic$from · `$url`"

object MediaThumb:


  def thumbPath(uid: String): String = s"/media/$uid/thumb"
  def filePath(uid: String): String = s"/media/$uid/file"
