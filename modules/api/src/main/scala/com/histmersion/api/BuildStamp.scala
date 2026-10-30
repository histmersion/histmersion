package com.histmersion.api

import scala.annotation.tailrec
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** The running server's build. `gitCommit`/`commitDate` are stamped at compile time from the checkout it was built from
  * (`None` when it was built outside a git checkout); `dirty` = built with uncommitted changes, so the commit alone
  * does not say what is running. `licence` is the SPDX id the program is distributed under and `sourceUrl` where its
  * source can be had (`None`: an older server).
  */
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
