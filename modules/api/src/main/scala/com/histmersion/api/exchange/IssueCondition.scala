package com.histmersion
package api.exchange
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given

final case class IssueCondition(issue: IssueCondition.Issue, when: MergeDecision.Kind)
    derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object IssueCondition:
  export MergeIssue.Id as Issue
