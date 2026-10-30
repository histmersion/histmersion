package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

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

  def thumbPath(uid: String): String = s"/media/$uid/thumb"
  def filePath(uid: String): String = s"/media/$uid/file"
