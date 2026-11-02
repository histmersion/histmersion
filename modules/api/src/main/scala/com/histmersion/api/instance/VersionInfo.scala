package com.histmersion.api.instance
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class VersionInfo(name: String, version: String) derives KindlingsCodecAsObject, KindlingsSchema
object VersionInfo:
  given Schema[VersionInfo] = summon[KindlingsSchema[VersionInfo]].schema
