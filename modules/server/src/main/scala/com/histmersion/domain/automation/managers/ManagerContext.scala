package com.histmersion
package domain.automation.managers
import com.histmersion.util.problem.Problem

import com.histmersion.api.automation.Agents.{ApprovalInfo, Pipeline, RunInfo, RunParams, RunRequest, RunState}
import com.histmersion.api.automation.Goal.Plans.LinkKind
import com.histmersion.api.automation.Managers.*
import com.histmersion.config.AgentsConfig
import com.histmersion.domain.ManagerPolicy.{ReviewSettings, StepFacts}

import java.time.Instant
import scala.collection.mutable.Builder
import com.histmersion.domain.automation.Approval
import com.histmersion.domain.automation.Run
import com.histmersion.domain.automation.goals.GoalRow
import com.histmersion.domain.automation.Goal
import com.histmersion.domain.research.Slug
import com.histmersion.domain.EnrichSource
import com.histmersion.util.jobs.JobRow
import com.histmersion.domain.automation.agents.AgentRepo
import com.histmersion.domain.ManagerPolicy
import com.histmersion.domain.automation.goals.BGoalLinkRow
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.domain.ManagerPolicy.Item

/** What the supervisor gives a [[ManagerKindDefinition]]: its managers' items, events and hints, the runs and approvals
  * they left, and the engine's mechanics of starting, retrying and settling an item. Every write is the one the
  * supervisor itself makes, with the same events.
  */
trait ManagerContext:
  def cfg: AgentsConfig
  def settings(m: ManagerInfo): ReviewSettings

  // ── the managers' items, events and hints ─────────────────────────────────────────────────────

  def event(
      id: ManagerLight.Id,
      item: Option[ManagerContext.ItemId],
      run: Option[Run.Id],
      feed: ManagerContext.EventFeed,
      text: ManagerContext.EventText
  ): Unit
  def lastEventOf(id: ManagerLight.Id, feed: ManagerContext.EventFeed): Option[ManagerEvent]
  def countEvents(id: ManagerLight.Id, feed: ManagerContext.EventFeed, since: Instant): RowCount
  def upsert(
      id: ManagerLight.Id,
      kind: ManagerContext.ItemKind,
      target: ManagerContext.ItemTarget,
      origin: ItemOrigin,
      priority: ManagerContext.Priority,
      reopen: ManagerContext.UpsertReopen,
      state: ItemState = ItemState()
  ): (ManagerContext.ItemId, Boolean)
  def item(itemId: ManagerContext.ItemId): Option[ManagerItem]
  def itemFor(
      id: ManagerLight.Id,
      kind: ManagerContext.ItemKind,
      target: ManagerContext.ItemTarget
  ): Option[ManagerItem]
  def hasItem(id: ManagerLight.Id, kind: ManagerContext.ItemKind, target: ManagerContext.ItemTarget): Boolean
  def restale(
      itemId: ManagerContext.ItemId,
      priority: ManagerContext.Priority,
      why: ManagerItem.Outcome
  ): Boolean
  def pending(id: ManagerLight.Id): RowCount
  def openKeys(id: ManagerLight.Id): List[(ManagerContext.ItemKind, ManagerContext.ItemTarget)]
  def evaluated(
      itemId: ManagerContext.ItemId,
      run: Option[Run.Id],
      status: ItemStatus,
      notBefore: Option[Instant],
      failures: Option[ManagerContext.FailureCount],
      state: ItemState,
      outcome: Option[ManagerContext.EvaluatedOutcome]
  ): Boolean
  def hints(id: ManagerLight.Id): List[ManagerHint]

  /** A manager's state (`active`, `paused`, …), if it exists. */
  def state(id: ManagerLight.Id): Option[ManagerLight.State]

  /** Tick soon (an item was added). */
  def nudge(): Unit

  /** The review selection's article queries (the review kind's, until research owns them). */
  def openMajorArticles(
      id: ManagerLight.Id,
      tags: List[GoalRow.Tags],
      goal: Option[Goal.Slug],
      limit: ManagerContext.Limit
  ): List[(Slug, RowCount)]
  def unreviewedArticles(
      id: ManagerLight.Id,
      tags: List[GoalRow.Tags],
      goal: Option[Goal.Slug],
      limit: ManagerContext.Limit
  ): List[Slug]
  def staleCandidates(
      id: ManagerLight.Id,
      tags: List[GoalRow.Tags],
      goal: Option[Goal.Slug],
      limit: ManagerContext.Limit
  ): List[(Slug, ManagerRepo.ProgressText, Option[ItemState.FilterHits], Option[ManagerContext.ItemId])]
  def extendCandidates(
      id: ManagerLight.Id,
      tags: List[GoalRow.Tags],
      goal: Option[Goal.Slug],
      limit: ManagerContext.Limit
  ): List[(Slug, Option[ManagerRepo.ProgressText])]
  def articleFacets(slug: Slug): (List[EnrichSource.Tag], List[Goal.Slug])

  // ── runs, steps and approvals ─────────────────────────────────────────────────────────────────

  /** The progress line a run left ("" when none). */
  def progress(run: Run.Id): JobRow.Progress

  /** The output of the last finished `step` of `run`, as recorded. */
  def stepOutput(run: Run.Id, step: AgentRepo.StepRecord.Name): Option[AgentRepo.StepRecord.Output]
  def approvalsOf(run: Run.Id, kind: Approval.Kind): List[ApprovalInfo]

  /** [[StepFacts]] from the approval of `kind` a run opened: none, open (the item waits for it), or decided. */
  def approvalFacts(run: Run.Id, kind: Approval.Kind, none: Item.Outcome): StepFacts
  def unsettledOn(target: ManagerContext.ItemTarget): Seq[RunInfo]
  def runsFor(p: Pipeline, target: ManagerContext.ItemTarget): Seq[RunInfo]
  def slugExists(slug: Slug): Boolean

  // ── goals ─────────────────────────────────────────────────────────────────────────────────────

  def parentOf(slug: Goal.Slug): Option[Goal.Slug]
  def linkGoal(goal: Goal.Slug, kind: LinkKind, ref: BGoalLinkRow.Ref, note: Option[BGoalLinkRow.Note]): Unit

  /** The references a goal links of `kind`. */
  def goalLinks(goal: Goal.Slug, kind: LinkKind): List[BGoalLinkRow.Ref]

  /** What is left today of the legal open-access ingests all watchers share (None: the autopilot is off). */
  def sharedIngestLeft(now: Instant): Option[RowCount]

  /** The autopilot gives a goal's new sub-goals their managers at its next tick. */
  def autopilotSoon(): Unit

  // ── the engine's mechanics ────────────────────────────────────────────────────────────────────

  /** Manager `id` of kind `k` exists (created paused, or active while the autopilot is on). */
  def ensure(k: Kind, id: ManagerLight.Id, goal: Option[Goal.Slug]): Boolean

  /** A review item for `slug` in review manager `id`, from a chain (priority 2): added, or a settled one re-opened. */
  def chainReview(
      id: ManagerLight.Id,
      slug: Slug,
      why: Problem,
      out: Builder[ManagerContext.TickLine, List[ManagerContext.TickLine]]
  ): Unit

  /** An approved `article-plan`'s compile items. */
  def applyPlan(a: ApprovalInfo, out: Builder[ManagerContext.TickLine, List[ManagerContext.TickLine]]): RowCount

  /** Settle a pending item without a run. */
  def settleNow(
      m: ManagerInfo,
      item: ManagerItem,
      status: ItemStatus,
      why: ManagerContext.SettleNowWhy
  ): Option[ManagerLight.Id]

  /** A failed run's item back in the queue after `notBefore`. */
  def retry(
      m: ManagerInfo,
      item: ManagerItem,
      run: Run.Id,
      notBefore: Instant,
      failures: ManagerContext.FailureCount,
      note: ManagerContext.RetryNote,
      out: Builder[ManagerContext.TickLine, List[ManagerContext.TickLine]]
  ): Unit

  /** Create, mark and submit one run of `item` (one transaction); `tweak` sets the options the request does not carry.
    */
  def start(
      m: ManagerInfo,
      item: ManagerItem,
      req: RunRequest,
      tweak: RunParams => RunParams,
      state: ItemState,
      what: ManagerContext.StartWhat,
      out: Builder[ManagerContext.TickLine, List[ManagerContext.TickLine]]
  ): Option[ManagerLight.Id]

  /** The one-run rule (infer-filter, extend, enrich, propose, compile, plan, sweeps) after a run settled. */
  def evaluateStep(
      m: ManagerInfo,
      s: ReviewSettings,
      item: ManagerItem,
      st: RunState,
      error: Option[Problem],
      now: Instant,
      out: Builder[ManagerContext.TickLine, List[ManagerContext.TickLine]],
      kind: ManagerKindDefinition
  ): Unit

