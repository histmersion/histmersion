package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** The converter sidecar's `/convert` response. */
final case class ConvertedImage(
    name: String,
    mime: String,
    base64: String,
    width: Option[Int] = None,
    height: Option[Int] = None,
    sha256: Option[String] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema
object ConvertedImage:
  given Schema[ConvertedImage] = summon[KindlingsSchema[ConvertedImage]].schema
