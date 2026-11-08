package com.histmersion.api.exchange
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given

final case class IssueCondition(issue: IssueCondition.Issue, when: MergeDecision.Kind) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema

object IssueCondition:
  export com.histmersion.api.exchange.MergeIssue.{Id as Issue}
