package com.histmersion.api

import scala.annotation.tailrec
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** The user config file the server read at startup (`reference.conf` < this file < the environment). */
final case class ConfigFileInfo(
    /** Where the file is looked for — `$HISTMERSION_CONFIG`, else the per-user default; `None` when no location could
      * be worked out (HOME unset).
      */
    path: Option[String] = None,
    /** It was there and was read. */
    loaded: Boolean = false,
    /** Keys of the file that are no setting (a typo), ignored. */
    unknownKeys: List[String] = Nil
) derives KindlingsCodecAsObject,
      KindlingsSchema
object ConfigFileInfo:
  given Schema[ConfigFileInfo] = summon[KindlingsSchema[ConfigFileInfo]].schema

  /** `path (read)` / `path (not found: defaults and environment only)` / `none`. Pure. */
  def describe(c: ConfigFileInfo): String =
    c.path match
      case None    => "none (no location: HOME is unset)"
      case Some(p) =>
        val state = if c.loaded then "read" else "not found: defaults and environment only"
        val keys = if c.unknownKeys.isEmpty then ""
        else s"; ignored unknown ${Plural.noun(c.unknownKeys.size, "key")}: ${c.unknownKeys.mkString(", ")}"
        s"$p ($state$keys)"
