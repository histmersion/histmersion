package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

final case class GazetteerExtract(
    name: String,
    file: String,
    sha256: String,
    bytes: Long,
    features: Long,
    version: String,
    title: String,
    licence: String,
    attribution: Option[String] = None,
    url: Option[String] = None,
    built: Option[String] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema
object GazetteerExtract:
  given Schema[GazetteerExtract] = summon[KindlingsSchema[GazetteerExtract]].schema
