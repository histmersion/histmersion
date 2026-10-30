package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

import com.histmersion.values.{BundleKind, DecidedBy, DecisionKind, IssueKind, IssueSeverity}

final case class MergeIssue(
    /** `<letter>-<8 hex>`: the letter names the entity (S source, O OCR body, F finding, A article, …), the hex is a
      * hash of the issue's kind and the uids and revs it is about.
      */
    id: String,
    kind: IssueKind,
    severity: IssueSeverity,
    entity: BundleKind,
    title: String,
    /** This collection's record, when it has one. */
    left: Option[IssueSide] = None,
    /** The bundle's record, when it has one. */
    right: Option[IssueSide] = None,
    /** Why: the evidence, e.g. the score's parts. */
    why: List[String] = Nil,
    /** The decisions that are allowed. */
    options: List[DecisionKind] = Nil,
    /** A suggested (or, for a [[IssueSeverity.Default]], the default) decision. */
    suggested: Option[MergeDecision] = None,
    condition: Option[IssueCondition] = None,
    /** The decision in force, and who made it. */
    decision: Option[MergeDecision] = None,
    decidedBy: Option[DecidedBy] = None,
    /** The two records side by side, for the review screen. */
    detail: Option[IssueDetail] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema:
  /** Open and holding the merge up: blocking, unconditional, undecided. */
  def open: Boolean = severity == IssueSeverity.Blocking && condition.isEmpty && decision.isEmpty
object MergeIssue:
  given Schema[MergeIssue] = summon[KindlingsSchema[MergeIssue]].schema
