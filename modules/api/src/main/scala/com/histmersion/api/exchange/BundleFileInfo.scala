package com.histmersion.api.exchange
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class BundleFileInfo(path: String, lines: Int, bytes: Long, sha256: String)
    derives KindlingsCodecAsObject,
      KindlingsSchema
object BundleFileInfo:
  given Schema[BundleFileInfo] = summon[KindlingsSchema[BundleFileInfo]].schema
