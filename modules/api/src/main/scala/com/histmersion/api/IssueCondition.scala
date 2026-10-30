package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

import com.histmersion.values.DecisionKind

/** An issue that exists only under one alternative of another, still open, issue: `if S-3c1e7a2b = map`. It is listed
  * now so that no new issue appears after the other one is decided (§6.1).
  */
final case class IssueCondition(issue: String, when: DecisionKind) derives KindlingsCodecAsObject, KindlingsSchema
object IssueCondition:
  given Schema[IssueCondition] = summon[KindlingsSchema[IssueCondition]].schema
