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
  def lastEventOf(id: com.histmersion.domain.automation.managers.ManagerLight.Id, feed: com.histmersion.domain.automation.managers.ManagerContext.EventFeed): Option[ManagerEvent]
  def countEvents(id: com.histmersion.domain.automation.managers.ManagerLight.Id, feed: com.histmersion.domain.automation.managers.ManagerContext.EventFeed, since: Instant): com.histmersion.RowCount
  def upsert(
      id: com.histmersion.domain.automation.managers.ManagerLight.Id,
      kind: com.histmersion.domain.automation.managers.ManagerContext.ItemKind,
      target: com.histmersion.domain.automation.managers.ManagerContext.ItemTarget,
      origin: ItemOrigin,
      priority: com.histmersion.domain.automation.managers.ManagerContext.Priority,
      reopen: com.histmersion.domain.automation.managers.ManagerContext.UpsertReopen,
      state: ItemState = ItemState()
  ): (com.histmersion.domain.automation.managers.ManagerContext.ItemId, Boolean)
  def item(itemId: com.histmersion.domain.automation.managers.ManagerContext.ItemId): Option[ManagerItem]
  def itemFor(id: com.histmersion.domain.automation.managers.ManagerLight.Id, kind: com.histmersion.domain.automation.managers.ManagerContext.ItemKind, target: com.histmersion.domain.automation.managers.ManagerContext.ItemTarget): Option[ManagerItem]
  def hasItem(id: com.histmersion.domain.automation.managers.ManagerLight.Id, kind: com.histmersion.domain.automation.managers.ManagerContext.ItemKind, target: com.histmersion.domain.automation.managers.ManagerContext.ItemTarget): Boolean
  def restale(itemId: com.histmersion.domain.automation.managers.ManagerContext.ItemId, priority: com.histmersion.domain.automation.managers.ManagerContext.Priority, why: com.histmersion.domain.automation.managers.ManagerRepo.RestaleReason): Boolean
  def pending(id: com.histmersion.domain.automation.managers.ManagerLight.Id): com.histmersion.RowCount
  def openKeys(id: com.histmersion.domain.automation.managers.ManagerLight.Id): List[(com.histmersion.domain.automation.managers.ManagerContext.ItemKind, com.histmersion.domain.automation.managers.ManagerContext.ItemTarget)]
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
  def state(id: com.histmersion.domain.automation.managers.ManagerLight.Id): Option[com.histmersion.domain.automation.managers.ManagerLight.State]

  /** Tick soon (an item was added). */
  def nudge(): Unit

  /** The review selection's article queries (the review kind's, until research owns them). */
  def openMajorArticles(id: com.histmersion.domain.automation.managers.ManagerLight.Id, tags: List[com.histmersion.domain.automation.goals.GoalRow.Tags], goal: Option[com.histmersion.domain.automation.Goal.Slug], limit: com.histmersion.domain.automation.managers.ManagerContext.Limit): List[(com.histmersion.domain.research.Slug, com.histmersion.RowCount)]
  def unreviewedArticles(id: com.histmersion.domain.automation.managers.ManagerLight.Id, tags: List[com.histmersion.domain.automation.goals.GoalRow.Tags], goal: Option[com.histmersion.domain.automation.Goal.Slug], limit: com.histmersion.domain.automation.managers.ManagerContext.Limit): List[com.histmersion.domain.research.Slug]
  def staleCandidates(
      id: com.histmersion.domain.automation.managers.ManagerLight.Id,
      tags: List[com.histmersion.domain.automation.goals.GoalRow.Tags],
      goal: Option[com.histmersion.domain.automation.Goal.Slug],
      limit: com.histmersion.domain.automation.managers.ManagerContext.Limit
  ): List[(com.histmersion.domain.research.Slug, com.histmersion.domain.automation.managers.ManagerRepo.ProgressText, Option[ItemState.FilterHits], Option[com.histmersion.domain.automation.managers.ManagerContext.ItemId])]
  def extendCandidates(id: com.histmersion.domain.automation.managers.ManagerLight.Id, tags: List[com.histmersion.domain.automation.goals.GoalRow.Tags], goal: Option[com.histmersion.domain.automation.Goal.Slug], limit: com.histmersion.domain.automation.managers.ManagerContext.Limit): List[(com.histmersion.domain.research.Slug, Option[com.histmersion.domain.automation.managers.ManagerRepo.ProgressText])]
  def articleFacets(slug: com.histmersion.domain.research.Slug): (List[com.histmersion.domain.EnrichSource.Tag], List[com.histmersion.domain.automation.Goal.Slug])

  // ── runs, steps and approvals ─────────────────────────────────────────────────────────────────

  /** The progress line a run left ("" when none). */
  def progress(run: com.histmersion.domain.automation.Run.Id): com.histmersion.util.jobs.JobRow.Progress

  /** The output of the last finished `step` of `run`, as recorded. */
  def stepOutput(run: com.histmersion.domain.automation.Run.Id, step: com.histmersion.domain.automation.agents.AgentRepo.StepRecord.Name): Option[com.histmersion.domain.automation.agents.AgentRepo.StepRecord.Output]
  def approvalsOf(run: com.histmersion.domain.automation.Run.Id, kind: Approval.Kind): List[ApprovalInfo]

  /** [[StepFacts]] from the approval of `kind` a run opened: none, open (the item waits for it), or decided. */
  def approvalFacts(run: com.histmersion.domain.automation.Run.Id, kind: Approval.Kind, none: com.histmersion.domain.ManagerPolicy.ItemOutcome): StepFacts
  def unsettledOn(target: com.histmersion.domain.automation.managers.ManagerContext.ItemTarget): Seq[RunInfo]
  def runsFor(p: Pipeline, target: com.histmersion.domain.automation.managers.ManagerContext.ItemTarget): Seq[RunInfo]
  def slugExists(slug: com.histmersion.domain.research.Slug): Boolean

  // ── goals ─────────────────────────────────────────────────────────────────────────────────────

  def parentOf(slug: com.histmersion.domain.automation.Goal.Slug): Option[com.histmersion.domain.automation.Goal.Slug]
  def linkGoal(goal: com.histmersion.domain.automation.Goal.Slug, kind: LinkKind, ref: com.histmersion.domain.automation.goals.BGoalLinkRow.Ref, note: Option[com.histmersion.domain.automation.goals.BGoalLinkRow.Note]): Unit

  /** The references a goal links of `kind`. */
  def goalLinks(goal: com.histmersion.domain.automation.Goal.Slug, kind: LinkKind): List[com.histmersion.domain.automation.goals.BGoalLinkRow.Ref]

  /** What is left today of the legal open-access ingests all watchers share (None: the autopilot is off). */
  def sharedIngestLeft(now: Instant): Option[com.histmersion.RowCount]

  /** The autopilot gives a goal's new sub-goals their managers at its next tick. */
  def autopilotSoon(): Unit

  // ── the engine's mechanics ────────────────────────────────────────────────────────────────────

  /** Manager `id` of kind `k` exists (created paused, or active while the autopilot is on). */
  def ensure(k: Kind, id: com.histmersion.domain.automation.managers.ManagerLight.Id, goal: Option[com.histmersion.domain.automation.Goal.Slug]): Boolean

  /** A review item for `slug` in review manager `id`, from a chain (priority 2): added, or a settled one re-opened. */
  def chainReview(id: com.histmersion.domain.automation.managers.ManagerLight.Id, slug: com.histmersion.domain.research.Slug, why: com.histmersion.domain.automation.managers.ManagerContext.ChainReviewWhy, out: Builder[com.histmersion.domain.automation.managers.ManagerContext.TickLine, List[com.histmersion.domain.automation.managers.ManagerContext.TickLine]]): Unit

  /** An approved `article-plan`'s compile items. */
  def applyPlan(a: ApprovalInfo, out: Builder[com.histmersion.domain.automation.managers.ManagerContext.TickLine, List[com.histmersion.domain.automation.managers.ManagerContext.TickLine]]): com.histmersion.RowCount

  /** Settle a pending item without a run. */
  def settleNow(m: ManagerInfo, item: ManagerItem, status: ItemStatus, why: com.histmersion.domain.automation.managers.ManagerContext.SettleNowWhy): Option[com.histmersion.domain.automation.managers.ManagerLight.Id]

  /** A failed run's item back in the queue after `notBefore`. */
  def retry(
      m: ManagerInfo,
      item: ManagerItem,
      run: com.histmersion.domain.automation.Run.Id,
      notBefore: Instant,
      failures: com.histmersion.domain.automation.managers.ManagerContext.FailureCount,
      note: com.histmersion.domain.automation.managers.ManagerContext.RetryNote,
      out: Builder[com.histmersion.domain.automation.managers.ManagerContext.TickLine, List[com.histmersion.domain.automation.managers.ManagerContext.TickLine]]
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
      out: Builder[com.histmersion.domain.automation.managers.ManagerContext.TickLine, List[com.histmersion.domain.automation.managers.ManagerContext.TickLine]]
  ): Option[com.histmersion.domain.automation.managers.ManagerLight.Id]

  /** The one-run rule (infer-filter, extend, enrich, propose, compile, plan, sweeps) after a run settled. */
  def evaluateStep(
      m: ManagerInfo,
      s: ReviewSettings,
      item: ManagerItem,
      st: RunState,
      error: Option[com.histmersion.domain.automation.managers.ManagerContext.EvaluateStepError],
      now: Instant,
      out: Builder[com.histmersion.domain.automation.managers.ManagerContext.TickLine, List[com.histmersion.domain.automation.managers.ManagerContext.TickLine]],
      kind: ManagerKindDefinition
  ): Unit

