package com.histmersion.api.exchange
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

import com.histmersion.util.data.exchange.DecisionKind
import com.histmersion.api.given

final case class IssueCondition(issue: String, when: DecisionKind) derives KindlingsCodecAsObject, KindlingsSchema
object IssueCondition:
  given Schema[IssueCondition] = summon[KindlingsSchema[IssueCondition]].schema
