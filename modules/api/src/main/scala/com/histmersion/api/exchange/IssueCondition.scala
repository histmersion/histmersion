package com.histmersion.api.exchange
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

import com.histmersion.api.given

final case class IssueCondition(issue: IssueCondition.Issue, when: MergeDecision.Kind) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema

object IssueCondition:
  type Issue = com.histmersion.api.exchange.MergeIssue.Id
  val Issue: _root_.com.histmersion.api.exchange.MergeIssue.Id.type = _root_.com.histmersion.api.exchange.MergeIssue.Id