object ManagerContext:
  type FailureCount = ManagerPolicy.Next.Retry.Failures
  val FailureCount: ManagerPolicy.Next.Retry.Failures.type = ManagerPolicy.Next.Retry.Failures
  type ItemId = Item.Id
  val ItemId: Item.Id.type = Item.Id
  type ItemKind = Item.Kind
  val ItemKind: Item.Kind.type = Item.Kind
  type ItemTarget = ManagerPolicy.Candidate.Target
  val ItemTarget: ManagerPolicy.Candidate.Target.type = ManagerPolicy.Candidate.Target
  type Limit = Limit.T
  object Limit extends Newtype[Int, Pure]
  type Priority = ManagerPolicy.Candidate.Priority
  val Priority: ManagerPolicy.Candidate.Priority.type = ManagerPolicy.Candidate.Priority
  type EventFeed = EventFeed.T
  object EventFeed extends Newtype[String, Pure]
  type EventText = EventText.T
  object EventText extends Newtype[String, Pure]
  type UpsertReopen = UpsertReopen.T
  object UpsertReopen extends Newtype[Boolean, Pure]
  type EvaluatedOutcome = Item.Outcome
  val EvaluatedOutcome: Item.Outcome.type = Item.Outcome
  type SettleNowWhy = Item.Outcome
  val SettleNowWhy: Item.Outcome.type = Item.Outcome
  type RetryNote = Item.Note
  val RetryNote: Item.Note.type = Item.Note
  type StartWhat = StartWhat.T
  object StartWhat extends Newtype[String, Pure]
  type TickLine = TickLine.T
  object TickLine extends Newtype[String, Pure]
