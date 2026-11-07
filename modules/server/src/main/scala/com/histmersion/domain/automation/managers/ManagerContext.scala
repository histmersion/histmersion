package com.histmersion.domain.automation.managers

import com.histmersion.api.automation.Agents.{ApprovalInfo, Pipeline, RunInfo, RunParams, RunRequest, RunState}
import com.histmersion.api.automation.Goal.Plans.LinkKind
import com.histmersion.api.automation.Managers.*
import com.histmersion.config.AgentsConfig
import com.histmersion.domain.ManagerPolicy.{ReviewSettings, StepFacts}

import java.time.Instant
import scala.collection.mutable.Builder
import com.histmersion.domain.automation.Approval

/** What the supervisor gives a [[ManagerKindDefinition]]: its managers' items, events and hints, the runs and approvals
  * they left, and the engine's mechanics of starting, retrying and settling an item. Every write is the one the
  * supervisor itself makes, with the same events.
  */
trait ManagerContext:
  def cfg: AgentsConfig
  def settings(m: ManagerInfo): ReviewSettings

  // ── the managers' items, events and hints ─────────────────────────────────────────────────────

  def event(id: com.histmersion.domain.automation.managers.ManagerLight.Id, item: Option[com.histmersion.domain.automation.managers.ManagerContext.ItemId], run: Option[com.histmersion.domain.automation.Run.Id], feed: com.histmersion.domain.automation.managers.ManagerContext.EventFeed, text: com.histmersion.domain.automation.managers.ManagerContext.EventText): Unit
  def lastEventOf(id: com.histmersion.domain.automation.managers.ManagerLight.Id, feed: com.histmersion.domain.automation.managers.ManagerContext.LastEventOfFeed): Option[ManagerEvent]
  def countEvents(id: com.histmersion.domain.automation.managers.ManagerLight.Id, feed: com.histmersion.domain.automation.managers.ManagerContext.CountEventsFeed, since: Instant): com.histmersion.domain.automation.managers.ManagerContext.CountEventsResult
  def upsert(
      id: com.histmersion.domain.automation.managers.ManagerLight.Id,
      kind: com.histmersion.domain.automation.managers.ManagerContext.ItemKind,
      target: com.histmersion.domain.automation.managers.ManagerContext.ItemTarget,
      origin: ItemOrigin,
      priority: com.histmersion.domain.automation.managers.ManagerContext.Priority,
      reopen: com.histmersion.domain.automation.managers.ManagerContext.UpsertReopen,
      state: ItemState = ItemState()
  ): (com.histmersion.domain.automation.managers.ManagerContext.UpsertResultPart1, Boolean)
  def item(itemId: com.histmersion.domain.automation.managers.ManagerContext.ItemId): Option[ManagerItem]
  def itemFor(id: com.histmersion.domain.automation.managers.ManagerLight.Id, kind: com.histmersion.domain.automation.managers.ManagerContext.ItemKind, target: com.histmersion.domain.automation.managers.ManagerContext.ItemTarget): Option[ManagerItem]
  def hasItem(id: com.histmersion.domain.automation.managers.ManagerLight.Id, kind: com.histmersion.domain.automation.managers.ManagerContext.ItemKind, target: com.histmersion.domain.automation.managers.ManagerContext.ItemTarget): Boolean
  def restale(itemId: com.histmersion.domain.automation.managers.ManagerContext.ItemId, priority: com.histmersion.domain.automation.managers.ManagerContext.Priority, why: com.histmersion.domain.automation.managers.ManagerRepo.RestaleReason): Boolean
  def pending(id: com.histmersion.domain.automation.managers.ManagerLight.Id): com.histmersion.domain.automation.managers.ManagerContext.PendingResult
  def openKeys(id: com.histmersion.domain.automation.managers.ManagerLight.Id): List[(com.histmersion.domain.automation.managers.ManagerContext.OpenKeysResultPart1, com.histmersion.domain.automation.managers.ManagerContext.OpenKeysResultPart2)]
  def evaluated(
      itemId: com.histmersion.domain.automation.managers.ManagerContext.ItemId,
      run: Option[com.histmersion.domain.automation.Run.Id],
      status: ItemStatus,
      notBefore: Option[Instant],
      failures: Option[com.histmersion.domain.automation.managers.ManagerContext.FailureCount],
      state: ItemState,
      outcome: Option[com.histmersion.domain.automation.managers.ManagerContext.EvaluatedOutcome]
  ): Boolean
  def hints(id: com.histmersion.domain.automation.managers.ManagerLight.Id): List[ManagerHint]

  /** A manager's state (`active`, `paused`, …), if it exists. */
  def state(id: com.histmersion.domain.automation.managers.ManagerLight.Id): Option[com.histmersion.domain.automation.managers.ManagerContext.StateResult]

  /** Tick soon (an item was added). */
  def nudge(): Unit

  /** The review selection's article queries (the review kind's, until research owns them). */
  def openMajorArticles(id: com.histmersion.domain.automation.managers.ManagerLight.Id, tags: List[com.histmersion.domain.automation.goals.GoalRow.Tags], goal: Option[com.histmersion.domain.automation.Goal.Slug], limit: com.histmersion.domain.automation.managers.ManagerContext.Limit): List[(com.histmersion.domain.automation.managers.ManagerContext.OpenMajorArticlesResultPart1, com.histmersion.domain.automation.managers.ManagerContext.OpenMajorArticlesResultPart2)]
  def unreviewedArticles(id: com.histmersion.domain.automation.managers.ManagerLight.Id, tags: List[com.histmersion.domain.automation.goals.GoalRow.Tags], goal: Option[com.histmersion.domain.automation.Goal.Slug], limit: com.histmersion.domain.automation.managers.ManagerContext.Limit): List[com.histmersion.domain.automation.managers.ManagerContext.UnreviewedArticlesResult]
  def staleCandidates(
      id: com.histmersion.domain.automation.managers.ManagerLight.Id,
      tags: List[com.histmersion.domain.automation.goals.GoalRow.Tags],
      goal: Option[com.histmersion.domain.automation.Goal.Slug],
      limit: com.histmersion.domain.automation.managers.ManagerContext.Limit
  ): List[(com.histmersion.domain.automation.managers.ManagerContext.StaleCandidatesResultPart1, com.histmersion.domain.automation.managers.ManagerContext.StaleCandidatesResultPart2, Option[com.histmersion.domain.automation.managers.ManagerContext.StaleCandidatesResultPart3], Option[com.histmersion.domain.automation.managers.ManagerContext.StaleCandidatesResultPart4])]
  def extendCandidates(id: com.histmersion.domain.automation.managers.ManagerLight.Id, tags: List[com.histmersion.domain.automation.goals.GoalRow.Tags], goal: Option[com.histmersion.domain.automation.Goal.Slug], limit: com.histmersion.domain.automation.managers.ManagerContext.Limit): List[(com.histmersion.domain.automation.managers.ManagerContext.ExtendCandidatesResultPart1, Option[com.histmersion.domain.automation.managers.ManagerContext.ExtendCandidatesResultPart2])]
  def articleFacets(slug: com.histmersion.domain.research.Slug): (List[com.histmersion.domain.automation.managers.ManagerContext.ArticleFacetsResultPart1], List[com.histmersion.domain.automation.managers.ManagerContext.ArticleFacetsResultPart2])

  // ── runs, steps and approvals ─────────────────────────────────────────────────────────────────

  /** The progress line a run left ("" when none). */
  def progress(run: com.histmersion.domain.automation.Run.Id): com.histmersion.domain.automation.managers.ManagerContext.ProgressResult

  /** The output of the last finished `step` of `run`, as recorded. */
  def stepOutput(run: com.histmersion.domain.automation.Run.Id, step: com.histmersion.domain.automation.managers.ManagerContext.StepOutputStep): Option[com.histmersion.domain.automation.managers.ManagerContext.StepOutputResult]
  def approvalsOf(run: com.histmersion.domain.automation.Run.Id, kind: Approval.Kind): List[ApprovalInfo]

  /** [[StepFacts]] from the approval of `kind` a run opened: none, open (the item waits for it), or decided. */
  def approvalFacts(run: com.histmersion.domain.automation.Run.Id, kind: Approval.Kind, none: com.histmersion.domain.automation.managers.ManagerContext.ApprovalFactsNone): StepFacts
  def unsettledOn(target: com.histmersion.domain.automation.managers.ManagerContext.ItemTarget): Seq[RunInfo]
  def runsFor(p: Pipeline, target: com.histmersion.domain.automation.managers.ManagerContext.ItemTarget): Seq[RunInfo]
  def slugExists(slug: com.histmersion.domain.automation.Goal.Slug): Boolean

  // ── goals ─────────────────────────────────────────────────────────────────────────────────────

  def parentOf(slug: com.histmersion.domain.automation.Goal.Slug): Option[com.histmersion.domain.automation.Goal.Slug]
  def linkGoal(goal: com.histmersion.domain.automation.Goal.Slug, kind: LinkKind, ref: com.histmersion.domain.automation.goals.BGoalLinkRow.Ref, note: Option[com.histmersion.domain.automation.goals.BGoalLinkRow.Note]): Unit

  /** The references a goal links of `kind`. */
  def goalLinks(goal: com.histmersion.domain.automation.Goal.Slug, kind: LinkKind): List[com.histmersion.domain.automation.managers.ManagerContext.GoalLinksResult]

  /** What is left today of the legal open-access ingests all watchers share (None: the autopilot is off). */
  def sharedIngestLeft(now: Instant): Option[com.histmersion.domain.automation.managers.ManagerContext.SharedIngestLeftResult]

  /** The autopilot gives a goal's new sub-goals their managers at its next tick. */
  def autopilotSoon(): Unit

  // ── the engine's mechanics ────────────────────────────────────────────────────────────────────

  /** Manager `id` of kind `k` exists (created paused, or active while the autopilot is on). */
  def ensure(k: Kind, id: com.histmersion.domain.automation.managers.ManagerLight.Id, goal: Option[com.histmersion.domain.automation.Goal.Slug]): Boolean

  /** A review item for `slug` in review manager `id`, from a chain (priority 2): added, or a settled one re-opened. */
  def chainReview(id: com.histmersion.domain.automation.managers.ManagerLight.Id, slug: com.histmersion.domain.automation.Goal.Slug, why: com.histmersion.domain.automation.managers.ManagerContext.ChainReviewWhy, out: Builder[com.histmersion.domain.automation.managers.ManagerContext.ChainReviewOutPart1, List[com.histmersion.domain.automation.managers.ManagerContext.ChainReviewOutPart2]]): Unit

  /** An approved `article-plan`'s compile items. */
  def applyPlan(a: ApprovalInfo, out: Builder[com.histmersion.domain.automation.managers.ManagerContext.ApplyPlanOutPart1, List[com.histmersion.domain.automation.managers.ManagerContext.ApplyPlanOutPart2]]): com.histmersion.domain.automation.managers.ManagerContext.ApplyPlanResult

  /** Settle a pending item without a run. */
  def settleNow(m: ManagerInfo, item: ManagerItem, status: ItemStatus, why: com.histmersion.domain.automation.managers.ManagerContext.SettleNowWhy): Option[com.histmersion.domain.automation.managers.ManagerContext.SettleNowResult]

  /** A failed run's item back in the queue after `notBefore`. */
  def retry(
      m: ManagerInfo,
      item: ManagerItem,
      run: com.histmersion.domain.automation.Run.Id,
      notBefore: Instant,
      failures: com.histmersion.domain.automation.managers.ManagerContext.FailureCount,
      note: com.histmersion.domain.automation.managers.ManagerContext.RetryNote,
      out: Builder[com.histmersion.domain.automation.managers.ManagerContext.RetryOutPart1, List[com.histmersion.domain.automation.managers.ManagerContext.RetryOutPart2]]
  ): Unit

  /** Create, mark and submit one run of `item` (one transaction); `tweak` sets the options the request does not carry.
    */
  def start(
      m: ManagerInfo,
      item: ManagerItem,
      req: RunRequest,
      tweak: RunParams => RunParams,
      state: ItemState,
      what: com.histmersion.domain.automation.managers.ManagerContext.StartWhat,
      out: Builder[com.histmersion.domain.automation.managers.ManagerContext.StartOutPart1, List[com.histmersion.domain.automation.managers.ManagerContext.StartOutPart2]]
  ): Option[com.histmersion.domain.automation.managers.ManagerContext.StartResult]

  /** The one-run rule (infer-filter, extend, enrich, propose, compile, plan, sweeps) after a run settled. */
  def evaluateStep(
      m: ManagerInfo,
      s: ReviewSettings,
      item: ManagerItem,
      st: RunState,
      error: Option[com.histmersion.domain.automation.managers.ManagerContext.EvaluateStepError],
      now: Instant,
      out: Builder[com.histmersion.domain.automation.managers.ManagerContext.EvaluateStepOutPart1, List[com.histmersion.domain.automation.managers.ManagerContext.EvaluateStepOutPart2]],
      kind: ManagerKindDefinition
  ): Unit

