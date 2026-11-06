package com.histmersion.util.data.exchange

import com.histmersion.api.exchange.EditionsPolicy

import com.histmersion.api.exchange.Bundle

import com.histmersion.api.exchange.{IssueCondition, IssueDetail, MergeDecision, MergeIssue, MergePlan}
import com.histmersion.domain.{MergePlanner, MergeSettings}
import com.histmersion.util.data.exchange.BundleEntities
import com.histmersion.util.text.MinHash

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
  def notices: List[com.histmersion.util.data.exchange.PlanRun.NoticesResult] = Nil

  /** The OCR bodies (by sha256) whose sketches could decide whether a source of the bundle is one here: the database
    * computes them before the plan.
    */
  def sketchesWanted(cfg: MergeSettings): Set[com.histmersion.util.data.exchange.PlanRun.SketchesWantedResult] = Set.empty

  /** The bundle's sources that are, or may be, one with a source here, in the order they were matched. */
  def matchSources(
      sketches: Map[com.histmersion.util.data.exchange.PlanRun.MatchSourcesSketchesPart1, MinHash],
      cfg: MergeSettings,
      editions: Option[EditionsPolicy]
  ): List[SourcePair] = Nil

  /** The questions about the bundle's sources themselves, before every other: whether a pair is one work, the fields of
    * a pair, a citekey taken here. `fates` is what became of each of the bundle's sources.
    */
  def sourceQuestions(
      fates: Map[UUID, SourceFate],
      sketches: Map[com.histmersion.util.data.exchange.PlanRun.SourceQuestionsSketchesPart1, MinHash],
      cfg: MergeSettings,
      questions: PairQuestions
  ): Unit = ()

  /** The question of a pair about its OCR bodies, when there is one (the bundle's source by uid). */
  def bodyIssue(right: UUID): Option[com.histmersion.util.data.exchange.PlanRun.BodyIssueResult] = None

  /** What a pair of sources that are (or may be) one owns of this kind, asked before every kind's [[pair]]. */
  def pairFirst(
      right: com.histmersion.api.exchange.Bundle.Source,
      left: com.histmersion.api.exchange.Bundle.Source,
      cond: Option[IssueCondition],
      cfg: MergeSettings,
      questions: PairQuestions
  ): Unit = ()

  /** The kind's labels a different record has here, before every kind's [[labelsTaken]]. */
  def labelsTakenFirst(questions: PlanQuestions): Unit = ()

  /** A decision's argument that names a source by its citekey checked (`map`, a rename no kind checks itself). */
  def citekeyArgument(issue: MergeIssue, decision: MergeDecision): Option[Either[com.histmersion.util.data.exchange.PlanRun.CitekeyArgumentResultPart1, Unit]] = None

  /** A pair of sources that folds, under `citekey`, before every kind's [[foldPairFirst]]: whether the folded source
    * takes the bundle's body.
    */
  def foldSources(
      right: com.histmersion.api.exchange.Bundle.Source,
      left: com.histmersion.api.exchange.Bundle.Source,
      citekey: com.histmersion.domain.library.Citekey,
      folds: PlanFolds
  ): Boolean = false

  /** A source of the bundle that arrives under another citekey (a collision decided `rename-right`). */
  def renamed(source: UUID, citekey: com.histmersion.domain.library.Citekey): Unit = ()

  /** The bundle's source that a line of this kind belongs to (its fate decides whether the line is held). */
  def ownerSource(foreign: UUID, kind: Bundle.Kind): Option[UUID] = None

  /** The bodies of the sources here (by sha256) the plan reads for this kind: of the pairs whose bodies differ. */
  def bodiesWanted(
      pairs: List[(com.histmersion.api.exchange.Bundle.Source, com.histmersion.api.exchange.Bundle.Source)]
  ): Set[com.histmersion.util.data.exchange.PlanRun.BodiesWantedResult] = Set.empty

  /** What a pair of sources that are (or may be) one owns of this kind: its questions under each alternative of the
    * body question (`bodyIssue`), `cond` the pair's own condition. `right` is the bundle's source, `left` the one here.
    */
  def pair(
      right: com.histmersion.api.exchange.Bundle.Source,
      left: com.histmersion.api.exchange.Bundle.Source,
      cond: Option[IssueCondition],
      bodyIssue: Option[com.histmersion.util.data.exchange.PlanRun.PairBodyIssue],
      questions: PairQuestions
  ): Unit = ()

  /** The two records of a row question side by side, for a kind whose screen shows more than labels. */
  def rowDetail(kind: Bundle.Kind, u: UUID, localized: BundleEntities): Option[IssueDetail] = None

  /** A row changed on both sides that the kind merges by itself; `localized` is the bundle's lines in this collection's
    * terms.
    */
  def conflict(row: MergePlanner.Row, localized: BundleEntities): Option[PlanMerge] = None

  /** The kind's labels a different record has here. */
  def labelsTaken(questions: PlanQuestions): Unit = ()

  /** The kind's records added on both sides that are one, or a question. */
  def addedOnBoth(questions: PlanQuestions): Unit = ()

  /** A decision's argument checked for an issue of this kind; `None` for an issue the kind does not check. */
  def argument(issue: MergeIssue, decision: MergeDecision): Option[Either[com.histmersion.util.data.exchange.PlanRun.ArgumentResultPart1, Unit]] = None

  /** An issue of a pair: its decision is carried out with the pair's fold. */
  def pairIssue(kind: MergeIssue.Kind): Boolean = false

  /** What a pair that folds does with the kind's lines before every kind's [[foldPair]]: `keepsBundleBody` is whether
    * the folded source takes the bundle's body.
    */
  def foldPairFirst(
      right: com.histmersion.api.exchange.Bundle.Source,
      left: com.histmersion.api.exchange.Bundle.Source,
      keepsBundleBody: com.histmersion.util.data.exchange.PlanRun.FoldPairFirstKeepsBundleBody,
      folds: PlanFolds
  ): Unit = ()

  /** What a pair that folds does with the kind's lines; `alt` is the body question's decision, when there is one. */
  def foldPair(
      right: com.histmersion.api.exchange.Bundle.Source,
      left: com.histmersion.api.exchange.Bundle.Source,
      alt: Option[MergeDecision.Kind],
      folds: PlanFolds
  ): Unit = ()

  /** The kind's labels of inserted records that a different record has here, given new ones before every kind's
    * [[relabel]]; `fates` is what became of each of the bundle's sources.
    */
  def relabelFirst(fates: Map[UUID, SourceFate], relabels: PlanRelabels): Unit = ()

  /** The kind's labels of inserted records that a different record has here, given new ones. */
  def relabel(relabels: PlanRelabels): Unit = ()

  /** A decided issue of this kind carried out. */
  def decided(issue: MergeIssue, folds: PlanFolds): Unit = ()

  /** The final lines of this kind: `es0` is the bundle's lines in this collection's uids, folds made. */
  def lines(es: BundleEntities, es0: BundleEntities): BundleEntities = es

  /** What cannot be imported whatever is decided: the written lines (`writes`) that need what is not coming. */
  def problems(finalEs: BundleEntities, live: UUID => Boolean, writes: UUID => Boolean): List[com.histmersion.util.data.exchange.PlanRun.ProblemsResult] = Nil

  /** Bundle records left out for good: recorded as deleted here. */
  def dropped: Set[UUID] = Set.empty

  /** This collection's attestations moved because the merge keeps the bundle's body. */
  def fixes: List[MergePlanner.AttestationFix] = Nil

  /** The tombstones of the records left out: uid, kind, origin. */
  def tombstones: List[(UUID, Bundle.Kind, UUID)] = Nil

  /** What the merge renames in a body it takes from the bundle: source uid → (old name, new name) of its files. */
  def bodyRenames: Map[UUID, List[(com.histmersion.util.data.exchange.PlanRun.BodyRenamesResultPart2Part1, com.histmersion.util.data.exchange.PlanRun.BodyRenamesResultPart2Part2)]] = Map.empty

  /** The plan with what the kind lists of its own (how each of the bundle's sources was matched). */
  def planned(plan: MergePlan): MergePlan = plan

  /** A text of the bundle the merge writes here, its links to sources pointed at the citekeys those sources have here
    * (`citekeys`: the bundle's citekey → this collection's, of the sources whose citekey changes).
    */
  def relinked(text: com.histmersion.util.data.exchange.PlanRun.RelinkedText, citekeys: Map[com.histmersion.util.data.exchange.PlanRun.RelinkedCitekeysPart1, com.histmersion.util.data.exchange.PlanRun.RelinkedCitekeysPart2]): com.histmersion.util.data.exchange.PlanRun.RelinkedResult = text

