package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

final case class VersionInfo(name: String, version: String) derives KindlingsCodecAsObject, KindlingsSchema
object VersionInfo:
  given Schema[VersionInfo] = summon[KindlingsSchema[VersionInfo]].schema
