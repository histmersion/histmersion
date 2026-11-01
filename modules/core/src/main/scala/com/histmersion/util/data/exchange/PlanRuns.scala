package com.histmersion.util.data.exchange

import com.histmersion.api.{BundleSource, IssueCondition, IssueDetail, MergeDecision, MergeIssue}
import com.histmersion.domain.MergePlanner
import com.histmersion.files.BundleEntities
import com.histmersion.values.{BundleKind, DecisionKind, IssueKind}

import scala.annotation.tailrec
import java.util.UUID

/** Several runs as one, each stage in the order the runs are given: what one kind's run answers first is the answer. */
final class PlanRuns(runs: List[PlanRun]) extends PlanRun:

  @tailrec private def through(rest: List[PlanRun], es: BundleEntities)(
      f: (PlanRun, BundleEntities) => BundleEntities
  ): BundleEntities =
    rest match
      case r :: tail => through(tail, f(r, es))(f)
      case Nil       => es

  override def localized(es: BundleEntities, mapped: Map[UUID, UUID]): BundleEntities =
    through(runs, es)(_.localized(_, mapped))

  override def notices: List[String] = runs.flatMap(_.notices)

  override def ownerSource(foreign: UUID, kind: BundleKind): Option[UUID] =
    runs.iterator.flatMap(_.ownerSource(foreign, kind)).nextOption()

  override def bodiesWanted(pairs: List[(BundleSource, BundleSource)]): Set[String] =
    runs.flatMap(_.bodiesWanted(pairs)).toSet

  override def pair(
      right: BundleSource,
      left: BundleSource,
      cond: Option[IssueCondition],
      bodyIssue: Option[String],
      questions: PairQuestions
  ): Unit = runs.foreach(_.pair(right, left, cond, bodyIssue, questions))

  override def rowDetail(kind: BundleKind, u: UUID, localized: BundleEntities): Option[IssueDetail] =
    runs.iterator.flatMap(_.rowDetail(kind, u, localized)).nextOption()

  override def conflict(row: MergePlanner.Row, localized: BundleEntities): Option[PlanMerge] =
    runs.iterator.flatMap(_.conflict(row, localized)).nextOption()

  override def labelsTaken(questions: PlanQuestions): Unit = runs.foreach(_.labelsTaken(questions))

  override def addedOnBoth(questions: PlanQuestions): Unit = runs.foreach(_.addedOnBoth(questions))

  override def argument(issue: MergeIssue, decision: MergeDecision): Option[Either[String, Unit]] =
    runs.iterator.flatMap(_.argument(issue, decision)).nextOption()

  override def pairIssue(kind: IssueKind): Boolean = runs.exists(_.pairIssue(kind))

  override def foldPair(right: BundleSource, left: BundleSource, alt: Option[DecisionKind], folds: PlanFolds): Unit =
    runs.foreach(_.foldPair(right, left, alt, folds))

  override def decided(issue: MergeIssue, folds: PlanFolds): Unit = runs.foreach(_.decided(issue, folds))

  override def lines(es: BundleEntities, es0: BundleEntities): BundleEntities = through(runs, es)(_.lines(_, es0))

  override def problems(finalEs: BundleEntities, live: UUID => Boolean, writes: UUID => Boolean): List[String] =
    runs.flatMap(_.problems(finalEs, live, writes))

  override def dropped: Set[UUID] = runs.flatMap(_.dropped).toSet

  override def fixes: List[MergePlanner.AttestationFix] = runs.flatMap(_.fixes)

  override def tombstones: List[(UUID, BundleKind, UUID)] = runs.flatMap(_.tombstones)
