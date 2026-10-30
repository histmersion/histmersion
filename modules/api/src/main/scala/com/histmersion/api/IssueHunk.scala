package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

final case class IssueHunk(
    conflict: Boolean,
    base: List[String] = Nil,
    left: List[String] = Nil,
    right: List[String] = Nil
) derives KindlingsCodecAsObject,
      KindlingsSchema
object IssueHunk:
  given Schema[IssueHunk] = summon[KindlingsSchema[IssueHunk]].schema
