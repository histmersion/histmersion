package com.histmersion.domain.automation.managers

import com.histmersion.api.automation.Agents.{JobKind, PauseReason}
import com.histmersion.api.automation.Managers.*
import com.histmersion.domain.automation.managers.ManagerRepo
import com.histmersion.domain.automation.AutomationState
import com.histmersion.util.database.Transactor
import com.histmersion.util.jobs.{JobRuns, JobRunState}
import hearth.kindlings.optics.syntax.*

import java.time.{Clock, Instant, ZoneOffset}
import java.util.concurrent.atomic.AtomicReference
import com.histmersion.domain.automation.Goal

/** The process managers as the engine's other features reach them: a manager and its settings, its items (added, held
  * and released for the focus), its events, and the few reads over every manager at once that the autopilot, the focus,
  * the scheduler and the digest make.
  */
trait ManagerService:
  def get(id: com.histmersion.domain.automation.managers.ManagerService.GetId): Option[ManagerInfo]

  /** Every manager, the oldest first. */
  def list(): List[ManagerInfo]

  /** A manager's pending items, the most urgent first. */
  def pending(managerId: com.histmersion.domain.automation.managers.ManagerService.PendingManagerId): List[ManagerItem]

  /** A manager's first `limit` items, the most urgent first: active, pending, held, then the rest; by priority, the
    * earliest not-before (none first), the latest change, the id.
    */
  def items(managerId: com.histmersion.domain.automation.managers.ManagerService.ItemsManagerId, limit: com.histmersion.domain.automation.managers.ManagerService.ItemsLimit): List[ManagerItem]

  /** Every manager without its counts, by id. */
  def light(): List[ManagerLight]

  /** A new manager; false when the id is taken. */
  def create(
      id: com.histmersion.domain.automation.managers.ManagerService.CreateId,
      kind: Kind,
      goal: Option[com.histmersion.domain.automation.managers.ManagerService.CreateGoal],
      state: State,
      config: ManagerConfig,
      cron: Option[com.histmersion.domain.automation.managers.ManagerService.CreateCron] = None,
      nextTick: Option[Instant] = None
  ): com.histmersion.domain.automation.managers.ManagerService.CreateResult
  def setState(id: com.histmersion.domain.automation.managers.ManagerService.SetStateId, state: State, pauseRuns: com.histmersion.domain.automation.managers.ManagerService.SetStatePauseRuns): com.histmersion.domain.automation.managers.ManagerService.SetStateResult

  /** Its settings and cron; `next` = when the cron fires next. */
  def setConfig(id: com.histmersion.domain.automation.managers.ManagerService.SetConfigId, config: ManagerConfig, cron: Option[com.histmersion.domain.automation.managers.ManagerService.SetConfigCron], next: Option[Instant]): com.histmersion.domain.automation.managers.ManagerService.SetConfigResult

  /** Add an item, or bring an existing one back (`reopen`: a settled one pending again; the priority raised): its id,
    * and whether it is new.
    */
  def upsert(
      managerId: com.histmersion.domain.automation.managers.ManagerService.UpsertManagerId,
      kind: com.histmersion.domain.automation.managers.ManagerService.UpsertKind,
      target: com.histmersion.domain.automation.managers.ManagerService.UpsertTarget,
      origin: ItemOrigin,
      priority: com.histmersion.domain.automation.managers.ManagerService.UpsertPriority,
      reopen: com.histmersion.domain.automation.managers.ManagerService.UpsertReopen,
      initial: ItemState = ItemState()
  ): (com.histmersion.domain.automation.managers.ManagerService.UpsertResultPart1, com.histmersion.domain.automation.managers.ManagerService.UpsertResultPart2)
  def item(id: com.histmersion.domain.automation.managers.ManagerService.ItemId): Option[ManagerItem]
  def itemFor(managerId: com.histmersion.domain.automation.managers.ManagerService.ItemForManagerId, kind: com.histmersion.domain.automation.managers.ManagerService.ItemForKind, target: com.histmersion.domain.automation.managers.ManagerService.ItemForTarget): Option[ManagerItem]

  /** The open items of any manager on `target`, the newest first. */
  def openFor(target: com.histmersion.domain.automation.managers.ManagerService.OpenForTarget): List[ManagerItem]

  /** A manager's open items as (kind, target). */
  def openKeys(managerId: com.histmersion.domain.automation.managers.ManagerService.OpenKeysManagerId): List[(com.histmersion.domain.automation.managers.ManagerService.OpenKeysResultPart1, com.histmersion.domain.automation.managers.ManagerService.OpenKeysResultPart2)]

  /** How many of a manager's items of these kinds are in these states (`focusHeld`: one the focus holds counts as
    * held).
    */
  def openOf(managerId: com.histmersion.domain.automation.managers.ManagerService.OpenOfManagerId, kinds: List[com.histmersion.domain.automation.managers.ManagerService.OpenOfKinds], statuses: List[com.histmersion.domain.automation.managers.ManagerService.OpenOfStatuses], focusHeld: com.histmersion.domain.automation.managers.ManagerService.OpenOfFocusHeld = true): com.histmersion.domain.automation.managers.ManagerService.OpenOfResult
  def event(managerId: com.histmersion.domain.automation.managers.ManagerService.EventManagerId, itemId: Option[com.histmersion.domain.automation.managers.ManagerService.EventItemId], runId: Option[com.histmersion.domain.automation.Run.Id], kind: com.histmersion.domain.automation.managers.ManagerService.EventKind, text: com.histmersion.domain.automation.managers.ManagerService.EventText): Unit

  /** A manager's newest event of `kind`. */
  def lastEventOf(managerId: com.histmersion.domain.automation.managers.ManagerService.LastEventOfManagerId, kind: com.histmersion.domain.automation.managers.ManagerService.LastEventOfKind): Option[ManagerEvent]

  /** Every manager's events of `kind` since `since`. */
  def countEventsAll(kind: com.histmersion.domain.automation.managers.ManagerService.CountEventsAllKind, since: Instant): com.histmersion.domain.automation.managers.ManagerService.CountEventsAllResult

  /** Per watcher: its ingest backlog's (pending, held) items. */
  def backlogCounts(): Map[com.histmersion.domain.automation.managers.ManagerService.BacklogCountsResultPart1, (com.histmersion.domain.automation.managers.ManagerService.BacklogCountsResultPart2Part1, com.histmersion.domain.automation.managers.ManagerService.BacklogCountsResultPart2Part2)]

  /** Every pending item of the ingest backlog of an active watcher, the most urgent first (priority, then the oldest).
    */
  def backlogPending(): List[ManagerItem]

  /** The open (pending, held, active) items of every watcher's ingest backlog, the oldest first. */
  def backlogOpen(): List[ManagerItem]

  /** The open backlog item of any watcher for `doi`, the oldest. */
  def backlogOpenFor(doi: com.histmersion.domain.library.Doi): Option[ManagerItem]

  /** A pending backlog item tried again later: one more attempt, its failures, not before `notBefore`, why; false = it
    * was not pending.
    */
  def backlogRetry(itemId: com.histmersion.domain.automation.managers.ManagerService.BacklogRetryItemId, failures: com.histmersion.domain.automation.managers.ManagerService.BacklogRetryFailures, notBefore: Instant, outcome: com.histmersion.domain.automation.managers.ManagerService.BacklogRetryOutcome): com.histmersion.domain.automation.managers.ManagerService.BacklogRetryResult

  /** Per manager: its events of `kind` since `since`. */
  def countEventsBy(kind: com.histmersion.domain.automation.managers.ManagerService.CountEventsByKind, since: Instant): Map[com.histmersion.domain.automation.managers.ManagerService.CountEventsByResultPart1, com.histmersion.domain.automation.managers.ManagerService.CountEventsByResultPart2]

  /** An item's state replaced while it is in one of `from`; false = it was not. */
  def setItemState(itemId: com.histmersion.domain.automation.managers.ManagerService.SetItemStateItemId, state: ItemState, from: List[ItemStatus]): com.histmersion.domain.automation.managers.ManagerService.SetItemStateResult

  /** The open acquisition items a watcher's sweep created, found by the run that picked their DOI. */
  def sweepAcquireItems(): List[SweepAcquireItem]

  /** Per manager: its open items, the items it settled since `since`, and its last event's time (the backlog not
    * counted).
    */
  def pace(since: Instant): Map[com.histmersion.domain.automation.managers.ManagerService.PaceResultPart1, (com.histmersion.domain.automation.managers.ManagerService.PaceResultPart2Part1, com.histmersion.domain.automation.managers.ManagerService.PaceResultPart2Part2, Option[com.histmersion.domain.automation.managers.ManagerService.PaceResultPart2Part3])]

  /** A person's (or a hint's) hold of a pending or held item; false = it was neither. */
  def hold(itemId: com.histmersion.domain.automation.managers.ManagerService.HoldItemId, h: Hold): com.histmersion.domain.automation.managers.ManagerService.HoldResult

  /** A held item pending again; false = it was not held. */
  def release(itemId: com.histmersion.domain.automation.managers.ManagerService.ReleaseItemId): com.histmersion.domain.automation.managers.ManagerService.ReleaseResult

  /** Settle an item that is in one of `from`; false = it was not. */
  def settle(itemId: com.histmersion.domain.automation.managers.ManagerService.SettleItemId, status: ItemStatus, outcome: com.histmersion.domain.automation.managers.ManagerService.SettleOutcome, from: List[ItemStatus]): com.histmersion.domain.automation.managers.ManagerService.SettleResult

  /** A pending item active with run `run`, one more attempt, `state` its state; false = it was not pending. */
  def dispatch(itemId: com.histmersion.domain.automation.managers.ManagerService.DispatchItemId, run: com.histmersion.domain.automation.managers.ManagerService.DispatchRun, state: ItemState): com.histmersion.domain.automation.managers.ManagerService.DispatchResult

  /** Hold a pending item for the focus; false = it was not pending. */
  def focusHold(itemId: com.histmersion.domain.automation.managers.ManagerService.FocusHoldItemId, h: Hold): com.histmersion.domain.automation.managers.ManagerService.FocusHoldResult

  /** Release the focus's holds among `ids`; how many. */
  def focusRelease(ids: List[com.histmersion.domain.automation.managers.ManagerService.FocusReleaseIds]): com.histmersion.domain.automation.managers.ManagerService.FocusReleaseResult

  /** Release every focus hold; how many. */
  def focusReleaseAll(): com.histmersion.domain.automation.managers.ManagerService.FocusReleaseAllResult
  def focusHeldCount(): com.histmersion.domain.automation.managers.ManagerService.FocusHeldCountResult

  /** Items that wait for a person and hold no place. */
  def forPerson(): com.histmersion.domain.automation.managers.ManagerService.ForPersonResult

  /** Per goal: when it last progressed (an ISO instant). */
  def lastProgress(): Map[com.histmersion.domain.automation.managers.ManagerService.LastProgressResultPart1, com.histmersion.domain.automation.managers.ManagerService.LastProgressResultPart2]

  /** Every agent run that occupies a place: its pipeline and its manager's goal. */
  def occupyingRuns(): List[(com.histmersion.domain.automation.managers.ManagerService.OccupyingRunsResultPart1, Option[com.histmersion.domain.automation.managers.ManagerService.OccupyingRunsResultPart2])]

  /** Per goal: its items a person holds. */
  def personHeldByGoal(): Map[com.histmersion.domain.automation.managers.ManagerService.PersonHeldByGoalResultPart1, com.histmersion.domain.automation.managers.ManagerService.PersonHeldByGoalResultPart2]

  /** The item kinds of the managers' last `n` runs, newest first. */
  def recentKinds(n: com.histmersion.domain.automation.managers.ManagerService.RecentKindsN): List[com.histmersion.domain.automation.managers.ManagerService.RecentKindsResult]

  /** A manager's runs that occupy a place. */
  def running(id: com.histmersion.domain.automation.managers.ManagerService.RunningId): com.histmersion.domain.automation.managers.ManagerService.RunningResult

  /** Per goal: its items whose run is queued or running. */
  def runningByGoal(): Map[com.histmersion.domain.automation.managers.ManagerService.RunningByGoalResultPart1, com.histmersion.domain.automation.managers.ManagerService.RunningByGoalResultPart2]

  /** Per goal: its watcher's settled acquisition sweep's outcome. */
  def sweepOutcomes(): Map[com.histmersion.domain.automation.managers.ManagerService.SweepOutcomesResultPart1, com.histmersion.domain.automation.managers.ManagerService.SweepOutcomesResultPart2]