object ManagerContext:
  type FailureCount = FailureCount.T
  object FailureCount extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ItemId = ItemId.T
  object ItemId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ItemKind = ItemKind.T
  object ItemKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ItemTarget = ItemTarget.T
  object ItemTarget extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Limit = Limit.T
  object Limit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Priority = Priority.T
  object Priority extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EventFeed = EventFeed.T
  object EventFeed extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EventText = EventText.T
  object EventText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LastEventOfFeed = LastEventOfFeed.T
  object LastEventOfFeed extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CountEventsResult = CountEventsResult.T
  object CountEventsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CountEventsFeed = CountEventsFeed.T
  object CountEventsFeed extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UpsertResultPart1 = UpsertResultPart1.T
  object UpsertResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UpsertReopen = UpsertReopen.T
  object UpsertReopen extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PendingResult = PendingResult.T
  object PendingResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OpenKeysResultPart1 = OpenKeysResultPart1.T
  object OpenKeysResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OpenKeysResultPart2 = OpenKeysResultPart2.T
  object OpenKeysResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EvaluatedOutcome = EvaluatedOutcome.T
  object EvaluatedOutcome extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StateResult = StateResult.T
  object StateResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OpenMajorArticlesResultPart1 = OpenMajorArticlesResultPart1.T
  object OpenMajorArticlesResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OpenMajorArticlesResultPart2 = OpenMajorArticlesResultPart2.T
  object OpenMajorArticlesResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UnreviewedArticlesResult = UnreviewedArticlesResult.T
  object UnreviewedArticlesResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StaleCandidatesResultPart1 = StaleCandidatesResultPart1.T
  object StaleCandidatesResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StaleCandidatesResultPart2 = StaleCandidatesResultPart2.T
  object StaleCandidatesResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StaleCandidatesResultPart3 = StaleCandidatesResultPart3.T
  object StaleCandidatesResultPart3 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StaleCandidatesResultPart4 = StaleCandidatesResultPart4.T
  object StaleCandidatesResultPart4 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ExtendCandidatesResultPart1 = ExtendCandidatesResultPart1.T
  object ExtendCandidatesResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ExtendCandidatesResultPart2 = ExtendCandidatesResultPart2.T
  object ExtendCandidatesResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ArticleFacetsResultPart1 = ArticleFacetsResultPart1.T
  object ArticleFacetsResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ArticleFacetsResultPart2 = ArticleFacetsResultPart2.T
  object ArticleFacetsResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ProgressResult = ProgressResult.T
  object ProgressResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StepOutputResult = StepOutputResult.T
  object StepOutputResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StepOutputStep = StepOutputStep.T
  object StepOutputStep extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApprovalFactsNone = ApprovalFactsNone.T
  object ApprovalFactsNone extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GoalLinksResult = GoalLinksResult.T
  object GoalLinksResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SharedIngestLeftResult = SharedIngestLeftResult.T
  object SharedIngestLeftResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ChainReviewWhy = ChainReviewWhy.T
  object ChainReviewWhy extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ChainReviewOutPart1 = ChainReviewOutPart1.T
  object ChainReviewOutPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ChainReviewOutPart2 = ChainReviewOutPart2.T
  object ChainReviewOutPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApplyPlanResult = ApplyPlanResult.T
  object ApplyPlanResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApplyPlanOutPart1 = ApplyPlanOutPart1.T
  object ApplyPlanOutPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApplyPlanOutPart2 = ApplyPlanOutPart2.T
  object ApplyPlanOutPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SettleNowResult = SettleNowResult.T
  object SettleNowResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SettleNowWhy = SettleNowWhy.T
  object SettleNowWhy extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RetryNote = RetryNote.T
  object RetryNote extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RetryOutPart1 = RetryOutPart1.T
  object RetryOutPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RetryOutPart2 = RetryOutPart2.T
  object RetryOutPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StartResult = StartResult.T
  object StartResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StartWhat = StartWhat.T
  object StartWhat extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StartOutPart1 = StartOutPart1.T
  object StartOutPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StartOutPart2 = StartOutPart2.T
  object StartOutPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EvaluateStepError = EvaluateStepError.T
  object EvaluateStepError extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EvaluateStepOutPart1 = EvaluateStepOutPart1.T
  object EvaluateStepOutPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EvaluateStepOutPart2 = EvaluateStepOutPart2.T
  object EvaluateStepOutPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
