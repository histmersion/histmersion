package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

final case class BackupInfo(file: String, bytes: Long, createdAt: String)
    derives KindlingsCodecAsObject,
      KindlingsSchema
object BackupInfo:
  given Schema[BackupInfo] = summon[KindlingsSchema[BackupInfo]].schema