object ManagerContext:
  type FailureCount = com.histmersion.domain.ManagerPolicy.Next.Retry.Failures
  val FailureCount: com.histmersion.domain.ManagerPolicy.Next.Retry.Failures.type = com.histmersion.domain.ManagerPolicy.Next.Retry.Failures
  type ItemId = com.histmersion.domain.ManagerPolicy.ItemId
  val ItemId: com.histmersion.domain.ManagerPolicy.ItemId.type = com.histmersion.domain.ManagerPolicy.ItemId
  type ItemKind = com.histmersion.domain.ManagerPolicy.ItemKind
  val ItemKind: com.histmersion.domain.ManagerPolicy.ItemKind.type = com.histmersion.domain.ManagerPolicy.ItemKind
  type ItemTarget = com.histmersion.domain.ManagerPolicy.Candidate.Target
  val ItemTarget: com.histmersion.domain.ManagerPolicy.Candidate.Target.type = com.histmersion.domain.ManagerPolicy.Candidate.Target
  type Limit = Limit.T
  object Limit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Priority = com.histmersion.domain.ManagerPolicy.Candidate.Priority
  val Priority: com.histmersion.domain.ManagerPolicy.Candidate.Priority.type = com.histmersion.domain.ManagerPolicy.Candidate.Priority
  type EventFeed = EventFeed.T
  object EventFeed extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EventText = EventText.T
  object EventText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UpsertReopen = UpsertReopen.T
  object UpsertReopen extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EvaluatedOutcome = com.histmersion.domain.ManagerPolicy.ItemOutcome
  val EvaluatedOutcome: com.histmersion.domain.ManagerPolicy.ItemOutcome.type = com.histmersion.domain.ManagerPolicy.ItemOutcome
  type ChainReviewWhy = ChainReviewWhy.T
  object ChainReviewWhy extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SettleNowWhy = com.histmersion.domain.ManagerPolicy.ItemOutcome
  val SettleNowWhy: com.histmersion.domain.ManagerPolicy.ItemOutcome.type = com.histmersion.domain.ManagerPolicy.ItemOutcome
  type RetryNote = com.histmersion.domain.ManagerPolicy.ItemNote
  val RetryNote: com.histmersion.domain.ManagerPolicy.ItemNote.type = com.histmersion.domain.ManagerPolicy.ItemNote
  type StartWhat = StartWhat.T
  object StartWhat extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EvaluateStepError = com.histmersion.domain.ManagerPolicy.Problem
  val EvaluateStepError: com.histmersion.domain.ManagerPolicy.Problem.type = com.histmersion.domain.ManagerPolicy.Problem
  type TickLine = TickLine.T
  object TickLine extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
