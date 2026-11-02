package com.histmersion.api.media
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class AssetInput(
    name: String,
    mime: String,
    base64: String,
    width: Option[Int] = None,
    height: Option[Int] = None,
    sha256: Option[String] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema
object AssetInput:
  given Schema[AssetInput] = summon[KindlingsSchema[AssetInput]].schema
