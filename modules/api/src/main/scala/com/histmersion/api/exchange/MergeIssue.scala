package com.histmersion.api.exchange
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

import com.histmersion.values.{BundleKind, DecidedBy, IssueKind, IssueSeverity}
import com.histmersion.util.data.exchange.DecisionKind
import com.histmersion.api.given

final case class MergeIssue(
    id: String,
    kind: IssueKind,
    severity: IssueSeverity,
    entity: BundleKind,
    title: String,
    left: Option[IssueSide] = None,
    right: Option[IssueSide] = None,
    why: List[String] = Nil,
    options: List[DecisionKind] = Nil,
    suggested: Option[MergeDecision] = None,
    condition: Option[IssueCondition] = None,
    decision: Option[MergeDecision] = None,
    decidedBy: Option[DecidedBy] = None,
    detail: Option[IssueDetail] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema:
  def open: Boolean = severity == IssueSeverity.Blocking && condition.isEmpty && decision.isEmpty
object MergeIssue:
  given Schema[MergeIssue] = summon[KindlingsSchema[MergeIssue]].schema
