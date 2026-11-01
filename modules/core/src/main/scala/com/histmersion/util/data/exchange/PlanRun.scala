package com.histmersion.util.data.exchange

import com.histmersion.api.{BundleSource, IssueCondition, IssueDetail, MergeDecision, MergeIssue}
import com.histmersion.domain.MergePlanner
import com.histmersion.files.BundleEntities
import com.histmersion.values.{BundleKind, DecisionKind, IssueKind}

import java.util.UUID

/** One plan's run of a kind's rules ([[PlanRules]]): what the planner asks of the kind at each stage, in the planner's
  * order. A run may keep what it learns between the stages; every member defaults to "nothing of that kind".
  */
trait PlanRun:

  /** The bundle's lines in this collection's terms, with what points at this kind's records by id pointed at the ones
    * here, before anything is compared; `mapped` is every bundle uid a redirect or the ledger maps onto another here.
    */
  def localized(es: BundleEntities, mapped: Map[UUID, UUID]): BundleEntities = es

  /** What the plan says first: how the bundle's records of this kind were taken. */
  def notices: List[String] = Nil

  /** The bundle's source that a line of this kind belongs to (its fate decides whether the line is held). */
  def ownerSource(foreign: UUID, kind: BundleKind): Option[UUID] = None

  /** The bodies of the sources here (by sha256) the plan reads for this kind: of the pairs whose bodies differ. */
  def bodiesWanted(pairs: List[(BundleSource, BundleSource)]): Set[String] = Set.empty

  /** What a pair of sources that are (or may be) one owns of this kind: its questions under each alternative of the
    * body question (`bodyIssue`), `cond` the pair's own condition. `right` is the bundle's source, `left` the one here.
    */
  def pair(
      right: BundleSource,
      left: BundleSource,
      cond: Option[IssueCondition],
      bodyIssue: Option[String],
      questions: PairQuestions
  ): Unit = ()

  /** The two records of a row question side by side, for a kind whose screen shows more than labels. */
  def rowDetail(kind: BundleKind, u: UUID, localized: BundleEntities): Option[IssueDetail] = None

  /** A row changed on both sides that the kind merges by itself; `localized` is the bundle's lines in this
    * collection's terms.
    */
  def conflict(row: MergePlanner.Row, localized: BundleEntities): Option[PlanMerge] = None

  /** The kind's labels a different record has here. */
  def labelsTaken(questions: PlanQuestions): Unit = ()

  /** The kind's records added on both sides that are one, or a question. */
  def addedOnBoth(questions: PlanQuestions): Unit = ()

  /** A decision's argument checked for an issue of this kind; `None` for an issue the kind does not check. */
  def argument(issue: MergeIssue, decision: MergeDecision): Option[Either[String, Unit]] = None

  /** An issue of a pair: its decision is carried out with the pair's fold. */
  def pairIssue(kind: IssueKind): Boolean = false

  /** What a pair that folds does with the kind's lines; `alt` is the body question's decision, when there is one. */
  def foldPair(right: BundleSource, left: BundleSource, alt: Option[DecisionKind], folds: PlanFolds): Unit = ()

  /** A decided issue of this kind carried out. */
  def decided(issue: MergeIssue, folds: PlanFolds): Unit = ()

  /** The final lines of this kind: `es0` is the bundle's lines in this collection's uids, folds made. */
  def lines(es: BundleEntities, es0: BundleEntities): BundleEntities = es

  /** What cannot be imported whatever is decided: the written lines (`writes`) that need what is not coming. */
  def problems(finalEs: BundleEntities, live: UUID => Boolean, writes: UUID => Boolean): List[String] = Nil

  /** Bundle records left out for good: recorded as deleted here. */
  def dropped: Set[UUID] = Set.empty

  /** This collection's attestations moved because the merge keeps the bundle's body. */
  def fixes: List[MergePlanner.AttestationFix] = Nil

  /** The tombstones of the records left out: uid, kind, origin. */
  def tombstones: List[(UUID, BundleKind, UUID)] = Nil
