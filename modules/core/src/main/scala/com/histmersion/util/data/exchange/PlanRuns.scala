package com.histmersion.util.data.exchange

import com.histmersion.api.exchange.EditionsPolicy

import com.histmersion.api.exchange.Bundle

import com.histmersion.api.exchange.{IssueCondition, IssueDetail, MergeDecision, MergeIssue, MergePlan}
import com.histmersion.domain.{MergePlanner, MergeSettings}
import com.histmersion.util.data.exchange.BundleEntities
import com.histmersion.util.text.MinHash

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

  override def sketchesWanted(cfg: MergeSettings): Set[String] = runs.flatMap(_.sketchesWanted(cfg)).toSet

  override def matchSources(
      sketches: Map[String, MinHash],
      cfg: MergeSettings,
      editions: Option[EditionsPolicy]
  ): List[SourcePair] = runs.flatMap(_.matchSources(sketches, cfg, editions))

  override def sourceQuestions(
      fates: Map[UUID, SourceFate],
      sketches: Map[String, MinHash],
      cfg: MergeSettings,
      questions: PairQuestions
  ): Unit = runs.foreach(_.sourceQuestions(fates, sketches, cfg, questions))

  override def bodyIssue(right: UUID): Option[String] = runs.iterator.flatMap(_.bodyIssue(right)).nextOption()

  override def pairFirst(
      right: com.histmersion.api.exchange.Bundle.Source,
      left: com.histmersion.api.exchange.Bundle.Source,
      cond: Option[IssueCondition],
      cfg: MergeSettings,
      questions: PairQuestions
  ): Unit = runs.foreach(_.pairFirst(right, left, cond, cfg, questions))

  override def labelsTakenFirst(questions: PlanQuestions): Unit = runs.foreach(_.labelsTakenFirst(questions))

  override def citekeyArgument(issue: MergeIssue, decision: MergeDecision): Option[Either[String, Unit]] =
    runs.iterator.flatMap(_.citekeyArgument(issue, decision)).nextOption()

  /** Every run folds; the folded source takes the bundle's body when one of them says so. */
  override def foldSources(
      right: com.histmersion.api.exchange.Bundle.Source,
      left: com.histmersion.api.exchange.Bundle.Source,
      citekey: String,
      folds: PlanFolds
  ): Boolean =
    runs.map(_.foldSources(right, left, citekey, folds)).exists(identity)

  override def renamed(source: UUID, citekey: String): Unit = runs.foreach(_.renamed(source, citekey))

  override def relabelFirst(fates: Map[UUID, SourceFate], relabels: PlanRelabels): Unit =
    runs.foreach(_.relabelFirst(fates, relabels))

  override def ownerSource(foreign: UUID, kind: Bundle.Kind): Option[UUID] =
    runs.iterator.flatMap(_.ownerSource(foreign, kind)).nextOption()

  override def bodiesWanted(
      pairs: List[(com.histmersion.api.exchange.Bundle.Source, com.histmersion.api.exchange.Bundle.Source)]
  ): Set[String] =
    runs.flatMap(_.bodiesWanted(pairs)).toSet

  override def pair(
      right: com.histmersion.api.exchange.Bundle.Source,
      left: com.histmersion.api.exchange.Bundle.Source,
      cond: Option[IssueCondition],
      bodyIssue: Option[String],
      questions: PairQuestions
  ): Unit = runs.foreach(_.pair(right, left, cond, bodyIssue, questions))

  override def rowDetail(kind: Bundle.Kind, u: UUID, localized: BundleEntities): Option[IssueDetail] =
    runs.iterator.flatMap(_.rowDetail(kind, u, localized)).nextOption()

  override def conflict(row: MergePlanner.Row, localized: BundleEntities): Option[PlanMerge] =
    runs.iterator.flatMap(_.conflict(row, localized)).nextOption()

  override def labelsTaken(questions: PlanQuestions): Unit = runs.foreach(_.labelsTaken(questions))

  override def addedOnBoth(questions: PlanQuestions): Unit = runs.foreach(_.addedOnBoth(questions))

  override def argument(issue: MergeIssue, decision: MergeDecision): Option[Either[String, Unit]] =
    runs.iterator.flatMap(_.argument(issue, decision)).nextOption()

  override def pairIssue(kind: MergeIssue.Kind): Boolean = runs.exists(_.pairIssue(kind))

  override def foldPairFirst(
      right: com.histmersion.api.exchange.Bundle.Source,
      left: com.histmersion.api.exchange.Bundle.Source,
      keepsBundleBody: Boolean,
      folds: PlanFolds
  ): Unit = runs.foreach(_.foldPairFirst(right, left, keepsBundleBody, folds))

  override def foldPair(
      right: com.histmersion.api.exchange.Bundle.Source,
      left: com.histmersion.api.exchange.Bundle.Source,
      alt: Option[MergeDecision.Kind],
      folds: PlanFolds
  ): Unit =
    runs.foreach(_.foldPair(right, left, alt, folds))

  override def relabel(relabels: PlanRelabels): Unit = runs.foreach(_.relabel(relabels))

  override def decided(issue: MergeIssue, folds: PlanFolds): Unit = runs.foreach(_.decided(issue, folds))

  override def lines(es: BundleEntities, es0: BundleEntities): BundleEntities = through(runs, es)(_.lines(_, es0))

  override def problems(finalEs: BundleEntities, live: UUID => Boolean, writes: UUID => Boolean): List[String] =
    runs.flatMap(_.problems(finalEs, live, writes))

  override def dropped: Set[UUID] = runs.flatMap(_.dropped).toSet

  override def fixes: List[MergePlanner.AttestationFix] = runs.flatMap(_.fixes)

  override def tombstones: List[(UUID, Bundle.Kind, UUID)] = runs.flatMap(_.tombstones)

  override def bodyRenames: Map[UUID, List[(String, String)]] = runs.flatMap(_.bodyRenames).toMap

  override def planned(plan: MergePlan): MergePlan = runs.foldLeft(plan)((p, r) => r.planned(p))

  override def relinked(text: String, citekeys: Map[String, String]): String =
    @tailrec def loop(rest: List[PlanRun], t: String): String = rest match
      case r :: tail => loop(tail, r.relinked(t, citekeys))
      case Nil       => t
    loop(runs, text)
