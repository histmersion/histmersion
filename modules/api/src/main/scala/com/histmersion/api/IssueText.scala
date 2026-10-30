package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

final case class IssueText(base: Option[String], left: String, right: String, hunks: List[IssueHunk] = Nil)
    derives KindlingsCodecAsObject,
      KindlingsSchema
object IssueText:
  given Schema[IssueText] = summon[KindlingsSchema[IssueText]].schema
