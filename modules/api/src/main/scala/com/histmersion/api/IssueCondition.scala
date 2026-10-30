package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

import com.histmersion.values.DecisionKind

final case class IssueCondition(issue: String, when: DecisionKind) derives KindlingsCodecAsObject, KindlingsSchema
object IssueCondition:
  given Schema[IssueCondition] = summon[KindlingsSchema[IssueCondition]].schema
