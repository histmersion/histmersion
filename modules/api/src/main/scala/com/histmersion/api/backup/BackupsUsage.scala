package com.histmersion.api.backup
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class BackupsUsage(count: Int, bytes: Long, newest: Option[String], keep: Int)
    derives KindlingsCodecAsObject,
      KindlingsSchema
object BackupsUsage:
  given Schema[BackupsUsage] = summon[KindlingsSchema[BackupsUsage]].schema
