package com.histmersion.api.backup
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class BackupInfo(file: String, bytes: Long, createdAt: String)
    derives KindlingsCodecAsObject,
      KindlingsSchema
object BackupInfo:
  given Schema[BackupInfo] = summon[KindlingsSchema[BackupInfo]].schema
