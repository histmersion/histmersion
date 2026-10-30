package com.histmersion.api

import scala.annotation.tailrec
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** `licence` and `sourceUrl` are `None` from an older server. */
final case class BuildStamp(
    version: String,
    gitCommit: Option[String] = None,
    commitDate: Option[String] = None,
    dirty: Boolean = false,
    licence: Option[String] = None,
    sourceUrl: Option[String] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema
object BuildStamp:
  given Schema[BuildStamp] = summon[KindlingsSchema[BuildStamp]].schema