object PlanRun:
  type NoticesResult = NoticesResult.T
  object NoticesResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SketchesWantedResult = SketchesWantedResult.T
  object SketchesWantedResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MatchSourcesSketchesPart1 = MatchSourcesSketchesPart1.T
  object MatchSourcesSketchesPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SourceQuestionsSketchesPart1 = SourceQuestionsSketchesPart1.T
  object SourceQuestionsSketchesPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BodyIssueResult = BodyIssueResult.T
  object BodyIssueResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CitekeyArgumentResultPart1 = CitekeyArgumentResultPart1.T
  object CitekeyArgumentResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BodiesWantedResult = BodiesWantedResult.T
  object BodiesWantedResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PairBodyIssue = PairBodyIssue.T
  object PairBodyIssue extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ArgumentResultPart1 = ArgumentResultPart1.T
  object ArgumentResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FoldPairFirstKeepsBundleBody = FoldPairFirstKeepsBundleBody.T
  object FoldPairFirstKeepsBundleBody extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ProblemsResult = ProblemsResult.T
  object ProblemsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BodyRenamesResultPart2Part1 = BodyRenamesResultPart2Part1.T
  object BodyRenamesResultPart2Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BodyRenamesResultPart2Part2 = BodyRenamesResultPart2Part2.T
  object BodyRenamesResultPart2Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RelinkedResult = RelinkedResult.T
  object RelinkedResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RelinkedText = RelinkedText.T
  object RelinkedText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RelinkedCitekeysPart1 = RelinkedCitekeysPart1.T
  object RelinkedCitekeysPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RelinkedCitekeysPart2 = RelinkedCitekeysPart2.T
  object RelinkedCitekeysPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
