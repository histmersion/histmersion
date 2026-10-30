package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** The archives in `backups.dir` and how many `backups.keep` leaves after each dump (0 = every one). */
final case class BackupsUsage(count: Int, bytes: Long, newest: Option[String], keep: Int)
    derives KindlingsCodecAsObject,
      KindlingsSchema
object BackupsUsage:
  given Schema[BackupsUsage] = summon[KindlingsSchema[BackupsUsage]].schema
