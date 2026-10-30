package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** The thumbnail a place or event card shows, with what its licence requires beside it: the credit line, the licence
  * (SPDX id or `public-domain`) and where to read it, and the page the picture comes from. `url` is this server's own
  * (`/media/{uid}/thumb`) — a hover never reaches outside.
  */
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
      KindlingsSchema

object MediaThumb:
  given Schema[MediaThumb] = summon[KindlingsSchema[MediaThumb]].schema

  /** The local URLs of a stored picture's thumbnail and rendition. */
  def thumbPath(uid: String): String = s"/media/$uid/thumb"
  def filePath(uid: String): String = s"/media/$uid/file"
