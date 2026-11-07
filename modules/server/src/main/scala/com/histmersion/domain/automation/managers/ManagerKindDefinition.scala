package com.histmersion.domain.automation.managers

import com.histmersion.api.automation.Agents.{ApprovalInfo, Pipeline, RunInfo, RunState}
import com.histmersion.domain.AutopilotPolicy
import com.histmersion.domain.automation.approvals.ApprovalChecks
import com.histmersion.api.automation.Autopilot.EngineStatus
import com.histmersion.api.automation.Managers
import com.histmersion.api.automation.Managers.{Kind, ManagerConfig, ManagerInfo, ManagerItem}
import com.histmersion.config.AgentsConfig
import com.histmersion.domain.ManagerDefaults
import com.histmersion.domain.ManagerPolicy.ReviewSettings
import com.histmersion.domain.ManagerPolicy.StepFacts

import java.time.Instant
import scala.collection.mutable.Builder
import com.histmersion.api.Api

/** A kind of manager a domain contributes (`review`, `enrich`, `seed`, `watch`): the items it drives, what a firing of
  * its cron and its selection put in its queue, the run each of its items starts, what a finished run left and what
  * follows it. The loop, the holds, the retries, the cron's timing, the settling of approvals and the hints are the
  * engine's ([[ManagerSupervisor]]), which a kind reaches through [[ManagerContext]].
  */
trait ManagerKindDefinition:
  def kind: Kind

  /** The item kinds it drives: an item is evaluated, followed up and started by the kind that lists its kind. */
  def items: List[com.histmersion.domain.automation.managers.ManagerKindDefinition.ItemsResult] = Managers.itemKinds(kind)

  /** The cron a new manager of this kind gets when a person names none (UTC); `None`: no cron. */
  def cron: Option[com.histmersion.domain.automation.managers.ManagerLight.Cron] = None

  /** A manager's settings when it saves none: the server's defaults for its kind. */
  def defaults(cfg: AgentsConfig, m: ManagerDefaults): ManagerConfig

  /** A manager's settings in effect: its saved config `c` over the defaults of its kind (by default the run limits,
    * failures and backoff the engine knows; a kind whose items have more to set reads them itself).
    */
  def settings(c: ManagerConfig, m: ManagerDefaults): ReviewSettings = ReviewSettings.forKind(io.scalaland.chimney.Transformer.derive[com.histmersion.api.automation.Manager.Kind, com.histmersion.domain.automation.Manager.Kind].transform(kind), c, m)

  /** The pipeline an item of `itemKind` runs. */
  def pipelineOf(itemKind: com.histmersion.domain.automation.managers.ManagerContext.ItemKind): Pipeline

  /** What a firing of a manager's cron re-opens, one phrase each (the cron event lists them). */
  def fire(c: ManagerContext, m: ManagerInfo, now: Instant): List[com.histmersion.domain.automation.managers.ManagerKindDefinition.FireResult]

  /** A manager's queue filled by its own selection, below the low water or when its cron fired (`force`). */
  def refill(
      c: ManagerContext,
      m: ManagerInfo,
      now: Instant,
      force: com.histmersion.domain.automation.managers.ManagerKindDefinition.RefillForce,
      out: Builder[com.histmersion.domain.automation.managers.ManagerKindDefinition.RefillOutPart1, List[com.histmersion.domain.automation.managers.ManagerKindDefinition.RefillOutPart2]]
  ): Unit =
    ()

  /** One of its items whose run settled (`st`): by default the one-run rule of [[ManagerContext.evaluateStep]] over
    * [[facts]].
    */
  def evaluate(
      c: ManagerContext,
      m: ManagerInfo,
      s: ReviewSettings,
      item: ManagerItem,
      st: RunState,
      error: Option[com.histmersion.domain.automation.managers.ManagerKindDefinition.EvaluateError],
      now: Instant,
      out: Builder[com.histmersion.domain.automation.managers.ManagerKindDefinition.EvaluateOutPart1, List[com.histmersion.domain.automation.managers.ManagerKindDefinition.EvaluateOutPart2]]
  ): Unit = c.evaluateStep(m, s, item, st, error, now, out, this)

  /** What a DONE run of one of its items left. */
  def facts(c: ManagerContext, item: ManagerItem, run: com.histmersion.domain.automation.Run.Id): StepFacts

  /** What follows one of its items once it is done. */
  def afterDone(
      c: ManagerContext,
      m: ManagerInfo,
      item: ManagerItem,
      run: Option[com.histmersion.domain.automation.Run.Id],
      out: Builder[com.histmersion.domain.automation.managers.ManagerKindDefinition.AfterDoneOutPart1, List[com.histmersion.domain.automation.managers.ManagerKindDefinition.AfterDoneOutPart2]]
  ): Unit = ()

  /** Start the next run of one of its items (`Some(manager id)` when it started), or settle it without one. */
  def dispatch(
      c: ManagerContext,
      m: ManagerInfo,
      s: ReviewSettings,
      item: ManagerItem,
      now: Instant,
      out: Builder[com.histmersion.domain.automation.managers.ManagerKindDefinition.DispatchOutPart1, List[com.histmersion.domain.automation.managers.ManagerKindDefinition.DispatchOutPart2]]
  ): Option[com.histmersion.domain.automation.managers.ManagerKindDefinition.DispatchResult]

  /** The work its domain does at every tick besides the items, after the holds and before the crons: each with the name
    * a failure is logged under. `draining` = nothing new may start.
    */
  def housework(now: Instant, draining: => Boolean): List[(com.histmersion.domain.automation.managers.ManagerKindDefinition.HouseworkResultPart1, () => List[com.histmersion.domain.automation.managers.ManagerKindDefinition.HouseworkResultPart2Result])] = Nil

  // ── what a person adds, and what the kind's domain adds to the engine's figures ──────────────

  /** The target an item of `itemKind` names when a person adds one to manager `m` (an article's slug, a source's
    * citekey, the manager's goal), checked; or why it cannot be added.
    */
  def target(m: ManagerInfo, itemKind: com.histmersion.domain.automation.managers.ManagerContext.ItemKind, raw: com.histmersion.domain.automation.managers.ManagerKindDefinition.TargetRaw): Either[Api.Error, com.histmersion.domain.automation.managers.ManagerKindDefinition.TargetResultPart2]

  /** A person added (or brought back) an item: what goes with it (the review kind: an infer-filter item first). */
  def added(c: ManagerContext, m: ManagerInfo, itemKind: com.histmersion.domain.automation.managers.ManagerContext.ItemKind, target: com.histmersion.domain.automation.managers.ManagerContext.ItemTarget, priority: com.histmersion.domain.automation.managers.ManagerContext.Priority): Unit = ()

  /** What a person's enqueue of this kind names: the targets to add and, for the others named, why they are skipped. */
  def expand(c: ManagerContext, raw: com.histmersion.domain.automation.managers.ManagerKindDefinition.ExpandRaw): Either[Api.Error, (List[com.histmersion.domain.automation.managers.ManagerKindDefinition.ExpandResultPart2Part1], List[com.histmersion.domain.automation.managers.ManagerKindDefinition.ExpandResultPart2Part2])] = Right((List(raw), Nil))

  /** Per manager of this kind: what waits in its backlog and the days it takes to drain (a watcher's ingest backlog).
    */
  def backlog(): Map[com.histmersion.domain.automation.managers.ManagerKindDefinition.BacklogResultPart1, (com.histmersion.domain.automation.managers.ManagerKindDefinition.BacklogResultPart2Part1, Option[com.histmersion.domain.automation.managers.ManagerKindDefinition.BacklogResultPart2Part2])] = Map.empty

  /** The autopilot's status with this kind's domain's part filled in. */
  def status(st: EngineStatus, now: Instant): EngineStatus = st

  /** What this kind's domain ingested today outside the managers' own events (the shared ingest cap counts it). */
  def ingestedToday(now: Instant): com.histmersion.domain.automation.managers.ManagerKindDefinition.IngestedTodayResult = 0

  /** The autopilot's checks of an approval this kind's items wait for (None: not one of its kinds). */
  def judge(
      c: ApprovalChecks,
      a: ApprovalInfo,
      run: Option[RunInfo],
      mgr: Option[ManagerInfo],
      now: Instant
  ): Option[AutopilotPolicy.Verdict] = None

  /** Whether a job of this kind's domain is in flight that a drain waits for. */
  def busy: Boolean = false

