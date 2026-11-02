package com.histmersion.domain.automation.managers

import com.histmersion.api.automation.Agents.{ApprovalInfo, Pipeline, RunInfo, RunParams, RunRequest, RunState}
import com.histmersion.api.automation.GoalPlans.LinkKind
import com.histmersion.api.automation.Managers.*
import com.histmersion.config.AgentsConfig
import com.histmersion.domain.ManagerPolicy.{ReviewSettings, StepFacts}
import com.histmersion.values.ApprovalKind

import java.time.Instant
import scala.collection.mutable.Builder

/** What the supervisor gives a [[ManagerKindDefinition]]: its managers' items, events and hints, the runs and approvals
  * they left, and the engine's mechanics of starting, retrying and settling an item. Every write is the one the
  * supervisor itself makes, with the same events.
  */
trait ManagerContext:
  def cfg: AgentsConfig
  def settings(m: ManagerInfo): ReviewSettings

  // ── the managers' items, events and hints ─────────────────────────────────────────────────────

  def event(id: String, item: Option[Long], run: Option[String], feed: String, text: String): Unit
  def lastEventOf(id: String, feed: String): Option[ManagerEvent]
  def countEvents(id: String, feed: String, since: Instant): Int
  def upsert(
      id: String,
      kind: String,
      target: String,
      origin: ItemOrigin,
      priority: Int,
      reopen: Boolean,
      state: ItemState = ItemState()
  ): (Long, Boolean)
  def item(itemId: Long): Option[ManagerItem]
  def itemFor(id: String, kind: String, target: String): Option[ManagerItem]
  def hasItem(id: String, kind: String, target: String): Boolean
  def restale(itemId: Long, priority: Int, why: String): Boolean
  def pending(id: String): Int
  def openKeys(id: String): List[(String, String)]
  def evaluated(
      itemId: Long,
      run: Option[String],
      status: ItemStatus,
      notBefore: Option[Instant],
      failures: Option[Int],
      state: ItemState,
      outcome: Option[String]
  ): Boolean
  def hints(id: String): List[ManagerHint]

  /** A manager's state (`active`, `paused`, …), if it exists. */
  def state(id: String): Option[String]

  /** Tick soon (an item was added). */
  def nudge(): Unit

  /** The review selection's article queries (the review kind's, until research owns them). */
  def openMajorArticles(id: String, tags: List[String], goal: Option[String], limit: Int): List[(String, Int)]
  def unreviewedArticles(id: String, tags: List[String], goal: Option[String], limit: Int): List[String]
  def staleCandidates(
      id: String,
      tags: List[String],
      goal: Option[String],
      limit: Int
  ): List[(String, String, Option[Int], Option[Long])]
  def extendCandidates(id: String, tags: List[String], goal: Option[String], limit: Int): List[(String, Option[String])]
  def articleFacets(slug: String): (List[String], List[String])

  // ── runs, steps and approvals ─────────────────────────────────────────────────────────────────

  /** The progress line a run left ("" when none). */
  def progress(run: String): String

  /** The output of the last finished `step` of `run`, as recorded. */
  def stepOutput(run: String, step: String): Option[String]
  def approvalsOf(run: String, kind: ApprovalKind): List[ApprovalInfo]

  /** [[StepFacts]] from the approval of `kind` a run opened: none, open (the item waits for it), or decided. */
  def approvalFacts(run: String, kind: ApprovalKind, none: String): StepFacts
  def unsettledOn(target: String): Seq[RunInfo]
  def runsFor(p: Pipeline, target: String): Seq[RunInfo]
  def slugExists(slug: String): Boolean

  // ── goals ─────────────────────────────────────────────────────────────────────────────────────

  def parentOf(slug: String): Option[String]
  def linkGoal(goal: String, kind: LinkKind, ref: String, note: Option[String]): Unit

  /** The references a goal links of `kind`. */
  def goalLinks(goal: String, kind: LinkKind): List[String]

  /** What is left today of the legal open-access ingests all watchers share (None: the autopilot is off). */
  def sharedIngestLeft(now: Instant): Option[Int]

  /** The autopilot gives a goal's new sub-goals their managers at its next tick. */
  def autopilotSoon(): Unit

  // ── the engine's mechanics ────────────────────────────────────────────────────────────────────

  /** Manager `id` of kind `k` exists (created paused, or active while the autopilot is on). */
  def ensure(k: Kind, id: String, goal: Option[String]): Boolean

  /** A review item for `slug` in review manager `id`, from a chain (priority 2): added, or a settled one re-opened. */
  def chainReview(id: String, slug: String, why: String, out: Builder[String, List[String]]): Unit

  /** An approved `article-plan`'s compile items. */
  def applyPlan(a: ApprovalInfo, out: Builder[String, List[String]]): Int

  /** Settle a pending item without a run. */
  def settleNow(m: ManagerInfo, item: ManagerItem, status: ItemStatus, why: String): Option[String]

  /** A failed run's item back in the queue after `notBefore`. */
  def retry(
      m: ManagerInfo,
      item: ManagerItem,
      run: String,
      notBefore: Instant,
      failures: Int,
      note: String,
      out: Builder[String, List[String]]
  ): Unit

  /** Create, mark and submit one run of `item` (one transaction); `tweak` sets the options the request does not carry.
    */
  def start(
      m: ManagerInfo,
      item: ManagerItem,
      req: RunRequest,
      tweak: RunParams => RunParams,
      state: ItemState,
      what: String,
      out: Builder[String, List[String]]
  ): Option[String]

  /** The one-run rule (infer-filter, extend, enrich, propose, compile, plan, sweeps) after a run settled. */
  def evaluateStep(
      m: ManagerInfo,
      s: ReviewSettings,
      item: ManagerItem,
      st: RunState,
      error: Option[String],
      now: Instant,
      out: Builder[String, List[String]],
      kind: ManagerKindDefinition
  ): Unit