object ManagerService:
  type GetId = GetId.T
  object GetId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PendingManagerId = PendingManagerId.T
  object PendingManagerId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ItemsManagerId = ItemsManagerId.T
  object ItemsManagerId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ItemsLimit = ItemsLimit.T
  object ItemsLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CreateResult = CreateResult.T
  object CreateResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CreateId = CreateId.T
  object CreateId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CreateGoal = CreateGoal.T
  object CreateGoal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CreateCron = CreateCron.T
  object CreateCron extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SetStateResult = SetStateResult.T
  object SetStateResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SetStateId = SetStateId.T
  object SetStateId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SetStatePauseRuns = SetStatePauseRuns.T
  object SetStatePauseRuns extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SetConfigResult = SetConfigResult.T
  object SetConfigResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SetConfigId = SetConfigId.T
  object SetConfigId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SetConfigCron = SetConfigCron.T
  object SetConfigCron extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UpsertResultPart1 = UpsertResultPart1.T
  object UpsertResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UpsertResultPart2 = UpsertResultPart2.T
  object UpsertResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UpsertManagerId = UpsertManagerId.T
  object UpsertManagerId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UpsertKind = UpsertKind.T
  object UpsertKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UpsertTarget = UpsertTarget.T
  object UpsertTarget extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UpsertPriority = UpsertPriority.T
  object UpsertPriority extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UpsertReopen = UpsertReopen.T
  object UpsertReopen extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ItemId = ItemId.T
  object ItemId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ItemForManagerId = ItemForManagerId.T
  object ItemForManagerId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ItemForKind = ItemForKind.T
  object ItemForKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ItemForTarget = ItemForTarget.T
  object ItemForTarget extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OpenForTarget = OpenForTarget.T
  object OpenForTarget extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OpenKeysResultPart1 = OpenKeysResultPart1.T
  object OpenKeysResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OpenKeysResultPart2 = OpenKeysResultPart2.T
  object OpenKeysResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OpenKeysManagerId = OpenKeysManagerId.T
  object OpenKeysManagerId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OpenOfResult = OpenOfResult.T
  object OpenOfResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OpenOfManagerId = OpenOfManagerId.T
  object OpenOfManagerId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OpenOfKinds = OpenOfKinds.T
  object OpenOfKinds extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OpenOfStatuses = OpenOfStatuses.T
  object OpenOfStatuses extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OpenOfFocusHeld = OpenOfFocusHeld.T
  object OpenOfFocusHeld extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EventManagerId = EventManagerId.T
  object EventManagerId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EventItemId = EventItemId.T
  object EventItemId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EventKind = EventKind.T
  object EventKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EventText = EventText.T
  object EventText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LastEventOfManagerId = LastEventOfManagerId.T
  object LastEventOfManagerId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LastEventOfKind = LastEventOfKind.T
  object LastEventOfKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CountEventsAllResult = CountEventsAllResult.T
  object CountEventsAllResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CountEventsAllKind = CountEventsAllKind.T
  object CountEventsAllKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BacklogCountsResultPart1 = BacklogCountsResultPart1.T
  object BacklogCountsResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BacklogCountsResultPart2Part1 = BacklogCountsResultPart2Part1.T
  object BacklogCountsResultPart2Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BacklogCountsResultPart2Part2 = BacklogCountsResultPart2Part2.T
  object BacklogCountsResultPart2Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BacklogRetryResult = BacklogRetryResult.T
  object BacklogRetryResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BacklogRetryItemId = BacklogRetryItemId.T
  object BacklogRetryItemId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BacklogRetryFailures = BacklogRetryFailures.T
  object BacklogRetryFailures extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BacklogRetryOutcome = BacklogRetryOutcome.T
  object BacklogRetryOutcome extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CountEventsByResultPart1 = CountEventsByResultPart1.T
  object CountEventsByResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CountEventsByResultPart2 = CountEventsByResultPart2.T
  object CountEventsByResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CountEventsByKind = CountEventsByKind.T
  object CountEventsByKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SetItemStateResult = SetItemStateResult.T
  object SetItemStateResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SetItemStateItemId = SetItemStateItemId.T
  object SetItemStateItemId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PaceResultPart1 = PaceResultPart1.T
  object PaceResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PaceResultPart2Part1 = PaceResultPart2Part1.T
  object PaceResultPart2Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PaceResultPart2Part2 = PaceResultPart2Part2.T
  object PaceResultPart2Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PaceResultPart2Part3 = PaceResultPart2Part3.T
  object PaceResultPart2Part3 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HoldResult = HoldResult.T
  object HoldResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HoldItemId = HoldItemId.T
  object HoldItemId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReleaseResult = ReleaseResult.T
  object ReleaseResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReleaseItemId = ReleaseItemId.T
  object ReleaseItemId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SettleResult = SettleResult.T
  object SettleResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SettleItemId = SettleItemId.T
  object SettleItemId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SettleOutcome = SettleOutcome.T
  object SettleOutcome extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DispatchResult = DispatchResult.T
  object DispatchResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DispatchItemId = DispatchItemId.T
  object DispatchItemId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DispatchRun = DispatchRun.T
  object DispatchRun extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FocusHoldResult = FocusHoldResult.T
  object FocusHoldResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FocusHoldItemId = FocusHoldItemId.T
  object FocusHoldItemId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FocusReleaseResult = FocusReleaseResult.T
  object FocusReleaseResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FocusReleaseIds = FocusReleaseIds.T
  object FocusReleaseIds extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FocusReleaseAllResult = FocusReleaseAllResult.T
  object FocusReleaseAllResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FocusHeldCountResult = FocusHeldCountResult.T
  object FocusHeldCountResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ForPersonResult = ForPersonResult.T
  object ForPersonResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LastProgressResultPart1 = LastProgressResultPart1.T
  object LastProgressResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LastProgressResultPart2 = LastProgressResultPart2.T
  object LastProgressResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OccupyingRunsResultPart1 = OccupyingRunsResultPart1.T
  object OccupyingRunsResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OccupyingRunsResultPart2 = OccupyingRunsResultPart2.T
  object OccupyingRunsResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PersonHeldByGoalResultPart1 = PersonHeldByGoalResultPart1.T
  object PersonHeldByGoalResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PersonHeldByGoalResultPart2 = PersonHeldByGoalResultPart2.T
  object PersonHeldByGoalResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RecentKindsResult = RecentKindsResult.T
  object RecentKindsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RecentKindsN = RecentKindsN.T
  object RecentKindsN extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RunningResult = RunningResult.T
  object RunningResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RunningId = RunningId.T
  object RunningId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RunningByGoalResultPart1 = RunningByGoalResultPart1.T
  object RunningByGoalResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RunningByGoalResultPart2 = RunningByGoalResultPart2.T
  object RunningByGoalResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SweepOutcomesResultPart1 = SweepOutcomesResultPart1.T
  object SweepOutcomesResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SweepOutcomesResultPart2 = SweepOutcomesResultPart2.T
  object SweepOutcomesResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def apply(db: Transactor): ManagerService = Implementation(db)

  final private class Implementation(db: Transactor) extends ManagerService:
    def get(id: com.histmersion.domain.automation.managers.ManagerService.Implementation.GetId): Option[ManagerInfo] = db.read(ManagerRepo.get(id))
    def list(): List[ManagerInfo] = db.read(ManagerRepo.list())
    def pending(managerId: com.histmersion.domain.automation.managers.ManagerService.Implementation.PendingManagerId): List[ManagerItem] = db.read(ManagerRepo.pending(managerId))
    def items(managerId: com.histmersion.domain.automation.managers.ManagerService.Implementation.ItemsManagerId, limit: com.histmersion.domain.automation.managers.ManagerService.Implementation.ItemsLimit): List[ManagerItem] =
      db.read(ManagerRepo.itemPage(managerId, None, 0, limit))._1
    def light(): List[ManagerLight] = db.read(ManagerRepo.light())
    def create(
        id: com.histmersion.domain.automation.managers.ManagerService.Implementation.CreateId,
        kind: Kind,
        goal: Option[com.histmersion.domain.automation.managers.ManagerService.Implementation.CreateGoal],
        state: State,
        config: ManagerConfig,
        cron: Option[com.histmersion.domain.automation.managers.ManagerService.Implementation.CreateCron],
        nextTick: Option[Instant]
    ): com.histmersion.domain.automation.managers.ManagerService.Implementation.CreateResult = db.transact(ManagerRepo.create(id, kind, goal, state, config, cron, nextTick))
    def setState(id: com.histmersion.domain.automation.managers.ManagerService.Implementation.SetStateId, state: State, pauseRuns: com.histmersion.domain.automation.managers.ManagerService.Implementation.SetStatePauseRuns): com.histmersion.domain.automation.managers.ManagerService.Implementation.SetStateResult =
      db.transact(ManagerRepo.setState(id, state, pauseRuns))
    def setConfig(id: com.histmersion.domain.automation.managers.ManagerService.Implementation.SetConfigId, config: ManagerConfig, cron: Option[com.histmersion.domain.automation.managers.ManagerService.Implementation.SetConfigCron], next: Option[Instant]): com.histmersion.domain.automation.managers.ManagerService.Implementation.SetConfigResult =
      db.transact(ManagerRepo.setConfig(id, config, cron, next))
    def upsert(
        managerId: com.histmersion.domain.automation.managers.ManagerService.Implementation.UpsertManagerId,
        kind: com.histmersion.domain.automation.managers.ManagerService.Implementation.UpsertKind,
        target: com.histmersion.domain.automation.managers.ManagerService.Implementation.UpsertTarget,
        origin: ItemOrigin,
        priority: com.histmersion.domain.automation.managers.ManagerService.Implementation.UpsertPriority,
        reopen: com.histmersion.domain.automation.managers.ManagerService.Implementation.UpsertReopen,
        initial: ItemState
    ): (com.histmersion.domain.automation.managers.ManagerService.Implementation.UpsertResultPart1, com.histmersion.domain.automation.managers.ManagerService.Implementation.UpsertResultPart2) = db.transact(ManagerRepo.upsert(managerId, kind, target, origin, priority, reopen, initial))
    def item(id: com.histmersion.domain.automation.managers.ManagerService.Implementation.ItemId): Option[ManagerItem] = db.read(ManagerRepo.item(id))
    def itemFor(managerId: com.histmersion.domain.automation.managers.ManagerService.Implementation.ItemForManagerId, kind: com.histmersion.domain.automation.managers.ManagerService.Implementation.ItemForKind, target: com.histmersion.domain.automation.managers.ManagerService.Implementation.ItemForTarget): Option[ManagerItem] =
      db.read(ManagerRepo.itemFor(managerId, kind, target))
    def openFor(target: com.histmersion.domain.automation.managers.ManagerService.Implementation.OpenForTarget): List[ManagerItem] = db.read(ManagerRepo.openFor(target))
    def openKeys(managerId: com.histmersion.domain.automation.managers.ManagerService.Implementation.OpenKeysManagerId): List[(com.histmersion.domain.automation.managers.ManagerService.Implementation.OpenKeysResultPart1, com.histmersion.domain.automation.managers.ManagerService.Implementation.OpenKeysResultPart2)] = db.read(ManagerRepo.openKeys(managerId))
    def openOf(managerId: com.histmersion.domain.automation.managers.ManagerService.Implementation.OpenOfManagerId, kinds: List[com.histmersion.domain.automation.managers.ManagerService.Implementation.OpenOfKinds], statuses: List[com.histmersion.domain.automation.managers.ManagerService.Implementation.OpenOfStatuses], focusHeld: com.histmersion.domain.automation.managers.ManagerService.Implementation.OpenOfFocusHeld): com.histmersion.domain.automation.managers.ManagerService.Implementation.OpenOfResult =
      db.read(ManagerRepo.openOf(managerId, kinds, statuses, focusHeld))
    def event(managerId: com.histmersion.domain.automation.managers.ManagerService.Implementation.EventManagerId, itemId: Option[com.histmersion.domain.automation.managers.ManagerService.Implementation.EventItemId], runId: Option[com.histmersion.domain.automation.Run.Id], kind: com.histmersion.domain.automation.managers.ManagerService.Implementation.EventKind, text: com.histmersion.domain.automation.managers.ManagerService.Implementation.EventText): Unit =
      db.transact(ManagerRepo.event(managerId, itemId, runId, kind, text))
    def lastEventOf(managerId: com.histmersion.domain.automation.managers.ManagerService.Implementation.LastEventOfManagerId, kind: com.histmersion.domain.automation.managers.ManagerService.Implementation.LastEventOfKind): Option[ManagerEvent] =
      db.read(ManagerRepo.lastEventOf(managerId, kind))
    def countEventsAll(kind: com.histmersion.domain.automation.managers.ManagerService.Implementation.CountEventsAllKind, since: Instant): com.histmersion.domain.automation.managers.ManagerService.Implementation.CountEventsAllResult = db.read(ManagerRepo.countEventsAll(kind, since))
    def backlogCounts(): Map[com.histmersion.domain.automation.managers.ManagerService.Implementation.BacklogCountsResultPart1, (com.histmersion.domain.automation.managers.ManagerService.Implementation.BacklogCountsResultPart2Part1, com.histmersion.domain.automation.managers.ManagerService.Implementation.BacklogCountsResultPart2Part2)] = db.read(ManagerRepo.backlogCounts())
    def backlogPending(): List[ManagerItem] = db.read(ManagerRepo.backlogPendingIn())
    def backlogOpen(): List[ManagerItem] = db.read(ManagerRepo.backlogOpenIn())
    def backlogOpenFor(doi: com.histmersion.domain.library.Doi): Option[ManagerItem] = db.read(ManagerRepo.backlogOpenForIn(doi))
    def backlogRetry(itemId: com.histmersion.domain.automation.managers.ManagerService.Implementation.BacklogRetryItemId, failures: com.histmersion.domain.automation.managers.ManagerService.Implementation.BacklogRetryFailures, notBefore: Instant, outcome: com.histmersion.domain.automation.managers.ManagerService.Implementation.BacklogRetryOutcome): com.histmersion.domain.automation.managers.ManagerService.Implementation.BacklogRetryResult =
      db.transact(ManagerRepo.backlogRetryIn(itemId, failures, notBefore, outcome))
    def countEventsBy(kind: com.histmersion.domain.automation.managers.ManagerService.Implementation.CountEventsByKind, since: Instant): Map[com.histmersion.domain.automation.managers.ManagerService.Implementation.CountEventsByResultPart1, com.histmersion.domain.automation.managers.ManagerService.Implementation.CountEventsByResultPart2] =
      db.read(ManagerRepo.countEventsByIn(kind, since))
    def setItemState(itemId: com.histmersion.domain.automation.managers.ManagerService.Implementation.SetItemStateItemId, state: ItemState, from: List[ItemStatus]): com.histmersion.domain.automation.managers.ManagerService.Implementation.SetItemStateResult =
      db.transact(ManagerRepo.setItemStateIn(itemId, state, from))
    def sweepAcquireItems(): List[SweepAcquireItem] = db.read(ManagerRepo.sweepAcquireItemsIn())
    def pace(since: Instant): Map[com.histmersion.domain.automation.managers.ManagerService.Implementation.PaceResultPart1, (com.histmersion.domain.automation.managers.ManagerService.Implementation.PaceResultPart2Part1, com.histmersion.domain.automation.managers.ManagerService.Implementation.PaceResultPart2Part2, Option[com.histmersion.domain.automation.managers.ManagerService.Implementation.PaceResultPart2Part3])] = db.read(ManagerRepo.pace(since))
    def hold(itemId: com.histmersion.domain.automation.managers.ManagerService.Implementation.HoldItemId, h: Hold): com.histmersion.domain.automation.managers.ManagerService.Implementation.HoldResult = db.transact(ManagerRepo.hold(itemId, h))
    def release(itemId: com.histmersion.domain.automation.managers.ManagerService.Implementation.ReleaseItemId): com.histmersion.domain.automation.managers.ManagerService.Implementation.ReleaseResult = db.transact(ManagerRepo.release(itemId))
    def settle(itemId: com.histmersion.domain.automation.managers.ManagerService.Implementation.SettleItemId, status: ItemStatus, outcome: com.histmersion.domain.automation.managers.ManagerService.Implementation.SettleOutcome, from: List[ItemStatus]): com.histmersion.domain.automation.managers.ManagerService.Implementation.SettleResult =
      db.transact(ManagerRepo.settle(itemId, status, outcome, from))
    def dispatch(itemId: com.histmersion.domain.automation.managers.ManagerService.Implementation.DispatchItemId, run: com.histmersion.domain.automation.managers.ManagerService.Implementation.DispatchRun, state: ItemState): com.histmersion.domain.automation.managers.ManagerService.Implementation.DispatchResult =
      db.transact(ManagerRepo.dispatchIn(itemId, run, state))
    def focusHold(itemId: com.histmersion.domain.automation.managers.ManagerService.Implementation.FocusHoldItemId, h: Hold): com.histmersion.domain.automation.managers.ManagerService.Implementation.FocusHoldResult = db.transact(ManagerRepo.focusHold(itemId, h))
    def focusRelease(ids: List[com.histmersion.domain.automation.managers.ManagerService.Implementation.FocusReleaseIds]): com.histmersion.domain.automation.managers.ManagerService.Implementation.FocusReleaseResult = db.transact(ManagerRepo.focusRelease(ids))
    def focusReleaseAll(): com.histmersion.domain.automation.managers.ManagerService.Implementation.FocusReleaseAllResult = db.transact(ManagerRepo.focusReleaseAll())
    def focusHeldCount(): com.histmersion.domain.automation.managers.ManagerService.Implementation.FocusHeldCountResult = db.read(ManagerRepo.focusHeldCount())
    def forPerson(): com.histmersion.domain.automation.managers.ManagerService.Implementation.ForPersonResult = db.read(ManagerRepo.forPerson())
    def lastProgress(): Map[com.histmersion.domain.automation.managers.ManagerService.Implementation.LastProgressResultPart1, com.histmersion.domain.automation.managers.ManagerService.Implementation.LastProgressResultPart2] = db.read(ManagerRepo.lastProgress())
    def occupyingRuns(): List[(com.histmersion.domain.automation.managers.ManagerService.Implementation.OccupyingRunsResultPart1, Option[com.histmersion.domain.automation.managers.ManagerService.Implementation.OccupyingRunsResultPart2])] = db.read(ManagerRepo.occupyingRuns())
    def personHeldByGoal(): Map[com.histmersion.domain.automation.managers.ManagerService.Implementation.PersonHeldByGoalResultPart1, com.histmersion.domain.automation.managers.ManagerService.Implementation.PersonHeldByGoalResultPart2] = db.read(ManagerRepo.personHeldByGoal())
    def recentKinds(n: com.histmersion.domain.automation.managers.ManagerService.Implementation.RecentKindsN): List[com.histmersion.domain.automation.managers.ManagerService.Implementation.RecentKindsResult] = db.read(ManagerRepo.recentKinds(n))
    def running(id: com.histmersion.domain.automation.managers.ManagerService.Implementation.RunningId): com.histmersion.domain.automation.managers.ManagerService.Implementation.RunningResult = db.read(ManagerRepo.running(id))
    def runningByGoal(): Map[com.histmersion.domain.automation.managers.ManagerService.Implementation.RunningByGoalResultPart1, com.histmersion.domain.automation.managers.ManagerService.Implementation.RunningByGoalResultPart2] = db.read(ManagerRepo.runningByGoal())
    def sweepOutcomes(): Map[com.histmersion.domain.automation.managers.ManagerService.Implementation.SweepOutcomesResultPart1, com.histmersion.domain.automation.managers.ManagerService.Implementation.SweepOutcomesResultPart2] = db.read(ManagerRepo.sweepOutcomes())

  object Implementation:
    type GetId = GetId.T
    object GetId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PendingManagerId = PendingManagerId.T
    object PendingManagerId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ItemsManagerId = ItemsManagerId.T
    object ItemsManagerId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ItemsLimit = ItemsLimit.T
    object ItemsLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CreateResult = CreateResult.T
    object CreateResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CreateId = CreateId.T
    object CreateId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CreateGoal = CreateGoal.T
    object CreateGoal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CreateCron = CreateCron.T
    object CreateCron extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SetStateResult = SetStateResult.T
    object SetStateResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SetStateId = SetStateId.T
    object SetStateId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SetStatePauseRuns = SetStatePauseRuns.T
    object SetStatePauseRuns extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SetConfigResult = SetConfigResult.T
    object SetConfigResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SetConfigId = SetConfigId.T
    object SetConfigId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SetConfigCron = SetConfigCron.T
    object SetConfigCron extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type UpsertResultPart1 = UpsertResultPart1.T
    object UpsertResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type UpsertResultPart2 = UpsertResultPart2.T
    object UpsertResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type UpsertManagerId = UpsertManagerId.T
    object UpsertManagerId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type UpsertKind = UpsertKind.T
    object UpsertKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type UpsertTarget = UpsertTarget.T
    object UpsertTarget extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type UpsertPriority = UpsertPriority.T
    object UpsertPriority extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type UpsertReopen = UpsertReopen.T
    object UpsertReopen extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ItemId = ItemId.T
    object ItemId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ItemForManagerId = ItemForManagerId.T
    object ItemForManagerId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ItemForKind = ItemForKind.T
    object ItemForKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ItemForTarget = ItemForTarget.T
    object ItemForTarget extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OpenForTarget = OpenForTarget.T
    object OpenForTarget extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OpenKeysResultPart1 = OpenKeysResultPart1.T
    object OpenKeysResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OpenKeysResultPart2 = OpenKeysResultPart2.T
    object OpenKeysResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OpenKeysManagerId = OpenKeysManagerId.T
    object OpenKeysManagerId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OpenOfResult = OpenOfResult.T
    object OpenOfResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OpenOfManagerId = OpenOfManagerId.T
    object OpenOfManagerId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OpenOfKinds = OpenOfKinds.T
    object OpenOfKinds extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OpenOfStatuses = OpenOfStatuses.T
    object OpenOfStatuses extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OpenOfFocusHeld = OpenOfFocusHeld.T
    object OpenOfFocusHeld extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type EventManagerId = EventManagerId.T
    object EventManagerId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type EventItemId = EventItemId.T
    object EventItemId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type EventKind = EventKind.T
    object EventKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type EventText = EventText.T
    object EventText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LastEventOfManagerId = LastEventOfManagerId.T
    object LastEventOfManagerId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LastEventOfKind = LastEventOfKind.T
    object LastEventOfKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CountEventsAllResult = CountEventsAllResult.T
    object CountEventsAllResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CountEventsAllKind = CountEventsAllKind.T
    object CountEventsAllKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type BacklogCountsResultPart1 = BacklogCountsResultPart1.T
    object BacklogCountsResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type BacklogCountsResultPart2Part1 = BacklogCountsResultPart2Part1.T
    object BacklogCountsResultPart2Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type BacklogCountsResultPart2Part2 = BacklogCountsResultPart2Part2.T
    object BacklogCountsResultPart2Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type BacklogRetryResult = BacklogRetryResult.T
    object BacklogRetryResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type BacklogRetryItemId = BacklogRetryItemId.T
    object BacklogRetryItemId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type BacklogRetryFailures = BacklogRetryFailures.T
    object BacklogRetryFailures extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type BacklogRetryOutcome = BacklogRetryOutcome.T
    object BacklogRetryOutcome extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CountEventsByResultPart1 = CountEventsByResultPart1.T
    object CountEventsByResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CountEventsByResultPart2 = CountEventsByResultPart2.T
    object CountEventsByResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CountEventsByKind = CountEventsByKind.T
    object CountEventsByKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SetItemStateResult = SetItemStateResult.T
    object SetItemStateResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SetItemStateItemId = SetItemStateItemId.T
    object SetItemStateItemId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PaceResultPart1 = PaceResultPart1.T
    object PaceResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PaceResultPart2Part1 = PaceResultPart2Part1.T
    object PaceResultPart2Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PaceResultPart2Part2 = PaceResultPart2Part2.T
    object PaceResultPart2Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PaceResultPart2Part3 = PaceResultPart2Part3.T
    object PaceResultPart2Part3 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type HoldResult = HoldResult.T
    object HoldResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type HoldItemId = HoldItemId.T
    object HoldItemId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ReleaseResult = ReleaseResult.T
    object ReleaseResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ReleaseItemId = ReleaseItemId.T
    object ReleaseItemId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SettleResult = SettleResult.T
    object SettleResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SettleItemId = SettleItemId.T
    object SettleItemId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SettleOutcome = SettleOutcome.T
    object SettleOutcome extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type DispatchResult = DispatchResult.T
    object DispatchResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type DispatchItemId = DispatchItemId.T
    object DispatchItemId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type DispatchRun = DispatchRun.T
    object DispatchRun extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FocusHoldResult = FocusHoldResult.T
    object FocusHoldResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FocusHoldItemId = FocusHoldItemId.T
    object FocusHoldItemId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FocusReleaseResult = FocusReleaseResult.T
    object FocusReleaseResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FocusReleaseIds = FocusReleaseIds.T
    object FocusReleaseIds extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FocusReleaseAllResult = FocusReleaseAllResult.T
    object FocusReleaseAllResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FocusHeldCountResult = FocusHeldCountResult.T
    object FocusHeldCountResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ForPersonResult = ForPersonResult.T
    object ForPersonResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LastProgressResultPart1 = LastProgressResultPart1.T
    object LastProgressResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LastProgressResultPart2 = LastProgressResultPart2.T
    object LastProgressResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OccupyingRunsResultPart1 = OccupyingRunsResultPart1.T
    object OccupyingRunsResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OccupyingRunsResultPart2 = OccupyingRunsResultPart2.T
    object OccupyingRunsResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PersonHeldByGoalResultPart1 = PersonHeldByGoalResultPart1.T
    object PersonHeldByGoalResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PersonHeldByGoalResultPart2 = PersonHeldByGoalResultPart2.T
    object PersonHeldByGoalResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RecentKindsResult = RecentKindsResult.T
    object RecentKindsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RecentKindsN = RecentKindsN.T
    object RecentKindsN extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RunningResult = RunningResult.T
    object RunningResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RunningId = RunningId.T
    object RunningId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RunningByGoalResultPart1 = RunningByGoalResultPart1.T
    object RunningByGoalResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RunningByGoalResultPart2 = RunningByGoalResultPart2.T
    object RunningByGoalResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SweepOutcomesResultPart1 = SweepOutcomesResultPart1.T
    object SweepOutcomesResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SweepOutcomesResultPart2 = SweepOutcomesResultPart2.T
    object SweepOutcomesResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** The managers in memory, with the runs they dispatched (the jobs area's in-memory runs) and the shared state's
    * steps and goal links; `clock` stands in for the database's `now()`.
    */
  final class InMemory(state: AtomicReference[AutomationState], jobs: JobRuns.InMemory, clock: Clock)
      extends ManagerService:
    private def now: Instant = Instant.now(clock)
    private def text(at: Instant): com.histmersion.domain.automation.managers.ManagerService.InMemory.TextResult = AutomationState.text(at)
    private def managers: Vector[ManagerRecord] = state.get.managers
    private def items: Vector[ManagerItemRecord] = state.get.items
    private def events: Vector[ManagerEventRecord] = state.get.events
    private def runs: List[JobRunState] = jobs.all.filter(_.row.kind == JobKind)
    private def manager(id: com.histmersion.domain.automation.managers.ManagerService.InMemory.ManagerId): Option[ManagerRecord] = managers.find(_.id == id)
    private val Open = Set("pending", "held", "active")
    private val Settled = Set("done", "stopped", "failed", "cancelled")
    private def focusHeld(i: ManagerItemRecord): com.histmersion.domain.automation.managers.ManagerService.InMemory.FocusHeldResult = i.status == "held" && i.hold.exists(_.focus.isDefined)
    private def personHeld(i: ManagerItemRecord): com.histmersion.domain.automation.managers.ManagerService.InMemory.PersonHeldResult =
      i.status == "held" && i.hold.forall(h => h.focus.isEmpty && h.untilApproval.isEmpty)
    private def occupying(r: JobRunState): com.histmersion.domain.automation.managers.ManagerService.InMemory.OccupyingResult =
      r.row.status == "queued" || r.row.status == "running" ||
        (r.row.status == "paused" && (r.resumeAt.isDefined || r.error.exists(e =>
          e.startsWith(s"${PauseReason.Allowance.wire}:") || e.startsWith(s"${PauseReason.PlanQuota.wire}:")
        )))
    private def runOf(i: ManagerItemRecord): Option[JobRunState] = i.runId.flatMap(jobs.get)
    private def goalOf(i: ManagerItemRecord): Option[com.histmersion.domain.automation.managers.ManagerService.InMemory.GoalOfResult] = manager(i.managerId).flatMap(_.goal)
    private def view(e: ManagerEventRecord): ManagerEvent =
      ManagerEvent(e.id, e.managerId, e.itemId, e.runId, text(e.at), e.kind, e.text)
    private def view(i: ManagerItemRecord): ManagerItem =
      ManagerItem(
        i.id,
        i.managerId,
        i.kind,
        i.target,
        i.status,
        i.priority,
        i.attempts,
        i.failures,
        i.runId,
        i.evaluatedRun,
        i.notBefore.map(text),
        i.hold,
        i.state,
        i.outcome,
        i.origin,
        text(i.createdAt),
        text(i.updatedAt)
      )
    private def counted[K](ks: Iterable[K]): Map[K, com.histmersion.domain.automation.managers.ManagerService.InMemory.CountedResultPart2] = ks.groupBy(identity).view.mapValues(_.size).toMap

    def get(id: com.histmersion.domain.automation.managers.ManagerService.InMemory.GetId): Option[ManagerInfo] =
      manager(id).map { m =>
        val day = now.atZone(ZoneOffset.UTC).toLocalDate.atStartOfDay(ZoneOffset.UTC).toInstant
        val mine = runs.filter(_.row.managerId.contains(id)).map(_.id).toSet
        val today = state.get.steps.filter(s => mine(s.jobId) && !s.finishedAt.isBefore(day)).map(_.step)
        ManagerInfo(
          m.id,
          m.kind,
          m.goal,
          m.state,
          m.pauseRuns,
          m.config,
          ManagerConfig(),
          m.statusNote,
          m.cron,
          m.nextTickAt.map(text),
          text(m.createdAt),
          text(m.updatedAt),
          ItemCounts.of(counted(items.filter(_.managerId == id).map(_.status)).toList),
          runs.count(r => r.row.managerId.contains(id) && Set("queued", "running", "paused", "waiting")(r.row.status)),
          events.filter(_.managerId == id).maxByOption(_.id).map(view),
          today.map(_.costUsd).sum.toDouble,
          today.map(s => s.usage.input + s.usage.output).sum
        )
      }
    def list(): List[ManagerInfo] = managers.sortBy(m => (m.createdAt, m.id)).toList.flatMap(m => get(m.id))
    def pending(managerId: com.histmersion.domain.automation.managers.ManagerService.InMemory.PendingManagerId): List[ManagerItem] =
      items
        .filter(i => i.managerId == managerId && i.status == "pending")
        .sortBy(i => (-i.priority, i.id))
        .toList
        .map(view)
    private def urgency(status: com.histmersion.domain.automation.managers.ManagerService.InMemory.UrgencyStatus): com.histmersion.domain.automation.managers.ManagerService.InMemory.UrgencyResult = status match
      case "active"  => 0
      case "pending" => 1
      case "held"    => 2
      case _         => 3
    def items(managerId: com.histmersion.domain.automation.managers.ManagerService.InMemory.ItemsManagerId, limit: com.histmersion.domain.automation.managers.ManagerService.InMemory.ItemsLimit): List[ManagerItem] =
      items
        .filter(_.managerId == managerId)
        .sortBy(i =>
          (
            urgency(i.status),
            -i.priority,
            i.notBefore.fold(Long.MinValue)(_.toEpochMilli),
            -i.updatedAt.toEpochMilli,
            i.id
          )
        )
        .take(math.max(1, limit))
        .toList
        .map(view)
    def light(): List[ManagerLight] =
      managers
        .sortBy(_.id)
        .toList
        .map(m =>
          ManagerLight(
            m.id,
            m.kind,
            m.goal,
            m.state,
            m.cron,
            m.config,
            m.statusNote,
            items.exists(i => i.managerId == m.id && i.kind == PlanGoalItem)
          )
        )
    def create(
        id: com.histmersion.domain.automation.managers.ManagerService.InMemory.CreateId,
        kind: Kind,
        goal: Option[com.histmersion.domain.automation.managers.ManagerService.InMemory.CreateGoal],
        state: State,
        config: ManagerConfig,
        cron: Option[com.histmersion.domain.automation.managers.ManagerService.InMemory.CreateCron],
        nextTick: Option[Instant]
    ): com.histmersion.domain.automation.managers.ManagerService.InMemory.CreateResult =
      val at = now
      !this.state
        .getAndUpdate(s =>
          if s.managers.exists(_.id == id) then s
          else
            s.modify(_.managers)
              .using(_ :+ ManagerRecord(id, kind.wire, goal, state.wire, false, config, None, cron, nextTick, at, at))
        )
        .managers
        .exists(_.id == id)
    private def changeManager(id: com.histmersion.domain.automation.managers.ManagerService.InMemory.ChangeManagerId)(f: ManagerRecord => ManagerRecord): com.histmersion.domain.automation.managers.ManagerService.InMemory.ChangeManagerResult =
      state
        .getAndUpdate(_.modify(_.managers).using(_.map(m => if m.id == id then f(m) else m)))
        .managers
        .exists(_.id == id)
    def setState(id: com.histmersion.domain.automation.managers.ManagerService.InMemory.SetStateId, state: State, pauseRuns: com.histmersion.domain.automation.managers.ManagerService.InMemory.SetStatePauseRuns): com.histmersion.domain.automation.managers.ManagerService.InMemory.SetStateResult =
      val at = now
      changeManager(id)(
        _.modify(_.state).setTo(state.wire).modify(_.pauseRuns).setTo(pauseRuns).modify(_.updatedAt).setTo(at)
      )
    def setConfig(id: com.histmersion.domain.automation.managers.ManagerService.InMemory.SetConfigId, config: ManagerConfig, cron: Option[com.histmersion.domain.automation.managers.ManagerService.InMemory.SetConfigCron], next: Option[Instant]): com.histmersion.domain.automation.managers.ManagerService.InMemory.SetConfigResult =
      val at = now
      changeManager(id)(
        _.modify(_.config)
          .setTo(config)
          .modify(_.cron)
          .setTo(cron)
          .modify(_.nextTickAt)
          .setTo(next)
          .modify(_.updatedAt)
          .setTo(at)
      )
    def upsert(
        managerId: com.histmersion.domain.automation.managers.ManagerService.InMemory.UpsertManagerId,
        kind: com.histmersion.domain.automation.managers.ManagerService.InMemory.UpsertKind,
        target: com.histmersion.domain.automation.managers.ManagerService.InMemory.UpsertTarget,
        origin: ItemOrigin,
        priority: com.histmersion.domain.automation.managers.ManagerService.InMemory.UpsertPriority,
        reopen: com.histmersion.domain.automation.managers.ManagerService.InMemory.UpsertReopen,
        initial: ItemState
    ): (com.histmersion.domain.automation.managers.ManagerService.InMemory.UpsertResultPart1, com.histmersion.domain.automation.managers.ManagerService.InMemory.UpsertResultPart2) =
      val at = now
      var result = (0L, false)
      val _ = state.updateAndGet { s =>
        s.items.find(i => i.managerId == managerId && i.kind == kind && i.target == target) match
          case None =>
            val id = s.lastItemId + 1
            result = (id, true)
            val made = ManagerItemRecord(
              id,
              managerId,
              kind,
              target,
              ItemStatus.Pending.wire,
              priority,
              0,
              0,
              None,
              None,
              None,
              None,
              initial,
              None,
              origin.wire,
              at,
              at
            )
            s.modify(_.items).using(_ :+ made).modify(_.lastItemId).setTo(id)
          case Some(old) =>
            result = (old.id, false)
            if !reopen then s
            else
              val back = Settled(old.status)
              val again = old
                .modify(_.status)
                .using(st => if back then ItemStatus.Pending.wire else st)
                .modify(_.attempts)
                .using(n => if back then 0 else n)
                .modify(_.failures)
                .using(n => if back then 0 else n)
                .modify(_.notBefore)
                .using(nb => if back then None else nb)
                .modify(_.outcome)
                .using(o => if back then None else o)
                .modify(_.priority)
                .using(math.max(_, priority))
                .modify(_.origin)
                .setTo(origin.wire)
                .modify(_.updatedAt)
                .setTo(at)
              s.modify(_.items).using(_.map(i => if i.id == old.id then again else i))
      }
      result
    def item(id: com.histmersion.domain.automation.managers.ManagerService.InMemory.ItemId): Option[ManagerItem] = items.find(_.id == id).map(view)
    def itemFor(managerId: com.histmersion.domain.automation.managers.ManagerService.InMemory.ItemForManagerId, kind: com.histmersion.domain.automation.managers.ManagerService.InMemory.ItemForKind, target: com.histmersion.domain.automation.managers.ManagerService.InMemory.ItemForTarget): Option[ManagerItem] =
      items.find(i => i.managerId == managerId && i.kind == kind && i.target == target).map(view)
    def openFor(target: com.histmersion.domain.automation.managers.ManagerService.InMemory.OpenForTarget): List[ManagerItem] =
      items.filter(i => i.target == target && Open(i.status)).sortBy(-_.id).toList.map(view)
    def openKeys(managerId: com.histmersion.domain.automation.managers.ManagerService.InMemory.OpenKeysManagerId): List[(com.histmersion.domain.automation.managers.ManagerService.InMemory.OpenKeysResultPart1, com.histmersion.domain.automation.managers.ManagerService.InMemory.OpenKeysResultPart2)] =
      items.filter(i => i.managerId == managerId && Open(i.status)).toList.map(i => (i.kind, i.target))
    def openOf(managerId: com.histmersion.domain.automation.managers.ManagerService.InMemory.OpenOfManagerId, kinds: List[com.histmersion.domain.automation.managers.ManagerService.InMemory.OpenOfKinds], statuses: List[com.histmersion.domain.automation.managers.ManagerService.InMemory.OpenOfStatuses], focusHeld: com.histmersion.domain.automation.managers.ManagerService.InMemory.OpenOfFocusHeld): com.histmersion.domain.automation.managers.ManagerService.InMemory.OpenOfResult =
      items.count(i =>
        i.managerId == managerId && kinds.contains(i.kind) && statuses.contains(i.status) &&
          (focusHeld || !this.focusHeld(i))
      )
    def event(managerId: com.histmersion.domain.automation.managers.ManagerService.InMemory.EventManagerId, itemId: Option[com.histmersion.domain.automation.managers.ManagerService.InMemory.EventItemId], runId: Option[com.histmersion.domain.automation.Run.Id], kind: com.histmersion.domain.automation.managers.ManagerService.InMemory.EventKind, text: com.histmersion.domain.automation.managers.ManagerService.InMemory.EventText): Unit =
      val at = now
      val _ = state.updateAndGet { s =>
        val id = s.lastEventId + 1
        s.modify(_.events)
          .using(_ :+ ManagerEventRecord(id, managerId, itemId, runId, at, kind, text.take(2000)))
          .modify(_.lastEventId)
          .setTo(id)
      }
    def lastEventOf(managerId: com.histmersion.domain.automation.managers.ManagerService.InMemory.LastEventOfManagerId, kind: com.histmersion.domain.automation.managers.ManagerService.InMemory.LastEventOfKind): Option[ManagerEvent] =
      events.filter(e => e.managerId == managerId && e.kind == kind).maxByOption(_.id).map(view)
    def countEventsAll(kind: com.histmersion.domain.automation.managers.ManagerService.InMemory.CountEventsAllKind, since: Instant): com.histmersion.domain.automation.managers.ManagerService.InMemory.CountEventsAllResult =
      events.count(e => e.kind == kind && !e.at.isBefore(since))
    def backlogCounts(): Map[com.histmersion.domain.automation.managers.ManagerService.InMemory.BacklogCountsResultPart1, (com.histmersion.domain.automation.managers.ManagerService.InMemory.BacklogCountsResultPart2Part1, com.histmersion.domain.automation.managers.ManagerService.InMemory.BacklogCountsResultPart2Part2)] =
      items
        .filter(i => i.kind == OaIngest && (i.status == "pending" || i.status == "held"))
        .groupBy(_.managerId)
        .view
        .mapValues(is => (is.count(_.status == "pending"), is.count(_.status == "held")))
        .toMap
    private def backlog(i: ManagerItemRecord): com.histmersion.domain.automation.managers.ManagerService.InMemory.BacklogResult = i.kind == OaIngest && Open(i.status)
    def backlogPending(): List[ManagerItem] =
      val active = managers.filter(m => m.kind == Kind.Watch.wire && m.state == State.Active.wire).map(_.id).toSet
      items
        .filter(i => i.kind == OaIngest && i.status == "pending" && active(i.managerId))
        .sortBy(i => (-i.priority, i.id))
        .toList
        .map(view)
    def backlogOpen(): List[ManagerItem] = items.filter(backlog).sortBy(_.id).toList.map(view)
    def backlogOpenFor(doi: com.histmersion.domain.library.Doi): Option[ManagerItem] =
      items.filter(i => backlog(i) && i.target == doi).minByOption(_.id).map(view)
    def backlogRetry(itemId: com.histmersion.domain.automation.managers.ManagerService.InMemory.BacklogRetryItemId, failures: com.histmersion.domain.automation.managers.ManagerService.InMemory.BacklogRetryFailures, notBefore: Instant, outcome: com.histmersion.domain.automation.managers.ManagerService.InMemory.BacklogRetryOutcome): com.histmersion.domain.automation.managers.ManagerService.InMemory.BacklogRetryResult =
      val at = now
      changeItems(i => i.id == itemId && i.status == "pending")(
        _.modify(_.failures)
          .setTo(failures)
          .modify(_.attempts)
          .using(_ + 1)
          .modify(_.notBefore)
          .setTo(Some(notBefore))
          .modify(_.outcome)
          .setTo(Some(outcome))
          .modify(_.updatedAt)
          .setTo(at)
      ) > 0
    def countEventsBy(kind: com.histmersion.domain.automation.managers.ManagerService.InMemory.CountEventsByKind, since: Instant): Map[com.histmersion.domain.automation.managers.ManagerService.InMemory.CountEventsByResultPart1, com.histmersion.domain.automation.managers.ManagerService.InMemory.CountEventsByResultPart2] =
      counted(events.filter(e => e.kind == kind && !e.at.isBefore(since)).map(_.managerId))
    def setItemState(itemId: com.histmersion.domain.automation.managers.ManagerService.InMemory.SetItemStateItemId, state: ItemState, from: List[ItemStatus]): com.histmersion.domain.automation.managers.ManagerService.InMemory.SetItemStateResult =
      val at = now
      changeItems(i => i.id == itemId && from.exists(_.wire == i.status))(
        _.modify(_.state).setTo(state).modify(_.updatedAt).setTo(at)
      ) > 0

    /** The acquisition items are the work lists', which this state does not hold: none. */
    def sweepAcquireItems(): List[SweepAcquireItem] = Nil

    def pace(since: Instant): Map[com.histmersion.domain.automation.managers.ManagerService.InMemory.PaceResultPart1, (com.histmersion.domain.automation.managers.ManagerService.InMemory.PaceResultPart2Part1, com.histmersion.domain.automation.managers.ManagerService.InMemory.PaceResultPart2Part2, Option[com.histmersion.domain.automation.managers.ManagerService.InMemory.PaceResultPart2Part3])] =
      managers.map { m =>
        val mine = items.filter(i => i.managerId == m.id && i.kind != OaIngest)
        m.id -> (
          mine.count(i => Open(i.status)),
          mine.count(i => Set("done", "stopped", "failed")(i.status) && !i.updatedAt.isBefore(since)),
          events.filter(_.managerId == m.id).map(_.at).maxOption.map(text)
        )
      }.toMap
    private def changeItems(which: ManagerItemRecord => com.histmersion.domain.automation.managers.ManagerService.InMemory.ChangeItemsWhichResult)(f: ManagerItemRecord => ManagerItemRecord): com.histmersion.domain.automation.managers.ManagerService.InMemory.ChangeItemsResult =
      state.getAndUpdate(_.modify(_.items).using(_.map(i => if which(i) then f(i) else i))).items.count(which)
    private def released(at: Instant)(i: ManagerItemRecord): ManagerItemRecord =
      i.modify(_.status).setTo(ItemStatus.Pending.wire).modify(_.hold).setTo(None).modify(_.updatedAt).setTo(at)
    def hold(itemId: com.histmersion.domain.automation.managers.ManagerService.InMemory.HoldItemId, h: Hold): com.histmersion.domain.automation.managers.ManagerService.InMemory.HoldResult =
      val at = now
      changeItems(i => i.id == itemId && (i.status == "pending" || i.status == "held"))(
        _.modify(_.status).setTo(ItemStatus.Held.wire).modify(_.hold).setTo(Some(h)).modify(_.updatedAt).setTo(at)
      ) > 0
    def release(itemId: com.histmersion.domain.automation.managers.ManagerService.InMemory.ReleaseItemId): com.histmersion.domain.automation.managers.ManagerService.InMemory.ReleaseResult = changeItems(i => i.id == itemId && i.status == "held")(released(now)) > 0
    def settle(itemId: com.histmersion.domain.automation.managers.ManagerService.InMemory.SettleItemId, status: ItemStatus, outcome: com.histmersion.domain.automation.managers.ManagerService.InMemory.SettleOutcome, from: List[ItemStatus]): com.histmersion.domain.automation.managers.ManagerService.InMemory.SettleResult =
      val at = now
      changeItems(i => i.id == itemId && from.exists(_.wire == i.status))(
        _.modify(_.status)
          .setTo(status.wire)
          .modify(_.outcome)
          .setTo(Some(outcome))
          .modify(_.hold)
          .setTo(None)
          .modify(_.updatedAt)
          .setTo(at)
      ) > 0
    def dispatch(itemId: com.histmersion.domain.automation.managers.ManagerService.InMemory.DispatchItemId, run: com.histmersion.domain.automation.managers.ManagerService.InMemory.DispatchRun, state: ItemState): com.histmersion.domain.automation.managers.ManagerService.InMemory.DispatchResult =
      val at = now
      changeItems(i => i.id == itemId && i.status == ItemStatus.Pending.wire)(
        _.modify(_.status)
          .setTo(ItemStatus.Active.wire)
          .modify(_.runId)
          .setTo(Some(run))
          .modify(_.attempts)
          .using(_ + 1)
          .modify(_.state)
          .setTo(state)
          .modify(_.updatedAt)
          .setTo(at)
      ) > 0
    def focusHold(itemId: com.histmersion.domain.automation.managers.ManagerService.InMemory.FocusHoldItemId, h: Hold): com.histmersion.domain.automation.managers.ManagerService.InMemory.FocusHoldResult =
      val at = now
      changeItems(i => i.id == itemId && i.status == "pending")(
        _.modify(_.status).setTo(ItemStatus.Held.wire).modify(_.hold).setTo(Some(h)).modify(_.updatedAt).setTo(at)
      ) > 0
    def focusRelease(ids: List[com.histmersion.domain.automation.managers.ManagerService.InMemory.FocusReleaseIds]): com.histmersion.domain.automation.managers.ManagerService.InMemory.FocusReleaseResult =
      if ids.isEmpty then 0 else changeItems(i => ids.contains(i.id) && focusHeld(i))(released(now))
    def focusReleaseAll(): com.histmersion.domain.automation.managers.ManagerService.InMemory.FocusReleaseAllResult = changeItems(focusHeld)(released(now))
    def focusHeldCount(): com.histmersion.domain.automation.managers.ManagerService.InMemory.FocusHeldCountResult = items.count(focusHeld)
    def forPerson(): com.histmersion.domain.automation.managers.ManagerService.InMemory.ForPersonResult =
      items.count(i =>
        manager(i.managerId).exists(_.state != State.Stopped.wire) &&
          ((i.status == "held" && i.hold.forall(_.focus.isEmpty)) ||
            (i.status == "active" && runOf(i).exists(_.row.status == "waiting")))
      )
    def lastProgress(): Map[com.histmersion.domain.automation.managers.ManagerService.InMemory.LastProgressResultPart1, com.histmersion.domain.automation.managers.ManagerService.InMemory.LastProgressResultPart2] =
      val kinds = Set(Feed.Started, Feed.Settled, Feed.Evaluated, Feed.Ingested, Feed.Swept)
      val fromEvents = events
        .filter(e => kinds(e.kind))
        .flatMap(e => manager(e.managerId).flatMap(_.goal).map(_ -> e.at))
      val fromLinks = state.get.links.filter(_.kind == Goal.LinkKind.Source).map(l => l.goal -> l.createdAt)
      (fromEvents ++ fromLinks).groupBy(_._1).view.mapValues(ts => text(ts.map(_._2).max)).toMap
    def occupyingRuns(): List[(com.histmersion.domain.automation.managers.ManagerService.InMemory.OccupyingRunsResultPart1, Option[com.histmersion.domain.automation.managers.ManagerService.InMemory.OccupyingRunsResultPart2])] =
      runs.filter(occupying).map(r => (r.row.pipeline, r.row.managerId.flatMap(manager).flatMap(_.goal)))
    def personHeldByGoal(): Map[com.histmersion.domain.automation.managers.ManagerService.InMemory.PersonHeldByGoalResultPart1, com.histmersion.domain.automation.managers.ManagerService.InMemory.PersonHeldByGoalResultPart2] = counted(items.filter(personHeld).flatMap(goalOf))
    def recentKinds(n: com.histmersion.domain.automation.managers.ManagerService.InMemory.RecentKindsN): List[com.histmersion.domain.automation.managers.ManagerService.InMemory.RecentKindsResult] =
      runs
        .sortBy(r => (r.createdAt, r.created))
        .reverse
        .flatMap(r => r.row.managerItem.flatMap(id => items.find(_.id == id)).map(_.kind))
        .take(math.max(0, n))
    def running(id: com.histmersion.domain.automation.managers.ManagerService.InMemory.RunningId): com.histmersion.domain.automation.managers.ManagerService.InMemory.RunningResult = runs.count(r => r.row.managerId.contains(id) && occupying(r))
    def runningByGoal(): Map[com.histmersion.domain.automation.managers.ManagerService.InMemory.RunningByGoalResultPart1, com.histmersion.domain.automation.managers.ManagerService.InMemory.RunningByGoalResultPart2] =
      counted(
        items
          .filter(i =>
            i.status == "active" && runOf(i).exists(r => r.row.status == "queued" || r.row.status == "running")
          )
          .flatMap(goalOf)
      )
    def sweepOutcomes(): Map[com.histmersion.domain.automation.managers.ManagerService.InMemory.SweepOutcomesResultPart1, com.histmersion.domain.automation.managers.ManagerService.InMemory.SweepOutcomesResultPart2] =
      items
        .filter(i => i.kind == AcquisitionSweep && Set("done", "stopped", "failed")(i.status))
        .flatMap(i => goalOf(i).map(_ -> i.outcome.getOrElse("")))
        .toMap

  object InMemory:
    type TextResult = TextResult.T
    object TextResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ManagerId = ManagerId.T
    object ManagerId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FocusHeldResult = FocusHeldResult.T
    object FocusHeldResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PersonHeldResult = PersonHeldResult.T
    object PersonHeldResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OccupyingResult = OccupyingResult.T
    object OccupyingResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type GoalOfResult = GoalOfResult.T
    object GoalOfResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CountedResultPart2 = CountedResultPart2.T
    object CountedResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type GetId = GetId.T
    object GetId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PendingManagerId = PendingManagerId.T
    object PendingManagerId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type UrgencyResult = UrgencyResult.T
    object UrgencyResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type UrgencyStatus = UrgencyStatus.T
    object UrgencyStatus extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ItemsManagerId = ItemsManagerId.T
    object ItemsManagerId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ItemsLimit = ItemsLimit.T
    object ItemsLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CreateResult = CreateResult.T
    object CreateResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CreateId = CreateId.T
    object CreateId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CreateGoal = CreateGoal.T
    object CreateGoal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CreateCron = CreateCron.T
    object CreateCron extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ChangeManagerResult = ChangeManagerResult.T
    object ChangeManagerResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ChangeManagerId = ChangeManagerId.T
    object ChangeManagerId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SetStateResult = SetStateResult.T
    object SetStateResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SetStateId = SetStateId.T
    object SetStateId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SetStatePauseRuns = SetStatePauseRuns.T
    object SetStatePauseRuns extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SetConfigResult = SetConfigResult.T
    object SetConfigResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SetConfigId = SetConfigId.T
    object SetConfigId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SetConfigCron = SetConfigCron.T
    object SetConfigCron extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type UpsertResultPart1 = UpsertResultPart1.T
    object UpsertResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type UpsertResultPart2 = UpsertResultPart2.T
    object UpsertResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type UpsertManagerId = UpsertManagerId.T
    object UpsertManagerId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type UpsertKind = UpsertKind.T
    object UpsertKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type UpsertTarget = UpsertTarget.T
    object UpsertTarget extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type UpsertPriority = UpsertPriority.T
    object UpsertPriority extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type UpsertReopen = UpsertReopen.T
    object UpsertReopen extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ItemId = ItemId.T
    object ItemId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ItemForManagerId = ItemForManagerId.T
    object ItemForManagerId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ItemForKind = ItemForKind.T
    object ItemForKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ItemForTarget = ItemForTarget.T
    object ItemForTarget extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OpenForTarget = OpenForTarget.T
    object OpenForTarget extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OpenKeysResultPart1 = OpenKeysResultPart1.T
    object OpenKeysResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OpenKeysResultPart2 = OpenKeysResultPart2.T
    object OpenKeysResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OpenKeysManagerId = OpenKeysManagerId.T
    object OpenKeysManagerId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OpenOfResult = OpenOfResult.T
    object OpenOfResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OpenOfManagerId = OpenOfManagerId.T
    object OpenOfManagerId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OpenOfKinds = OpenOfKinds.T
    object OpenOfKinds extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OpenOfStatuses = OpenOfStatuses.T
    object OpenOfStatuses extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OpenOfFocusHeld = OpenOfFocusHeld.T
    object OpenOfFocusHeld extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type EventManagerId = EventManagerId.T
    object EventManagerId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type EventItemId = EventItemId.T
    object EventItemId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type EventKind = EventKind.T
    object EventKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type EventText = EventText.T
    object EventText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LastEventOfManagerId = LastEventOfManagerId.T
    object LastEventOfManagerId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LastEventOfKind = LastEventOfKind.T
    object LastEventOfKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CountEventsAllResult = CountEventsAllResult.T
    object CountEventsAllResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CountEventsAllKind = CountEventsAllKind.T
    object CountEventsAllKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type BacklogCountsResultPart1 = BacklogCountsResultPart1.T
    object BacklogCountsResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type BacklogCountsResultPart2Part1 = BacklogCountsResultPart2Part1.T
    object BacklogCountsResultPart2Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type BacklogCountsResultPart2Part2 = BacklogCountsResultPart2Part2.T
    object BacklogCountsResultPart2Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type BacklogResult = BacklogResult.T
    object BacklogResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type BacklogRetryResult = BacklogRetryResult.T
    object BacklogRetryResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type BacklogRetryItemId = BacklogRetryItemId.T
    object BacklogRetryItemId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type BacklogRetryFailures = BacklogRetryFailures.T
    object BacklogRetryFailures extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type BacklogRetryOutcome = BacklogRetryOutcome.T
    object BacklogRetryOutcome extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CountEventsByResultPart1 = CountEventsByResultPart1.T
    object CountEventsByResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CountEventsByResultPart2 = CountEventsByResultPart2.T
    object CountEventsByResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CountEventsByKind = CountEventsByKind.T
    object CountEventsByKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SetItemStateResult = SetItemStateResult.T
    object SetItemStateResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SetItemStateItemId = SetItemStateItemId.T
    object SetItemStateItemId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PaceResultPart1 = PaceResultPart1.T
    object PaceResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PaceResultPart2Part1 = PaceResultPart2Part1.T
    object PaceResultPart2Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PaceResultPart2Part2 = PaceResultPart2Part2.T
    object PaceResultPart2Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PaceResultPart2Part3 = PaceResultPart2Part3.T
    object PaceResultPart2Part3 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ChangeItemsResult = ChangeItemsResult.T
    object ChangeItemsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ChangeItemsWhichResult = ChangeItemsWhichResult.T
    object ChangeItemsWhichResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type HoldResult = HoldResult.T
    object HoldResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type HoldItemId = HoldItemId.T
    object HoldItemId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ReleaseResult = ReleaseResult.T
    object ReleaseResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ReleaseItemId = ReleaseItemId.T
    object ReleaseItemId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SettleResult = SettleResult.T
    object SettleResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SettleItemId = SettleItemId.T
    object SettleItemId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SettleOutcome = SettleOutcome.T
    object SettleOutcome extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type DispatchResult = DispatchResult.T
    object DispatchResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type DispatchItemId = DispatchItemId.T
    object DispatchItemId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type DispatchRun = DispatchRun.T
    object DispatchRun extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FocusHoldResult = FocusHoldResult.T
    object FocusHoldResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FocusHoldItemId = FocusHoldItemId.T
    object FocusHoldItemId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FocusReleaseResult = FocusReleaseResult.T
    object FocusReleaseResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FocusReleaseIds = FocusReleaseIds.T
    object FocusReleaseIds extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FocusReleaseAllResult = FocusReleaseAllResult.T
    object FocusReleaseAllResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FocusHeldCountResult = FocusHeldCountResult.T
    object FocusHeldCountResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ForPersonResult = ForPersonResult.T
    object ForPersonResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LastProgressResultPart1 = LastProgressResultPart1.T
    object LastProgressResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LastProgressResultPart2 = LastProgressResultPart2.T
    object LastProgressResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OccupyingRunsResultPart1 = OccupyingRunsResultPart1.T
    object OccupyingRunsResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OccupyingRunsResultPart2 = OccupyingRunsResultPart2.T
    object OccupyingRunsResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PersonHeldByGoalResultPart1 = PersonHeldByGoalResultPart1.T
    object PersonHeldByGoalResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PersonHeldByGoalResultPart2 = PersonHeldByGoalResultPart2.T
    object PersonHeldByGoalResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RecentKindsResult = RecentKindsResult.T
    object RecentKindsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RecentKindsN = RecentKindsN.T
    object RecentKindsN extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RunningResult = RunningResult.T
    object RunningResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RunningId = RunningId.T
    object RunningId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RunningByGoalResultPart1 = RunningByGoalResultPart1.T
    object RunningByGoalResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RunningByGoalResultPart2 = RunningByGoalResultPart2.T
    object RunningByGoalResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SweepOutcomesResultPart1 = SweepOutcomesResultPart1.T
    object SweepOutcomesResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SweepOutcomesResultPart2 = SweepOutcomesResultPart2.T
    object SweepOutcomesResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
