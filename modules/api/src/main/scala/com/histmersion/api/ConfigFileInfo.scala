package com.histmersion.api

import scala.annotation.tailrec
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

final case class ConfigFileInfo(
    path: Option[String] = None,
    loaded: Boolean = false,
    unknownKeys: List[String] = Nil
) derives KindlingsCodecAsObject,
      KindlingsSchema
object ConfigFileInfo:
  given Schema[ConfigFileInfo] = summon[KindlingsSchema[ConfigFileInfo]].schema

  def describe(c: ConfigFileInfo): String =
    c.path match
      case None    => "none (no location: HOME is unset)"
      case Some(p) =>
        val state = if c.loaded then "read" else "not found: defaults and environment only"
        val keys = if c.unknownKeys.isEmpty then ""
        else s"; ignored unknown ${Plural.noun(c.unknownKeys.size, "key")}: ${c.unknownKeys.mkString(", ")}"
        s"$p ($state$keys)"
