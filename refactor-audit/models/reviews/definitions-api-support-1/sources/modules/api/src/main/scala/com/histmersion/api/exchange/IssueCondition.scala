package com.histmersion.api.exchange
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

import com.histmersion.api.given

final case class IssueCondition(issue: IssueCondition.Issue, when: MergeDecision.Kind) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema

object IssueCondition:
  type Issue = Issue.T
  object Issue extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
