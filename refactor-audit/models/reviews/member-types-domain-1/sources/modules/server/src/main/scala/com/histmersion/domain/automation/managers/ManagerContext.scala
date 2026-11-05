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

  def event(id: com.histmersion.domain.automation.managers.ManagerContext.EventId, item: Option[com.histmersion.domain.automation.managers.ManagerContext.EventItem], run: Option[com.histmersion.domain.automation.managers.ManagerContext.EventRun], feed: com.histmersion.domain.automation.managers.ManagerContext.EventFeed, text: com.histmersion.domain.automation.managers.ManagerContext.EventText): Unit
  def lastEventOf(id: com.histmersion.domain.automation.managers.ManagerContext.LastEventOfId, feed: com.histmersion.domain.automation.managers.ManagerContext.LastEventOfFeed): Option[ManagerEvent]
  def countEvents(id: com.histmersion.domain.automation.managers.ManagerContext.CountEventsId, feed: com.histmersion.domain.automation.managers.ManagerContext.CountEventsFeed, since: Instant): com.histmersion.domain.automation.managers.ManagerContext.CountEventsResult
  def upsert(
      id: com.histmersion.domain.automation.managers.ManagerContext.UpsertId,
      kind: com.histmersion.domain.automation.managers.ManagerContext.UpsertKind,
      target: com.histmersion.domain.automation.managers.ManagerContext.UpsertTarget,
      origin: ItemOrigin,
      priority: com.histmersion.domain.automation.managers.ManagerContext.UpsertPriority,
      reopen: com.histmersion.domain.automation.managers.ManagerContext.UpsertReopen,
      state: ItemState = ItemState()
  ): (com.histmersion.domain.automation.managers.ManagerContext.UpsertResultPart1, com.histmersion.domain.automation.managers.ManagerContext.UpsertResultPart2)
  def item(itemId: com.histmersion.domain.automation.managers.ManagerContext.ItemItemId): Option[ManagerItem]
  def itemFor(id: com.histmersion.domain.automation.managers.ManagerContext.ItemForId, kind: com.histmersion.domain.automation.managers.ManagerContext.ItemForKind, target: com.histmersion.domain.automation.managers.ManagerContext.ItemForTarget): Option[ManagerItem]
  def hasItem(id: com.histmersion.domain.automation.managers.ManagerContext.HasItemId, kind: com.histmersion.domain.automation.managers.ManagerContext.HasItemKind, target: com.histmersion.domain.automation.managers.ManagerContext.HasItemTarget): com.histmersion.domain.automation.managers.ManagerContext.HasItemResult
  def restale(itemId: com.histmersion.domain.automation.managers.ManagerContext.RestaleItemId, priority: com.histmersion.domain.automation.managers.ManagerContext.RestalePriority, why: com.histmersion.domain.automation.managers.ManagerContext.RestaleWhy): com.histmersion.domain.automation.managers.ManagerContext.RestaleResult
  def pending(id: com.histmersion.domain.automation.managers.ManagerContext.PendingId): com.histmersion.domain.automation.managers.ManagerContext.PendingResult
  def openKeys(id: com.histmersion.domain.automation.managers.ManagerContext.OpenKeysId): List[(com.histmersion.domain.automation.managers.ManagerContext.OpenKeysResultPart1, com.histmersion.domain.automation.managers.ManagerContext.OpenKeysResultPart2)]
  def evaluated(
      itemId: com.histmersion.domain.automation.managers.ManagerContext.EvaluatedItemId,
      run: Option[com.histmersion.domain.automation.managers.ManagerContext.EvaluatedRun],
      status: ItemStatus,
      notBefore: Option[Instant],
      failures: Option[com.histmersion.domain.automation.managers.ManagerContext.EvaluatedFailures],
      state: ItemState,
      outcome: Option[com.histmersion.domain.automation.managers.ManagerContext.EvaluatedOutcome]
  ): com.histmersion.domain.automation.managers.ManagerContext.EvaluatedResult
  def hints(id: com.histmersion.domain.automation.managers.ManagerContext.HintsId): List[ManagerHint]

  /** A manager's state (`active`, `paused`, …), if it exists. */
  def state(id: com.histmersion.domain.automation.managers.ManagerContext.StateId): Option[com.histmersion.domain.automation.managers.ManagerContext.StateResult]

  /** Tick soon (an item was added). */
  def nudge(): Unit

  /** The review selection's article queries (the review kind's, until research owns them). */
  def openMajorArticles(id: com.histmersion.domain.automation.managers.ManagerContext.OpenMajorArticlesId, tags: List[com.histmersion.domain.automation.managers.ManagerContext.OpenMajorArticlesTags], goal: Option[com.histmersion.domain.automation.managers.ManagerContext.OpenMajorArticlesGoal], limit: com.histmersion.domain.automation.managers.ManagerContext.OpenMajorArticlesLimit): List[(com.histmersion.domain.automation.managers.ManagerContext.OpenMajorArticlesResultPart1, com.histmersion.domain.automation.managers.ManagerContext.OpenMajorArticlesResultPart2)]
  def unreviewedArticles(id: com.histmersion.domain.automation.managers.ManagerContext.UnreviewedArticlesId, tags: List[com.histmersion.domain.automation.managers.ManagerContext.UnreviewedArticlesTags], goal: Option[com.histmersion.domain.automation.managers.ManagerContext.UnreviewedArticlesGoal], limit: com.histmersion.domain.automation.managers.ManagerContext.UnreviewedArticlesLimit): List[com.histmersion.domain.automation.managers.ManagerContext.UnreviewedArticlesResult]
  def staleCandidates(
      id: com.histmersion.domain.automation.managers.ManagerContext.StaleCandidatesId,
      tags: List[com.histmersion.domain.automation.managers.ManagerContext.StaleCandidatesTags],
      goal: Option[com.histmersion.domain.automation.managers.ManagerContext.StaleCandidatesGoal],
      limit: com.histmersion.domain.automation.managers.ManagerContext.StaleCandidatesLimit
  ): List[(com.histmersion.domain.automation.managers.ManagerContext.StaleCandidatesResultPart1, com.histmersion.domain.automation.managers.ManagerContext.StaleCandidatesResultPart2, Option[com.histmersion.domain.automation.managers.ManagerContext.StaleCandidatesResultPart3], Option[com.histmersion.domain.automation.managers.ManagerContext.StaleCandidatesResultPart4])]
  def extendCandidates(id: com.histmersion.domain.automation.managers.ManagerContext.ExtendCandidatesId, tags: List[com.histmersion.domain.automation.managers.ManagerContext.ExtendCandidatesTags], goal: Option[com.histmersion.domain.automation.managers.ManagerContext.ExtendCandidatesGoal], limit: com.histmersion.domain.automation.managers.ManagerContext.ExtendCandidatesLimit): List[(com.histmersion.domain.automation.managers.ManagerContext.ExtendCandidatesResultPart1, Option[com.histmersion.domain.automation.managers.ManagerContext.ExtendCandidatesResultPart2])]
  def articleFacets(slug: com.histmersion.domain.automation.managers.ManagerContext.ArticleFacetsSlug): (List[com.histmersion.domain.automation.managers.ManagerContext.ArticleFacetsResultPart1], List[com.histmersion.domain.automation.managers.ManagerContext.ArticleFacetsResultPart2])

  // ── runs, steps and approvals ─────────────────────────────────────────────────────────────────

  /** The progress line a run left ("" when none). */
  def progress(run: com.histmersion.domain.automation.managers.ManagerContext.ProgressRun): com.histmersion.domain.automation.managers.ManagerContext.ProgressResult

  /** The output of the last finished `step` of `run`, as recorded. */
  def stepOutput(run: com.histmersion.domain.automation.managers.ManagerContext.StepOutputRun, step: com.histmersion.domain.automation.managers.ManagerContext.StepOutputStep): Option[com.histmersion.domain.automation.managers.ManagerContext.StepOutputResult]
  def approvalsOf(run: com.histmersion.domain.automation.managers.ManagerContext.ApprovalsOfRun, kind: Approval.Kind): List[ApprovalInfo]

  /** [[StepFacts]] from the approval of `kind` a run opened: none, open (the item waits for it), or decided. */
  def approvalFacts(run: com.histmersion.domain.automation.managers.ManagerContext.ApprovalFactsRun, kind: Approval.Kind, none: com.histmersion.domain.automation.managers.ManagerContext.ApprovalFactsNone): StepFacts
  def unsettledOn(target: com.histmersion.domain.automation.managers.ManagerContext.UnsettledOnTarget): Seq[RunInfo]
  def runsFor(p: Pipeline, target: com.histmersion.domain.automation.managers.ManagerContext.RunsForTarget): Seq[RunInfo]
  def slugExists(slug: com.histmersion.domain.automation.managers.ManagerContext.SlugExistsSlug): com.histmersion.domain.automation.managers.ManagerContext.SlugExistsResult

  // ── goals ─────────────────────────────────────────────────────────────────────────────────────

  def parentOf(slug: com.histmersion.domain.automation.managers.ManagerContext.ParentOfSlug): Option[com.histmersion.domain.automation.managers.ManagerContext.ParentOfResult]
  def linkGoal(goal: com.histmersion.domain.automation.managers.ManagerContext.LinkGoalGoal, kind: LinkKind, ref: com.histmersion.domain.automation.managers.ManagerContext.LinkGoalRef, note: Option[com.histmersion.domain.automation.managers.ManagerContext.LinkGoalNote]): Unit

  /** The references a goal links of `kind`. */
  def goalLinks(goal: com.histmersion.domain.automation.managers.ManagerContext.GoalLinksGoal, kind: LinkKind): List[com.histmersion.domain.automation.managers.ManagerContext.GoalLinksResult]

  /** What is left today of the legal open-access ingests all watchers share (None: the autopilot is off). */
  def sharedIngestLeft(now: Instant): Option[com.histmersion.domain.automation.managers.ManagerContext.SharedIngestLeftResult]

  /** The autopilot gives a goal's new sub-goals their managers at its next tick. */
  def autopilotSoon(): Unit

  // ── the engine's mechanics ────────────────────────────────────────────────────────────────────

  /** Manager `id` of kind `k` exists (created paused, or active while the autopilot is on). */
  def ensure(k: Kind, id: com.histmersion.domain.automation.managers.ManagerContext.EnsureId, goal: Option[com.histmersion.domain.automation.managers.ManagerContext.EnsureGoal]): com.histmersion.domain.automation.managers.ManagerContext.EnsureResult

  /** A review item for `slug` in review manager `id`, from a chain (priority 2): added, or a settled one re-opened. */
  def chainReview(id: com.histmersion.domain.automation.managers.ManagerContext.ChainReviewId, slug: com.histmersion.domain.automation.managers.ManagerContext.ChainReviewSlug, why: com.histmersion.domain.automation.managers.ManagerContext.ChainReviewWhy, out: Builder[com.histmersion.domain.automation.managers.ManagerContext.ChainReviewOutPart1, List[com.histmersion.domain.automation.managers.ManagerContext.ChainReviewOutPart2]]): Unit

  /** An approved `article-plan`'s compile items. */
  def applyPlan(a: ApprovalInfo, out: Builder[com.histmersion.domain.automation.managers.ManagerContext.ApplyPlanOutPart1, List[com.histmersion.domain.automation.managers.ManagerContext.ApplyPlanOutPart2]]): com.histmersion.domain.automation.managers.ManagerContext.ApplyPlanResult

  /** Settle a pending item without a run. */
  def settleNow(m: ManagerInfo, item: ManagerItem, status: ItemStatus, why: com.histmersion.domain.automation.managers.ManagerContext.SettleNowWhy): Option[com.histmersion.domain.automation.managers.ManagerContext.SettleNowResult]

  /** A failed run's item back in the queue after `notBefore`. */
  def retry(
      m: ManagerInfo,
      item: ManagerItem,
      run: com.histmersion.domain.automation.managers.ManagerContext.RetryRun,
      notBefore: Instant,
      failures: com.histmersion.domain.automation.managers.ManagerContext.RetryFailures,
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
  type EventId = EventId.T
  object EventId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EventItem = EventItem.T
  object EventItem extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EventRun = EventRun.T
  object EventRun extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EventFeed = EventFeed.T
  object EventFeed extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EventText = EventText.T
  object EventText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LastEventOfId = LastEventOfId.T
  object LastEventOfId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LastEventOfFeed = LastEventOfFeed.T
  object LastEventOfFeed extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CountEventsResult = CountEventsResult.T
  object CountEventsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CountEventsId = CountEventsId.T
  object CountEventsId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CountEventsFeed = CountEventsFeed.T
  object CountEventsFeed extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UpsertResultPart1 = UpsertResultPart1.T
  object UpsertResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UpsertResultPart2 = UpsertResultPart2.T
  object UpsertResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UpsertId = UpsertId.T
  object UpsertId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UpsertKind = UpsertKind.T
  object UpsertKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UpsertTarget = UpsertTarget.T
  object UpsertTarget extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UpsertPriority = UpsertPriority.T
  object UpsertPriority extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UpsertReopen = UpsertReopen.T
  object UpsertReopen extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ItemItemId = ItemItemId.T
  object ItemItemId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ItemForId = ItemForId.T
  object ItemForId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ItemForKind = ItemForKind.T
  object ItemForKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ItemForTarget = ItemForTarget.T
  object ItemForTarget extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HasItemResult = HasItemResult.T
  object HasItemResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HasItemId = HasItemId.T
  object HasItemId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HasItemKind = HasItemKind.T
  object HasItemKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HasItemTarget = HasItemTarget.T
  object HasItemTarget extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RestaleResult = RestaleResult.T
  object RestaleResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RestaleItemId = RestaleItemId.T
  object RestaleItemId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RestalePriority = RestalePriority.T
  object RestalePriority extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RestaleWhy = RestaleWhy.T
  object RestaleWhy extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PendingResult = PendingResult.T
  object PendingResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PendingId = PendingId.T
  object PendingId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OpenKeysResultPart1 = OpenKeysResultPart1.T
  object OpenKeysResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OpenKeysResultPart2 = OpenKeysResultPart2.T
  object OpenKeysResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OpenKeysId = OpenKeysId.T
  object OpenKeysId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EvaluatedResult = EvaluatedResult.T
  object EvaluatedResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EvaluatedItemId = EvaluatedItemId.T
  object EvaluatedItemId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EvaluatedRun = EvaluatedRun.T
  object EvaluatedRun extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EvaluatedFailures = EvaluatedFailures.T
  object EvaluatedFailures extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EvaluatedOutcome = EvaluatedOutcome.T
  object EvaluatedOutcome extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HintsId = HintsId.T
  object HintsId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StateResult = StateResult.T
  object StateResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StateId = StateId.T
  object StateId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OpenMajorArticlesResultPart1 = OpenMajorArticlesResultPart1.T
  object OpenMajorArticlesResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OpenMajorArticlesResultPart2 = OpenMajorArticlesResultPart2.T
  object OpenMajorArticlesResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OpenMajorArticlesId = OpenMajorArticlesId.T
  object OpenMajorArticlesId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OpenMajorArticlesTags = OpenMajorArticlesTags.T
  object OpenMajorArticlesTags extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OpenMajorArticlesGoal = OpenMajorArticlesGoal.T
  object OpenMajorArticlesGoal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OpenMajorArticlesLimit = OpenMajorArticlesLimit.T
  object OpenMajorArticlesLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UnreviewedArticlesResult = UnreviewedArticlesResult.T
  object UnreviewedArticlesResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UnreviewedArticlesId = UnreviewedArticlesId.T
  object UnreviewedArticlesId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UnreviewedArticlesTags = UnreviewedArticlesTags.T
  object UnreviewedArticlesTags extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UnreviewedArticlesGoal = UnreviewedArticlesGoal.T
  object UnreviewedArticlesGoal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UnreviewedArticlesLimit = UnreviewedArticlesLimit.T
  object UnreviewedArticlesLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StaleCandidatesResultPart1 = StaleCandidatesResultPart1.T
  object StaleCandidatesResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StaleCandidatesResultPart2 = StaleCandidatesResultPart2.T
  object StaleCandidatesResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StaleCandidatesResultPart3 = StaleCandidatesResultPart3.T
  object StaleCandidatesResultPart3 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StaleCandidatesResultPart4 = StaleCandidatesResultPart4.T
  object StaleCandidatesResultPart4 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StaleCandidatesId = StaleCandidatesId.T
  object StaleCandidatesId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StaleCandidatesTags = StaleCandidatesTags.T
  object StaleCandidatesTags extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StaleCandidatesGoal = StaleCandidatesGoal.T
  object StaleCandidatesGoal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StaleCandidatesLimit = StaleCandidatesLimit.T
  object StaleCandidatesLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ExtendCandidatesResultPart1 = ExtendCandidatesResultPart1.T
  object ExtendCandidatesResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ExtendCandidatesResultPart2 = ExtendCandidatesResultPart2.T
  object ExtendCandidatesResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ExtendCandidatesId = ExtendCandidatesId.T
  object ExtendCandidatesId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ExtendCandidatesTags = ExtendCandidatesTags.T
  object ExtendCandidatesTags extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ExtendCandidatesGoal = ExtendCandidatesGoal.T
  object ExtendCandidatesGoal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ExtendCandidatesLimit = ExtendCandidatesLimit.T
  object ExtendCandidatesLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ArticleFacetsResultPart1 = ArticleFacetsResultPart1.T
  object ArticleFacetsResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ArticleFacetsResultPart2 = ArticleFacetsResultPart2.T
  object ArticleFacetsResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ArticleFacetsSlug = ArticleFacetsSlug.T
  object ArticleFacetsSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ProgressResult = ProgressResult.T
  object ProgressResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ProgressRun = ProgressRun.T
  object ProgressRun extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StepOutputResult = StepOutputResult.T
  object StepOutputResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StepOutputRun = StepOutputRun.T
  object StepOutputRun extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StepOutputStep = StepOutputStep.T
  object StepOutputStep extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApprovalsOfRun = ApprovalsOfRun.T
  object ApprovalsOfRun extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApprovalFactsRun = ApprovalFactsRun.T
  object ApprovalFactsRun extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApprovalFactsNone = ApprovalFactsNone.T
  object ApprovalFactsNone extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UnsettledOnTarget = UnsettledOnTarget.T
  object UnsettledOnTarget extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RunsForTarget = RunsForTarget.T
  object RunsForTarget extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SlugExistsResult = SlugExistsResult.T
  object SlugExistsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SlugExistsSlug = SlugExistsSlug.T
  object SlugExistsSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParentOfResult = ParentOfResult.T
  object ParentOfResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParentOfSlug = ParentOfSlug.T
  object ParentOfSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LinkGoalGoal = LinkGoalGoal.T
  object LinkGoalGoal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LinkGoalRef = LinkGoalRef.T
  object LinkGoalRef extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LinkGoalNote = LinkGoalNote.T
  object LinkGoalNote extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GoalLinksResult = GoalLinksResult.T
  object GoalLinksResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GoalLinksGoal = GoalLinksGoal.T
  object GoalLinksGoal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SharedIngestLeftResult = SharedIngestLeftResult.T
  object SharedIngestLeftResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EnsureResult = EnsureResult.T
  object EnsureResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EnsureId = EnsureId.T
  object EnsureId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EnsureGoal = EnsureGoal.T
  object EnsureGoal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ChainReviewId = ChainReviewId.T
  object ChainReviewId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ChainReviewSlug = ChainReviewSlug.T
  object ChainReviewSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
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
  type RetryRun = RetryRun.T
  object RetryRun extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RetryFailures = RetryFailures.T
  object RetryFailures extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
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
