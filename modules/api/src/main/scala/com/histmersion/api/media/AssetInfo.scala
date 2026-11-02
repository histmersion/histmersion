package com.histmersion.api.media
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class AssetInfo(
    name: String,
    mime: String,
    bytes: Int,
    sha256: String,
    width: Option[Int] = None,
    height: Option[Int] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema
object AssetInfo:
  given Schema[AssetInfo] = summon[KindlingsSchema[AssetInfo]].schema
