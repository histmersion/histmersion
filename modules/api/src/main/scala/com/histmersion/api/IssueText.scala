package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** The texts of an article question: the common version (when the histories share one), both sides', and the merge's
  * regions — what the review screen's diff3 view and its editor start from.
  */
final case class IssueText(base: Option[String], left: String, right: String, hunks: List[IssueHunk] = Nil)
    derives KindlingsCodecAsObject,
      KindlingsSchema
object IssueText:
  given Schema[IssueText] = summon[KindlingsSchema[IssueText]].schema
