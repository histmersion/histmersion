package com.histmersion.api.exchange
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

import com.histmersion.util.data.exchange.DecisionKind
import com.histmersion.api.given
import com.histmersion.util.data.exchange.Issue

final case class MergeIssue(
    id: String,
    kind: Issue.Kind,
    severity: Issue.Severity,
    entity: com.histmersion.util.data.exchange.Bundle.Kind,
    title: String,
    left: Option[IssueSide] = None,
    right: Option[IssueSide] = None,
    why: List[String] = Nil,
    options: List[DecisionKind] = Nil,
    suggested: Option[MergeDecision] = None,
    condition: Option[IssueCondition] = None,
    decision: Option[MergeDecision] = None,
    decidedBy: Option[Issue.DecidedBy] = None,
    detail: Option[IssueDetail] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema:
  def open: Boolean = severity == Issue.Severity.Blocking && condition.isEmpty && decision.isEmpty
object MergeIssue:
  given Schema[MergeIssue] = summon[KindlingsSchema[MergeIssue]].schema