object ManagerKindDefinition:
  type ItemsResult = ItemsResult.T
  object ItemsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FireResult = FireResult.T
  object FireResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RefillForce = RefillForce.T
  object RefillForce extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RefillOutPart1 = RefillOutPart1.T
  object RefillOutPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RefillOutPart2 = RefillOutPart2.T
  object RefillOutPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EvaluateError = EvaluateError.T
  object EvaluateError extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EvaluateOutPart1 = EvaluateOutPart1.T
  object EvaluateOutPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EvaluateOutPart2 = EvaluateOutPart2.T
  object EvaluateOutPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AfterDoneOutPart1 = AfterDoneOutPart1.T
  object AfterDoneOutPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AfterDoneOutPart2 = AfterDoneOutPart2.T
  object AfterDoneOutPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DispatchResult = DispatchResult.T
  object DispatchResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DispatchOutPart1 = DispatchOutPart1.T
  object DispatchOutPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DispatchOutPart2 = DispatchOutPart2.T
  object DispatchOutPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HouseworkResultPart1 = HouseworkResultPart1.T
  object HouseworkResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HouseworkResultPart2Result = HouseworkResultPart2Result.T
  object HouseworkResultPart2Result extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TargetResultPart2 = TargetResultPart2.T
  object TargetResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TargetRaw = TargetRaw.T
  object TargetRaw extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ExpandResultPart2Part1 = ExpandResultPart2Part1.T
  object ExpandResultPart2Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ExpandResultPart2Part2 = ExpandResultPart2Part2.T
  object ExpandResultPart2Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ExpandRaw = ExpandRaw.T
  object ExpandRaw extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BacklogResultPart1 = BacklogResultPart1.T
  object BacklogResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BacklogResultPart2Part1 = BacklogResultPart2Part1.T
  object BacklogResultPart2Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BacklogResultPart2Part2 = BacklogResultPart2Part2.T
  object BacklogResultPart2Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IngestedTodayResult = IngestedTodayResult.T
  object IngestedTodayResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
