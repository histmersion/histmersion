package com.histmersion.domain.automation.managers
import com.histmersion.api.automation.Agents.{JobKind, PauseReason, RunState}
import com.histmersion.api.automation.Managers.*

import com.augustnagro.magnum.*
import com.histmersion.domain.automation.managers.SweepAcquireItem
import com.histmersion.domain.automation.managers.ManagerLight
import com.histmersion.util.database.{Jsonb, Param, Sql}
import com.histmersion.util.database.given

/** The store of the process managers (V087, docs/research-process-managers.md §2): managers, their work items, hints
  * (and which runs they reached), events, and the agent lease. All state lives in these rows, so a restart picks up
  * where the last process left off. Every state change of an item is a compare-and-set on its status (and, for an
  * evaluation, on the run it evaluates), so a tick that runs twice — or two processes, should the lease ever fail —
  * cannot do a step twice.
  */
object ManagerRepo:

  type AtCol = AtCol.T
  object AtCol extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SpendTodayResultPart2 = SpendTodayResultPart2.T
  object SpendTodayResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SpendTodayId = SpendTodayId.T
  object SpendTodayId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GetId = GetId.T
  object GetId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CreateResult = CreateResult.T
  object CreateResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CreateId = CreateId.T
  object CreateId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CreateGoal = CreateGoal.T
  object CreateGoal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CreateCron = CreateCron.T
  object CreateCron extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CreateInResult = CreateInResult.T
  object CreateInResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CreateInId = CreateInId.T
  object CreateInId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CreateInGoal = CreateInGoal.T
  object CreateInGoal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CreateInCron = CreateInCron.T
  object CreateInCron extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SetNextTickResult = SetNextTickResult.T
  object SetNextTickResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SetNextTickId = SetNextTickId.T
  object SetNextTickId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
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
  type SetConfigInResult = SetConfigInResult.T
  object SetConfigInResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SetConfigInId = SetConfigInId.T
  object SetConfigInId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SetConfigInCron = SetConfigInCron.T
  object SetConfigInCron extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SetStatusNoteResult = SetStatusNoteResult.T
  object SetStatusNoteResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SetStatusNoteId = SetStatusNoteId.T
  object SetStatusNoteId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SetStatusNoteNote = SetStatusNoteNote.T
  object SetStatusNoteNote extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DeleteResult = DeleteResult.T
  object DeleteResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DeleteId = DeleteId.T
  object DeleteId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CountsId = CountsId.T
  object CountsId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type InFlightResult = InFlightResult.T
  object InFlightResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type InFlightId = InFlightId.T
  object InFlightId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RunningResult = RunningResult.T
  object RunningResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RunningId = RunningId.T
  object RunningId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OccupyingRunsResultPart1 = OccupyingRunsResultPart1.T
  object OccupyingRunsResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OccupyingRunsResultPart2 = OccupyingRunsResultPart2.T
  object OccupyingRunsResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OccupyingOnT = OccupyingOnT.T
  object OccupyingOnT extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RecentKindsResult = RecentKindsResult.T
  object RecentKindsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RecentKindsN = RecentKindsN.T
  object RecentKindsN extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LastProgressResultPart1 = LastProgressResultPart1.T
  object LastProgressResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LastProgressResultPart2 = LastProgressResultPart2.T
  object LastProgressResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PersonHeldByGoalResultPart1 = PersonHeldByGoalResultPart1.T
  object PersonHeldByGoalResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PersonHeldByGoalResultPart2 = PersonHeldByGoalResultPart2.T
  object PersonHeldByGoalResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RunningByGoalResultPart1 = RunningByGoalResultPart1.T
  object RunningByGoalResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RunningByGoalResultPart2 = RunningByGoalResultPart2.T
  object RunningByGoalResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SweepOutcomesResultPart1 = SweepOutcomesResultPart1.T
  object SweepOutcomesResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SweepOutcomesResultPart2 = SweepOutcomesResultPart2.T
  object SweepOutcomesResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ForPersonResult = ForPersonResult.T
  object ForPersonResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
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
  type ItemPageResultPart2 = ItemPageResultPart2.T
  object ItemPageResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ItemPageManagerId = ItemPageManagerId.T
  object ItemPageManagerId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ItemPageOffset = ItemPageOffset.T
  object ItemPageOffset extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ItemPageLimit = ItemPageLimit.T
  object ItemPageLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WithStatusManagerId = WithStatusManagerId.T
  object WithStatusManagerId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
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
  type PendingManagerId = PendingManagerId.T
  object PendingManagerId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DispatchInResult = DispatchInResult.T
  object DispatchInResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DispatchInItemId = DispatchInItemId.T
  object DispatchInItemId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DispatchInRun = DispatchInRun.T
  object DispatchInRun extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ToEvaluateResultPart2Part1 = ToEvaluateResultPart2Part1.T
  object ToEvaluateResultPart2Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ToEvaluateResultPart2Part2 = ToEvaluateResultPart2Part2.T
  object ToEvaluateResultPart2Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ToEvaluateManagerId = ToEvaluateManagerId.T
  object ToEvaluateManagerId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
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
  type HoldResult = HoldResult.T
  object HoldResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HoldItemId = HoldItemId.T
  object HoldItemId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReleaseResult = ReleaseResult.T
  object ReleaseResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReleaseItemId = ReleaseItemId.T
  object ReleaseItemId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
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
  type SetPriorityResult = SetPriorityResult.T
  object SetPriorityResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SetPriorityItemId = SetPriorityItemId.T
  object SetPriorityItemId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SetPriorityPriority = SetPriorityPriority.T
  object SetPriorityPriority extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SettleResult = SettleResult.T
  object SettleResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SettleItemId = SettleItemId.T
  object SettleItemId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SettleOutcome = SettleOutcome.T
  object SettleOutcome extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReopenResult = ReopenResult.T
  object ReopenResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReopenItemId = ReopenItemId.T
  object ReopenItemId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RestaleResult = RestaleResult.T
  object RestaleResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RestaleItemId = RestaleItemId.T
  object RestaleItemId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RestalePriority = RestalePriority.T
  object RestalePriority extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RestaleWhy = RestaleWhy.T
  object RestaleWhy extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OpenKeysResultPart1 = OpenKeysResultPart1.T
  object OpenKeysResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OpenKeysResultPart2 = OpenKeysResultPart2.T
  object OpenKeysResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OpenKeysManagerId = OpenKeysManagerId.T
  object OpenKeysManagerId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HasItemResult = HasItemResult.T
  object HasItemResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HasItemManagerId = HasItemManagerId.T
  object HasItemManagerId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HasItemKind = HasItemKind.T
  object HasItemKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HasItemTarget = HasItemTarget.T
  object HasItemTarget extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CancelOpenResult = CancelOpenResult.T
  object CancelOpenResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CancelOpenManagerId = CancelOpenManagerId.T
  object CancelOpenManagerId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CancelOpenWhy = CancelOpenWhy.T
  object CancelOpenWhy extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RequeueResult = RequeueResult.T
  object RequeueResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RequeueItemId = RequeueItemId.T
  object RequeueItemId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RequeueOutcome = RequeueOutcome.T
  object RequeueOutcome extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OpenMajorArticlesResultPart1 = OpenMajorArticlesResultPart1.T
  object OpenMajorArticlesResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OpenMajorArticlesResultPart2 = OpenMajorArticlesResultPart2.T
  object OpenMajorArticlesResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OpenMajorArticlesManagerId = OpenMajorArticlesManagerId.T
  object OpenMajorArticlesManagerId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OpenMajorArticlesKind = OpenMajorArticlesKind.T
  object OpenMajorArticlesKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OpenMajorArticlesTags = OpenMajorArticlesTags.T
  object OpenMajorArticlesTags extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OpenMajorArticlesGoal = OpenMajorArticlesGoal.T
  object OpenMajorArticlesGoal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OpenMajorArticlesLimit = OpenMajorArticlesLimit.T
  object OpenMajorArticlesLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UnreviewedArticlesResult = UnreviewedArticlesResult.T
  object UnreviewedArticlesResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UnreviewedArticlesManagerId = UnreviewedArticlesManagerId.T
  object UnreviewedArticlesManagerId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UnreviewedArticlesKind = UnreviewedArticlesKind.T
  object UnreviewedArticlesKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
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
  type StaleCandidatesManagerId = StaleCandidatesManagerId.T
  object StaleCandidatesManagerId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StaleCandidatesKind = StaleCandidatesKind.T
  object StaleCandidatesKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
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
  type ExtendCandidatesManagerId = ExtendCandidatesManagerId.T
  object ExtendCandidatesManagerId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ExtendCandidatesTags = ExtendCandidatesTags.T
  object ExtendCandidatesTags extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ExtendCandidatesGoal = ExtendCandidatesGoal.T
  object ExtendCandidatesGoal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ExtendCandidatesLimit = ExtendCandidatesLimit.T
  object ExtendCandidatesLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PaceResultPart1 = PaceResultPart1.T
  object PaceResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PaceResultPart2Part1 = PaceResultPart2Part1.T
  object PaceResultPart2Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PaceResultPart2Part2 = PaceResultPart2Part2.T
  object PaceResultPart2Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PaceResultPart2Part3 = PaceResultPart2Part3.T
  object PaceResultPart2Part3 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
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
  type OpenOnResult = OpenOnResult.T
  object OpenOnResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OpenOnManagerId = OpenOnManagerId.T
  object OpenOnManagerId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OpenOnTarget = OpenOnTarget.T
  object OpenOnTarget extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ArticleFacetsResultPart1 = ArticleFacetsResultPart1.T
  object ArticleFacetsResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ArticleFacetsResultPart2 = ArticleFacetsResultPart2.T
  object ArticleFacetsResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ArticleFacetsSlug = ArticleFacetsSlug.T
  object ArticleFacetsSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SourceExistsResult = SourceExistsResult.T
  object SourceExistsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SourceExistsRef = SourceExistsRef.T
  object SourceExistsRef extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BacklogCountsResultPart1 = BacklogCountsResultPart1.T
  object BacklogCountsResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BacklogCountsResultPart2Part1 = BacklogCountsResultPart2Part1.T
  object BacklogCountsResultPart2Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BacklogCountsResultPart2Part2 = BacklogCountsResultPart2Part2.T
  object BacklogCountsResultPart2Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CountEventsByInResultPart1 = CountEventsByInResultPart1.T
  object CountEventsByInResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CountEventsByInResultPart2 = CountEventsByInResultPart2.T
  object CountEventsByInResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CountEventsByInKind = CountEventsByInKind.T
  object CountEventsByInKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SetItemStateInResult = SetItemStateInResult.T
  object SetItemStateInResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SetItemStateInItemId = SetItemStateInItemId.T
  object SetItemStateInItemId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BacklogRetryInResult = BacklogRetryInResult.T
  object BacklogRetryInResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BacklogRetryInItemId = BacklogRetryInItemId.T
  object BacklogRetryInItemId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BacklogRetryInFailures = BacklogRetryInFailures.T
  object BacklogRetryInFailures extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BacklogRetryInOutcome = BacklogRetryInOutcome.T
  object BacklogRetryInOutcome extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CitekeyOfDoiResult = CitekeyOfDoiResult.T
  object CitekeyOfDoiResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OpenAcquireCountResult = OpenAcquireCountResult.T
  object OpenAcquireCountResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EventInManagerId = EventInManagerId.T
  object EventInManagerId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EventInItemId = EventInItemId.T
  object EventInItemId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EventInKind = EventInKind.T
  object EventInKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EventInText = EventInText.T
  object EventInText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EventManagerId = EventManagerId.T
  object EventManagerId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EventItemId = EventItemId.T
  object EventItemId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EventKind = EventKind.T
  object EventKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EventText = EventText.T
  object EventText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EventsManagerId = EventsManagerId.T
  object EventsManagerId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EventsBefore = EventsBefore.T
  object EventsBefore extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EventsLimit = EventsLimit.T
  object EventsLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LastEventManagerId = LastEventManagerId.T
  object LastEventManagerId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CountEventsResult = CountEventsResult.T
  object CountEventsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CountEventsManagerId = CountEventsManagerId.T
  object CountEventsManagerId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CountEventsKind = CountEventsKind.T
  object CountEventsKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CountEventsAllResult = CountEventsAllResult.T
  object CountEventsAllResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CountEventsAllKind = CountEventsAllKind.T
  object CountEventsAllKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LastEventOfManagerId = LastEventOfManagerId.T
  object LastEventOfManagerId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LastEventOfKind = LastEventOfKind.T
  object LastEventOfKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PruneEventsResult = PruneEventsResult.T
  object PruneEventsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PruneEventsDays = PruneEventsDays.T
  object PruneEventsDays extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AddHintManagerId = AddHintManagerId.T
  object AddHintManagerId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AddHintItemId = AddHintItemId.T
  object AddHintItemId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AddHintText = AddHintText.T
  object AddHintText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AddHintAuthor = AddHintAuthor.T
  object AddHintAuthor extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AddHintStanding = AddHintStanding.T
  object AddHintStanding extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HintId = HintId.T
  object HintId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HintsManagerId = HintsManagerId.T
  object HintsManagerId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HintsLive = HintsLive.T
  object HintsLive extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SetHintEffectResult = SetHintEffectResult.T
  object SetHintEffectResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SetHintEffectId = SetHintEffectId.T
  object SetHintEffectId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RetireHintResult = RetireHintResult.T
  object RetireHintResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RetireHintManagerId = RetireHintManagerId.T
  object RetireHintManagerId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RetireHintId = RetireHintId.T
  object RetireHintId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private val ts = """'YYYY-MM-DD"T"HH24:MI:SS"Z"'"""
  private def at(col: com.histmersion.domain.automation.managers.ManagerRepo.AtCol) = s"to_char($col AT TIME ZONE 'UTC', $ts)"

  // the jsonb columns, through their DTOs' codecs ([[Jsonb]]); absent fields are left out when written
  private given configCodec: DbCodec[ManagerConfig] = Jsonb.codec(ManagerConfig(), dropNulls = true)
  private given stateCodec: DbCodec[ItemState] = Jsonb.codec(ItemState(), dropNulls = true)
  private given holdCodec: DbCodec[Option[Hold]] = Jsonb.optional[Hold](dropNulls = true)
  private given effectCodec: DbCodec[Option[HintEffect]] = Jsonb.optional[HintEffect](dropNulls = true)

  // ── managers ───────────────────────────────────────────────────────────────────────────────────

  final private case class ManagerRow(
      id: ManagerRow.Id,
      kind: ManagerRow.Kind,
      goal: Option[ManagerRow.Goal],
      state: ManagerRow.State,
      pauseRuns: ManagerRow.PauseRuns,
      config: ManagerConfig,
      statusNote: Option[ManagerRow.StatusNote],
      cron: Option[ManagerRow.Cron],
      nextTickAt: Option[ManagerRow.NextTickAt],
      createdAt: ManagerRow.CreatedAt,
      updatedAt: ManagerRow.UpdatedAt
  ) derives DbCodec

  object ManagerRow:
    type Id = Id.T
    object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Kind = Kind.T
    object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Goal = Goal.T
    object Goal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type State = State.T
    object State extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PauseRuns = PauseRuns.T
    object PauseRuns extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type StatusNote = StatusNote.T
    object StatusNote extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Cron = Cron.T
    object Cron extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type NextTickAt = NextTickAt.T
    object NextTickAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CreatedAt = CreatedAt.T
    object CreatedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type UpdatedAt = UpdatedAt.T
    object UpdatedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private val managerSelect =
    Sql.raw(s"""SELECT id, kind, goal_slug, state, pause_runs, config::text, status_note, cron, ${at("next_tick_at")},
              ${at("created_at")}, ${at("updated_at")} FROM agent_managers""")

  private def toInfo(r: ManagerRow)(using DbCon): ManagerInfo =
    val (usd, tokens) = spendToday(r.id)
    ManagerInfo(
      r.id,
      r.kind,
      r.goal,
      r.state,
      r.pauseRuns,
      r.config,
      ManagerConfig(),
      r.statusNote,
      r.cron,
      r.nextTickAt,
      r.createdAt,
      r.updatedAt,
      counts(r.id),
      inFlight(r.id),
      lastEvent(r.id),
      usd.toDouble,
      tokens
    )

  /** What a manager's runs spent this UTC day (the steps that finished since midnight UTC): metered USD — a plan-billed
    * call is recorded at 0 — and tokens in + out, every call's.
    */
  def spendToday(id: com.histmersion.domain.automation.managers.ManagerRepo.SpendTodayId)(using DbCon): (BigDecimal, com.histmersion.domain.automation.managers.ManagerRepo.SpendTodayResultPart2) =
    sql"""SELECT coalesce(sum(s.cost_usd), 0), coalesce(sum(s.tokens_in + s.tokens_out), 0)::bigint
          FROM agent_steps s JOIN jobs j ON j.id = s.job_id
          WHERE j.manager_id = $id
            AND s.finished_at >= date_trunc('day', now() AT TIME ZONE 'UTC') AT TIME ZONE 'UTC'"""
      .query[(BigDecimal, Long)]
      .run()
      .head

  def list()(using DbCon): List[ManagerInfo] =
    (managerSelect ++ Sql.of(" ORDER BY created_at, id")).frag
      .query[ManagerRow]
      .run()
      .toList
      .map(toInfo(_))

  def get(id: com.histmersion.domain.automation.managers.ManagerRepo.GetId)(using DbCon): Option[ManagerInfo] =
    (managerSelect ++ Sql.of(" WHERE id = ?", Param.S(id))).frag
      .query[ManagerRow]
      .run()
      .headOption
      .map(toInfo(_))

  /** A new manager (with its cron and first firing, if it has one); `false` when the id is taken. */
  def create(
      id: com.histmersion.domain.automation.managers.ManagerRepo.CreateId,
      kind: Kind,
      goal: Option[com.histmersion.domain.automation.managers.ManagerRepo.CreateGoal],
      state: State,
      config: ManagerConfig,
      cron: Option[com.histmersion.domain.automation.managers.ManagerRepo.CreateCron] = None,
      nextTick: Option[java.time.Instant] = None
  )(using DbCon): com.histmersion.domain.automation.managers.ManagerRepo.CreateResult =
    createIn(id, kind, goal, state, config, cron, nextTick)

  def createIn(
      id: com.histmersion.domain.automation.managers.ManagerRepo.CreateInId,
      kind: Kind,
      goal: Option[com.histmersion.domain.automation.managers.ManagerRepo.CreateInGoal],
      state: State,
      config: ManagerConfig,
      cron: Option[com.histmersion.domain.automation.managers.ManagerRepo.CreateInCron],
      nextTick: Option[java.time.Instant]
  )(using DbCon): com.histmersion.domain.automation.managers.ManagerRepo.CreateInResult =
    val next = nextTick.map(_.toString)
    sql"""INSERT INTO agent_managers (id, kind, goal_slug, state, config, cron, next_tick_at)
          VALUES ($id, ${kind.wire}, $goal, ${state.wire}, $config::jsonb, $cron, $next::timestamptz)
          ON CONFLICT (id) DO NOTHING""".update.run() > 0

  /** When its cron fires next (None: not scheduled). */
  def setNextTick(id: com.histmersion.domain.automation.managers.ManagerRepo.SetNextTickId, next: Option[java.time.Instant])(using DbCon): com.histmersion.domain.automation.managers.ManagerRepo.SetNextTickResult =
    val n = next.map(_.toString)
    (sql"""UPDATE agent_managers SET next_tick_at = $n::timestamptz WHERE id = $id""".update.run() > 0)

  def setState(id: com.histmersion.domain.automation.managers.ManagerRepo.SetStateId, state: State, pauseRuns: com.histmersion.domain.automation.managers.ManagerRepo.SetStatePauseRuns)(using DbCon): com.histmersion.domain.automation.managers.ManagerRepo.SetStateResult =
    sql"""UPDATE agent_managers SET state = ${state.wire}, pause_runs = $pauseRuns, updated_at = now()
          WHERE id = $id""".update.run() > 0

  /** Its settings and cron; `next` = when the cron fires next (None with no cron). */
  def setConfig(
      id: com.histmersion.domain.automation.managers.ManagerRepo.SetConfigId,
      config: ManagerConfig,
      cron: Option[com.histmersion.domain.automation.managers.ManagerRepo.SetConfigCron],
      next: Option[java.time.Instant]
  )(using DbCon): com.histmersion.domain.automation.managers.ManagerRepo.SetConfigResult =
    setConfigIn(id, config, cron, next)

  def setConfigIn(id: com.histmersion.domain.automation.managers.ManagerRepo.SetConfigInId, config: ManagerConfig, cron: Option[com.histmersion.domain.automation.managers.ManagerRepo.SetConfigInCron], next: Option[java.time.Instant])(using
      DbCon
  ): com.histmersion.domain.automation.managers.ManagerRepo.SetConfigInResult =
    val n = next.map(_.toString)
    sql"""UPDATE agent_managers SET config = $config::jsonb, cron = $cron, next_tick_at = $n::timestamptz, updated_at = now()
          WHERE id = $id""".update.run() > 0

  /** The derived status line; written only when it changed (no `updated_at` churn every tick). */
  def setStatusNote(id: com.histmersion.domain.automation.managers.ManagerRepo.SetStatusNoteId, note: com.histmersion.domain.automation.managers.ManagerRepo.SetStatusNoteNote)(using DbCon): com.histmersion.domain.automation.managers.ManagerRepo.SetStatusNoteResult =
    sql"""UPDATE agent_managers SET status_note = $note
          WHERE id = $id AND status_note IS DISTINCT FROM $note""".update.run() > 0

  def delete(id: com.histmersion.domain.automation.managers.ManagerRepo.DeleteId)(using DbCon): com.histmersion.domain.automation.managers.ManagerRepo.DeleteResult =
    (sql"DELETE FROM agent_managers WHERE id = $id".update.run() > 0)

  def counts(id: com.histmersion.domain.automation.managers.ManagerRepo.CountsId)(using DbCon): ItemCounts =
    ItemCounts.of(
      sql"""SELECT status, count(*)::int FROM manager_items WHERE manager_id = $id GROUP BY status"""
        .query[(String, Int)]
        .run()
        .toList
    )

  /** Its runs not settled yet (queued, running, paused, waiting). */
  def inFlight(id: com.histmersion.domain.automation.managers.ManagerRepo.InFlightId)(using DbCon): com.histmersion.domain.automation.managers.ManagerRepo.InFlightResult =
    sql"""SELECT count(*)::int FROM jobs WHERE kind = $JobKind AND manager_id = $id
                      AND status IN ('queued', 'running', 'paused', 'waiting')""".query[Int].run().head

  /** Its runs that run, or will run again without a person (what a manager's own `maxRuns` counts): queued, running,
    * and paused for a reason that resumes by itself — a restart's `interrupted` and a `stalled` pause (both carry
    * `resume_at`), an `allowance` or `plan-quota` pause. Counting only `queued`/`running` let a restart overshoot
    * (#1803): boot pauses the runs it interrupted, the first tick saw no run and started `maxRuns` new ones, and a
    * minute later the engine resumed the interrupted ones beside them. A pause a person must lift (`requested`,
    * `budget`, `provider`) frees its place — resuming it is that person's call — and so does a run WAITING for a
    * person's approval (#3762 proposal 1: one unanswered approval used to stop a whole manager).
    */
  def running(id: com.histmersion.domain.automation.managers.ManagerRepo.RunningId)(using DbCon): com.histmersion.domain.automation.managers.ManagerRepo.RunningResult =

    (Sql.of("SELECT count(*)::int FROM jobs WHERE kind = ? AND manager_id = ? AND ", Param.S(JobKind), Param.S(id)) ++
      occupyingOn("jobs")).frag.query[Int].run().head

  /** Every agent run that occupies a place ([[running]]'s rule, a person's runs too): its pipeline, and the goal of the
    * manager that started it (None: a person's, or a library-wide manager's).
    */
  def occupyingRuns()(using DbCon): List[(com.histmersion.domain.automation.managers.ManagerRepo.OccupyingRunsResultPart1, Option[com.histmersion.domain.automation.managers.ManagerRepo.OccupyingRunsResultPart2])] =

    (Sql.of(
      "SELECT j.pipeline, m.goal_slug FROM jobs j LEFT JOIN agent_managers m ON m.id = j.manager_id " +
        "WHERE j.kind = ? AND ",
      Param.S(JobKind)
    ) ++ occupyingOn("j")).frag.query[(String, Option[String])].run().toList

  /** The runs that occupy a place ([[running]]'s rule), as a predicate on table (alias) `t` of `jobs`. */
  private def occupyingOn(t: com.histmersion.domain.automation.managers.ManagerRepo.OccupyingOnT): Sql =
    Sql.of(
      s"($t.status IN ('queued', 'running') OR ($t.status = 'paused' AND ($t.resume_at IS NOT NULL OR $t.error LIKE ? " +
        s"OR $t.error LIKE ?)))",
      Param.S(s"${PauseReason.Allowance.wire}:%"),
      Param.S(s"${PauseReason.PlanQuota.wire}:%")
    )

  /** The item kinds of the last `n` runs the managers started, newest first (what the scheduler's shares count).
    */
  def recentKinds(n: com.histmersion.domain.automation.managers.ManagerRepo.RecentKindsN)(using DbCon): List[com.histmersion.domain.automation.managers.ManagerRepo.RecentKindsResult] =
    sql"""SELECT i.kind FROM jobs j JOIN manager_items i ON i.id = j.manager_item
          WHERE j.kind = $JobKind ORDER BY j.created_at DESC, j.id DESC LIMIT ${math.max(0, n)}"""
      .query[String]
      .run()
      .toList

  /** Per goal: when it last progressed — a run of one of its managers started, settled or was evaluated, a work was
    * ingested or swept for it, or a source was linked to it — as an ISO instant (UTC).
    */
  def lastProgress()(using DbCon): Map[com.histmersion.domain.automation.managers.ManagerRepo.LastProgressResultPart1, com.histmersion.domain.automation.managers.ManagerRepo.LastProgressResultPart2] =
    val ts = """'YYYY-MM-DD"T"HH24:MI:SS"Z"'"""
    Sql
      .of(
        s"""SELECT goal, to_char(max(at) AT TIME ZONE 'UTC', $ts) FROM (
                 SELECT m.goal_slug AS goal, max(e.at) AS at FROM manager_events e JOIN agent_managers m ON m.id = e.manager_id
                 WHERE m.goal_slug IS NOT NULL AND e.kind IN (?, ?, ?, ?, ?) GROUP BY m.goal_slug
                 UNION ALL
                 SELECT goal_slug, max(created_at) FROM goal_links WHERE kind = 'source' GROUP BY goal_slug) t
               GROUP BY goal""",
        Param.S(Feed.Started),
        Param.S(Feed.Settled),
        Param.S(Feed.Evaluated),
        Param.S(Feed.Ingested),
        Param.S(Feed.Swept)
      )
      .frag
      .query[(String, String)]
      .run()
      .toMap

  /** Per goal: its items a person holds (held, not for an approval and not by the focus). */
  def personHeldByGoal()(using DbCon): Map[com.histmersion.domain.automation.managers.ManagerRepo.PersonHeldByGoalResultPart1, com.histmersion.domain.automation.managers.ManagerRepo.PersonHeldByGoalResultPart2] =
    sql"""SELECT m.goal_slug, count(*)::int FROM manager_items i JOIN agent_managers m ON m.id = i.manager_id
          WHERE m.goal_slug IS NOT NULL AND i.status = 'held' AND i.hold->>'focus' IS NULL AND i.hold->>'untilApproval' IS NULL
          GROUP BY m.goal_slug""".query[(String, Int)].run().toMap

  /** Per goal: its items whose run is queued or running. */
  def runningByGoal()(using DbCon): Map[com.histmersion.domain.automation.managers.ManagerRepo.RunningByGoalResultPart1, com.histmersion.domain.automation.managers.ManagerRepo.RunningByGoalResultPart2] =
    sql"""SELECT m.goal_slug, count(*)::int FROM manager_items i JOIN agent_managers m ON m.id = i.manager_id
          JOIN jobs j ON j.id = i.run_id
          WHERE m.goal_slug IS NOT NULL AND i.status = 'active' AND j.status IN ('queued', 'running')
          GROUP BY m.goal_slug""".query[(String, Int)].run().toMap

  /** Per goal: the outcome of its watcher's acquisition sweep when it is settled. */
  def sweepOutcomes()(using DbCon): Map[com.histmersion.domain.automation.managers.ManagerRepo.SweepOutcomesResultPart1, com.histmersion.domain.automation.managers.ManagerRepo.SweepOutcomesResultPart2] =
    sql"""SELECT m.goal_slug, coalesce(i.outcome, '') FROM manager_items i JOIN agent_managers m ON m.id = i.manager_id
          WHERE m.goal_slug IS NOT NULL AND i.kind = $AcquisitionSweep AND i.status IN ('done', 'stopped', 'failed')"""
      .query[(String, String)]
      .run()
      .toMap

  /** Items that wait for a person and hold no place: held (not by the focus: for an approval, or by a person), or
    * active with their run waiting for an approval — of managers that are not stopped.
    */
  def forPerson()(using DbCon): com.histmersion.domain.automation.managers.ManagerRepo.ForPersonResult =
    sql"""SELECT count(*)::int FROM manager_items i JOIN agent_managers m ON m.id = i.manager_id
          LEFT JOIN jobs j ON j.id = i.run_id
          WHERE m.state <> 'stopped' AND ((i.status = 'held' AND i.hold->>'focus' IS NULL)
            OR (i.status = 'active' AND j.status = 'waiting'))""".query[Int].run().head

  // ── items ──────────────────────────────────────────────────────────────────────────────────────

  final private case class ItemRow(
      id: ItemRow.Id,
      managerId: ItemRow.ManagerId,
      kind: ItemRow.Kind,
      target: ItemRow.Target,
      status: ItemRow.Status,
      priority: ItemRow.Priority,
      attempts: ItemRow.Attempts,
      failures: ItemRow.Failures,
      runId: Option[com.histmersion.domain.automation.Run.Id],
      evaluatedRun: Option[ItemRow.EvaluatedRun],
      notBefore: Option[ItemRow.NotBefore],
      hold: Option[Hold],
      state: ItemState,
      outcome: Option[ItemRow.Outcome],
      origin: ItemRow.Origin,
      createdAt: ItemRow.CreatedAt,
      updatedAt: ItemRow.UpdatedAt
  ) derives DbCodec

  object ItemRow:
    type Id = Id.T
    object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ManagerId = ManagerId.T
    object ManagerId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Kind = Kind.T
    object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Target = Target.T
    object Target extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Status = Status.T
    object Status extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Priority = Priority.T
    object Priority extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Attempts = Attempts.T
    object Attempts extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Failures = Failures.T
    object Failures extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type EvaluatedRun = EvaluatedRun.T
    object EvaluatedRun extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type NotBefore = NotBefore.T
    object NotBefore extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Outcome = Outcome.T
    object Outcome extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Origin = Origin.T
    object Origin extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CreatedAt = CreatedAt.T
    object CreatedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type UpdatedAt = UpdatedAt.T
    object UpdatedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private val itemSelect =
    Sql.raw(s"""SELECT id, manager_id, kind, target, status, priority, attempts, failures, run_id, evaluated_run,
              ${at("not_before")}, hold::text, state::text, outcome, origin, ${at("created_at")}, ${at("updated_at")}
       FROM manager_items""")

  private def toItem(r: ItemRow): ManagerItem =
    ManagerItem(
      r.id,
      r.managerId,
      r.kind,
      r.target,
      r.status,
      r.priority,
      r.attempts,
      r.failures,
      r.runId,
      r.evaluatedRun,
      r.notBefore,
      r.hold,
      r.state,
      r.outcome,
      r.origin,
      r.createdAt,
      r.updatedAt
    )

  def item(id: com.histmersion.domain.automation.managers.ManagerRepo.ItemId)(using DbCon): Option[ManagerItem] =
    (itemSelect ++ Sql.of(" WHERE id = ?", Param.L(id))).frag.query[ItemRow].run().headOption.map(toItem)

  def itemFor(managerId: com.histmersion.domain.automation.managers.ManagerRepo.ItemForManagerId, kind: com.histmersion.domain.automation.managers.ManagerRepo.ItemForKind, target: com.histmersion.domain.automation.managers.ManagerRepo.ItemForTarget)(using DbCon): Option[ManagerItem] =

    (itemSelect ++ Sql.of(
      " WHERE manager_id = ? AND kind = ? AND target = ?",
      Param.S(managerId),
      Param.S(kind),
      Param.S(target)
    )).frag.query[ItemRow].run().headOption.map(toItem)

  /** The open items (pending, held, active) of any manager whose target is `target`, the newest first. */
  def openFor(target: com.histmersion.domain.automation.managers.ManagerRepo.OpenForTarget)(using DbCon): List[ManagerItem] =

    (itemSelect ++ Sql.of(
      " WHERE target = ? AND status IN ('pending', 'held', 'active') ORDER BY id DESC",
      Param.S(target)
    )).frag.query[ItemRow].run().toList.map(toItem)

  /** [[items]] on the caller's connection. */
  def itemPage(managerId: com.histmersion.domain.automation.managers.ManagerRepo.ItemPageManagerId, status: Option[ItemStatus], offset: com.histmersion.domain.automation.managers.ManagerRepo.ItemPageOffset, limit: com.histmersion.domain.automation.managers.ManagerRepo.ItemPageLimit)(using
      DbCon
  ): (List[ManagerItem], com.histmersion.domain.automation.managers.ManagerRepo.ItemPageResultPart2) =
    val where = Sql.of(" WHERE manager_id = ?", Param.S(managerId)) ++
      status.fold(Sql.empty)(s => Sql.of(" AND status = ?", Param.S(s.wire)))
    val total = (Sql.of("SELECT count(*)::int FROM manager_items") ++ where).frag.query[Int].run().head
    val rows = (itemSelect ++ where ++ Sql.of(
      """ ORDER BY CASE status WHEN 'active' THEN 0 WHEN 'pending' THEN 1 WHEN 'held' THEN 2 ELSE 3 END,
             priority DESC, coalesce(not_before, '-infinity'::timestamptz), updated_at DESC, id
           OFFSET ? LIMIT ?""",
      Param.I(math.max(0, offset)),
      Param.I(math.max(1, limit))
    )).frag.query[ItemRow].run().toList.map(toItem)
    (rows, total)

  def withStatus(managerId: com.histmersion.domain.automation.managers.ManagerRepo.WithStatusManagerId, status: ItemStatus)(using DbCon): List[ManagerItem] =

    (itemSelect ++ Sql.of(
      " WHERE manager_id = ? AND status = ? ORDER BY priority DESC, id",
      Param.S(managerId),
      Param.S(status.wire)
    )).frag.query[ItemRow].run().toList.map(toItem)

  /** Add an item, or — when it exists — bring it back: `reopen` sets a settled one pending again, and the priority is
    * raised to `priority` (an active one keeps running). Returns the item's id and whether it was new.
    */
  def upsert(
      managerId: com.histmersion.domain.automation.managers.ManagerRepo.UpsertManagerId,
      kind: com.histmersion.domain.automation.managers.ManagerRepo.UpsertKind,
      target: com.histmersion.domain.automation.managers.ManagerRepo.UpsertTarget,
      origin: ItemOrigin,
      priority: com.histmersion.domain.automation.managers.ManagerRepo.UpsertPriority,
      reopen: com.histmersion.domain.automation.managers.ManagerRepo.UpsertReopen,
      initial: ItemState = ItemState()
  )(using DbCon): (com.histmersion.domain.automation.managers.ManagerRepo.UpsertResultPart1, com.histmersion.domain.automation.managers.ManagerRepo.UpsertResultPart2) =
    sql"""INSERT INTO manager_items (manager_id, kind, target, origin, priority, state)
          VALUES ($managerId, $kind, $target, ${origin.wire}, $priority, $initial::jsonb)
          ON CONFLICT (manager_id, kind, target) DO NOTHING RETURNING id""".query[Long].run().headOption match
      case Some(id) => (id, true)
      case None     =>
        val id =
          sql"""SELECT id FROM manager_items WHERE manager_id = $managerId AND kind = $kind AND target = $target"""
            .query[Long]
            .run()
            .head
        if reopen then
          sql"""UPDATE manager_items SET
                  status = CASE WHEN status IN ('done', 'stopped', 'failed', 'cancelled') THEN 'pending' ELSE status END,
                  attempts = CASE WHEN status IN ('done', 'stopped', 'failed', 'cancelled') THEN 0 ELSE attempts END,
                  failures = CASE WHEN status IN ('done', 'stopped', 'failed', 'cancelled') THEN 0 ELSE failures END,
                  not_before = CASE WHEN status IN ('done', 'stopped', 'failed', 'cancelled') THEN NULL ELSE not_before END,
                  outcome = CASE WHEN status IN ('done', 'stopped', 'failed', 'cancelled') THEN NULL ELSE outcome END,
                  priority = greatest(priority, $priority), origin = ${origin.wire}, updated_at = now()
                WHERE id = $id""".update.run()
        (id, false)

  /** The pending items whose time has come, the most urgent first: (id, target, priority, not-before). */
  def pending(managerId: com.histmersion.domain.automation.managers.ManagerRepo.PendingManagerId)(using DbCon): List[ManagerItem] =
    withStatus(managerId, ItemStatus.Pending)

  /** Dispatch, in the caller's transaction: pending → active with `run`, one more attempt, the open MAJORs before it in
    * its state. `false` = it was no longer pending (another tick took it).
    */
  def dispatchIn(itemId: com.histmersion.domain.automation.managers.ManagerRepo.DispatchInItemId, run: com.histmersion.domain.automation.managers.ManagerRepo.DispatchInRun, state: ItemState)(using DbCon): com.histmersion.domain.automation.managers.ManagerRepo.DispatchInResult =
    sql"""UPDATE manager_items SET status = ${ItemStatus.Active.wire}, run_id = $run, attempts = attempts + 1,
                                   state = $state::jsonb, updated_at = now()
          WHERE id = $itemId AND status = ${ItemStatus.Pending.wire}""".update.run() > 0

  /** Active items whose run is terminal and not evaluated yet (or whose run is gone): the evaluation queue.
    */
  def toEvaluate(managerId: com.histmersion.domain.automation.managers.ManagerRepo.ToEvaluateManagerId)(using DbCon): List[(ManagerItem, Option[(com.histmersion.domain.automation.managers.ManagerRepo.ToEvaluateResultPart2Part1, Option[com.histmersion.domain.automation.managers.ManagerRepo.ToEvaluateResultPart2Part2])])] =
    val rows =
      (itemSelect ++ Sql.of(" WHERE manager_id = ? AND status = 'active' ORDER BY id", Param.S(managerId))).frag
        .query[ItemRow]
        .run()
        .toList
        .map(toItem)
    rows.flatMap { i =>
      val run = i.runId.flatMap(r =>
        (
          sql"SELECT status, error FROM jobs WHERE id = $r"
            .query[(String, Option[String])]
            .run()
            .headOption
        )
      )
      run match
        case None => List(i -> None) // its run was deleted
        case Some((st, err)) if RunState.parse(st).exists(_.isTerminal) && !i.evaluatedRun.contains(i.runId.get) =>
          List(i -> Some((st, err)))
        case _ => Nil
    }

  /** Record an evaluation (keyed by the run: a second evaluation of the same run changes nothing). `hold` = the hold it
    * is left in (`held`: waiting for an approval).
    */
  def evaluated(
      itemId: com.histmersion.domain.automation.managers.ManagerRepo.EvaluatedItemId,
      run: Option[com.histmersion.domain.automation.managers.ManagerRepo.EvaluatedRun],
      status: ItemStatus,
      notBefore: Option[java.time.Instant],
      failures: Option[com.histmersion.domain.automation.managers.ManagerRepo.EvaluatedFailures],
      state: ItemState,
      outcome: Option[com.histmersion.domain.automation.managers.ManagerRepo.EvaluatedOutcome],
      hold: Option[Hold] = None
  )(using DbCon): com.histmersion.domain.automation.managers.ManagerRepo.EvaluatedResult =
    val nb = notBefore.map(_.toString)
    val key = run.getOrElse("")
    sql"""UPDATE manager_items SET status = ${status.wire}, evaluated_run = coalesce($run, evaluated_run),
                                   not_before = $nb::timestamptz, failures = coalesce($failures, failures),
                                   state = $state::jsonb, outcome = coalesce($outcome, outcome), hold = $hold::jsonb,
                                   updated_at = now()
          WHERE id = $itemId AND status = 'active'
            AND coalesce(run_id, '') = $key AND evaluated_run IS DISTINCT FROM $run""".update.run() > 0

  /** A person's (or a hint's) change of one item. Each is a CAS on the statuses it may change. */
  def hold(itemId: com.histmersion.domain.automation.managers.ManagerRepo.HoldItemId, h: Hold)(using DbCon): com.histmersion.domain.automation.managers.ManagerRepo.HoldResult =
    val hold = Option(h)
    sql"""UPDATE manager_items SET status = 'held', hold = $hold::jsonb, updated_at = now()
          WHERE id = $itemId AND status IN ('pending', 'held')""".update.run() > 0

  def release(itemId: com.histmersion.domain.automation.managers.ManagerRepo.ReleaseItemId)(using DbCon): com.histmersion.domain.automation.managers.ManagerRepo.ReleaseResult =
    sql"""UPDATE manager_items SET status = 'pending', hold = NULL, updated_at = now()
          WHERE id = $itemId AND status = 'held'""".update.run() > 0

  // ── the focus's holds (docs/research-process-managers.md "As built: the focus") ─────────────────

  /** Hold a PENDING item for the focus (`h.focus` = its goal); false = it was not pending any more. */
  def focusHold(itemId: com.histmersion.domain.automation.managers.ManagerRepo.FocusHoldItemId, h: Hold)(using DbCon): com.histmersion.domain.automation.managers.ManagerRepo.FocusHoldResult =
    val hold = Option(h)
    sql"""UPDATE manager_items SET status = 'held', hold = $hold::jsonb, updated_at = now()
          WHERE id = $itemId AND status = 'pending'""".update.run() > 0

  /** Release the focus holds among `ids` (a person's or an approval's hold is never touched); how many. */
  def focusRelease(ids: List[com.histmersion.domain.automation.managers.ManagerRepo.FocusReleaseIds])(using DbCon): com.histmersion.domain.automation.managers.ManagerRepo.FocusReleaseResult =
    if ids.isEmpty then 0
    else
      val arr = ids.map(_.toString)
      sql"""UPDATE manager_items SET status = 'pending', hold = NULL, updated_at = now()
            WHERE id = ANY($arr::bigint[]) AND status = 'held' AND hold->>'focus' IS NOT NULL""".update.run()

  /** Release every focus hold (the focus is not in force any more); how many. */
  def focusReleaseAll()(using DbCon): com.histmersion.domain.automation.managers.ManagerRepo.FocusReleaseAllResult =
    sql"""UPDATE manager_items SET status = 'pending', hold = NULL, updated_at = now()
          WHERE status = 'held' AND hold->>'focus' IS NOT NULL""".update.run()

  /** The items the focus holds now. */
  def focusHeldCount()(using DbCon): com.histmersion.domain.automation.managers.ManagerRepo.FocusHeldCountResult =

    sql"""SELECT count(*)::int FROM manager_items WHERE status = 'held' AND hold->>'focus' IS NOT NULL"""
      .query[Int]
      .run()
      .head

  def setPriority(itemId: com.histmersion.domain.automation.managers.ManagerRepo.SetPriorityItemId, priority: com.histmersion.domain.automation.managers.ManagerRepo.SetPriorityPriority)(using DbCon): com.histmersion.domain.automation.managers.ManagerRepo.SetPriorityResult =
    (
      sql"UPDATE manager_items SET priority = $priority, updated_at = now() WHERE id = $itemId".update.run() > 0
    )

  /** Settle an item that is not active (skip: stopped by a person; a manager's stop: cancelled). */
  def settle(itemId: com.histmersion.domain.automation.managers.ManagerRepo.SettleItemId, status: ItemStatus, outcome: com.histmersion.domain.automation.managers.ManagerRepo.SettleOutcome, from: List[ItemStatus])(using DbCon): com.histmersion.domain.automation.managers.ManagerRepo.SettleResult =
    val fs = from.map(_.wire)
    sql"""UPDATE manager_items SET status = ${status.wire}, outcome = $outcome, hold = NULL, updated_at = now()
          WHERE id = $itemId AND status = ANY($fs::text[])""".update.run() > 0

  /** Re-open a settled item: pending again, its run count and failures reset, how often recorded. */
  def reopen(itemId: com.histmersion.domain.automation.managers.ManagerRepo.ReopenItemId)(using DbCon): com.histmersion.domain.automation.managers.ManagerRepo.ReopenResult =
    sql"""UPDATE manager_items SET status = 'pending', attempts = 0, failures = 0, not_before = NULL, outcome = NULL,
                                   hold = NULL,
                                   state = jsonb_set(state - 'history', '{reopened}',
                                     to_jsonb(coalesce((state->>'reopened')::int, 0) + 1)),
                                   updated_at = now()
          WHERE id = $itemId AND status IN ('done', 'stopped', 'failed', 'cancelled')""".update.run() > 0

  /** A converged item selected again because it went stale: pending at `priority` (exactly: the lowest), its runs
    * counted from zero, its run history dropped (a new cycle), why in its outcome.
    */
  def restale(itemId: com.histmersion.domain.automation.managers.ManagerRepo.RestaleItemId, priority: com.histmersion.domain.automation.managers.ManagerRepo.RestalePriority, why: com.histmersion.domain.automation.managers.ManagerRepo.RestaleWhy)(using DbCon): com.histmersion.domain.automation.managers.ManagerRepo.RestaleResult =
    sql"""UPDATE manager_items SET status = 'pending', attempts = 0, failures = 0, not_before = NULL, hold = NULL,
                                   priority = $priority, origin = ${ItemOrigin.Selection.wire}, outcome = $why,
                                   state = state - 'history', updated_at = now()
          WHERE id = $itemId AND status = 'done'""".update.run() > 0

  /** A manager's open items (pending, held, active) as (kind, target) — what
    * [[com.histmersion.domain.ManagerPolicy.waitsFor]] reads.
    */
  def openKeys(managerId: com.histmersion.domain.automation.managers.ManagerRepo.OpenKeysManagerId)(using DbCon): List[(com.histmersion.domain.automation.managers.ManagerRepo.OpenKeysResultPart1, com.histmersion.domain.automation.managers.ManagerRepo.OpenKeysResultPart2)] =
    sql"""SELECT kind, target FROM manager_items WHERE manager_id = $managerId
          AND status IN ('pending', 'held', 'active')""".query[(String, String)].run().toList

  /** Does manager `managerId` have (or ever had) an item of `kind` on `target`? */
  def hasItem(managerId: com.histmersion.domain.automation.managers.ManagerRepo.HasItemManagerId, kind: com.histmersion.domain.automation.managers.ManagerRepo.HasItemKind, target: com.histmersion.domain.automation.managers.ManagerRepo.HasItemTarget)(using DbCon): com.histmersion.domain.automation.managers.ManagerRepo.HasItemResult =
    sql"""SELECT 1 FROM manager_items WHERE manager_id = $managerId AND kind = $kind AND target = $target"""
      .query[Int]
      .run()
      .nonEmpty

  /** Every open item of a manager cancelled (a stop); returns how many. */
  def cancelOpen(managerId: com.histmersion.domain.automation.managers.ManagerRepo.CancelOpenManagerId, why: com.histmersion.domain.automation.managers.ManagerRepo.CancelOpenWhy)(using DbCon): com.histmersion.domain.automation.managers.ManagerRepo.CancelOpenResult =
    sql"""UPDATE manager_items SET status = 'cancelled', outcome = $why, updated_at = now()
          WHERE manager_id = $managerId AND status IN ('pending', 'held', 'active')""".update.run()

  /** Back to pending (an active item whose run vanished, or was never submitted). */
  def requeue(itemId: com.histmersion.domain.automation.managers.ManagerRepo.RequeueItemId, outcome: com.histmersion.domain.automation.managers.ManagerRepo.RequeueOutcome)(using DbCon): com.histmersion.domain.automation.managers.ManagerRepo.RequeueResult =
    sql"""UPDATE manager_items SET status = 'pending', run_id = NULL, outcome = $outcome, updated_at = now()
          WHERE id = $itemId AND status = 'active'""".update.run() > 0

  // ── selection: what the review manager picks by itself ─────────────────────────────────────────

  /** Articles with open MAJORs in their review ledger and no item of this manager yet, the most open MAJORs first;
    * `tags` (all of) and `goal` (linked to it) narrow them.
    */
  def openMajorArticles(managerId: com.histmersion.domain.automation.managers.ManagerRepo.OpenMajorArticlesManagerId, kind: com.histmersion.domain.automation.managers.ManagerRepo.OpenMajorArticlesKind, tags: List[com.histmersion.domain.automation.managers.ManagerRepo.OpenMajorArticlesTags], goal: Option[com.histmersion.domain.automation.managers.ManagerRepo.OpenMajorArticlesGoal], limit: com.histmersion.domain.automation.managers.ManagerRepo.OpenMajorArticlesLimit)(using
      DbCon
  ): List[(com.histmersion.domain.automation.managers.ManagerRepo.OpenMajorArticlesResultPart1, com.histmersion.domain.automation.managers.ManagerRepo.OpenMajorArticlesResultPart2)] =
    sql"""SELECT a.slug, m.n FROM articles a
          JOIN review_ledgers l ON l.slug = a.slug
          CROSS JOIN LATERAL (
            SELECT count(*)::int AS n FROM jsonb_array_elements(coalesce(l.ledger->'entries', '[]'::jsonb)) e
            WHERE coalesce(e->>'status', 'open') = 'open' AND e->'finding'->>'severity' = 'major') m
          WHERE m.n > 0 AND a.kind <> 'index'
            AND a.tags @> $tags::text[]
            AND ($goal::text IS NULL OR EXISTS (SELECT 1 FROM goal_links g WHERE g.goal_slug = $goal
                                                 AND g.kind = 'article' AND g.ref = a.slug))
            AND NOT EXISTS (SELECT 1 FROM manager_items i WHERE i.manager_id = $managerId AND i.kind = $kind
                            AND i.target = a.slug)
          ORDER BY m.n DESC, a.slug LIMIT ${math.max(1, limit)}""".query[(String, Int)].run().toList

  /** Articles never reviewed (no ledger, no review-article run) and with no item of this manager. */
  def unreviewedArticles(managerId: com.histmersion.domain.automation.managers.ManagerRepo.UnreviewedArticlesManagerId, kind: com.histmersion.domain.automation.managers.ManagerRepo.UnreviewedArticlesKind, tags: List[com.histmersion.domain.automation.managers.ManagerRepo.UnreviewedArticlesTags], goal: Option[com.histmersion.domain.automation.managers.ManagerRepo.UnreviewedArticlesGoal], limit: com.histmersion.domain.automation.managers.ManagerRepo.UnreviewedArticlesLimit)(using
      DbCon
  ): List[com.histmersion.domain.automation.managers.ManagerRepo.UnreviewedArticlesResult] =
    sql"""SELECT a.slug FROM articles a
          WHERE a.kind <> 'index' AND a.tags @> $tags::text[]
            AND NOT EXISTS (SELECT 1 FROM review_ledgers l WHERE l.slug = a.slug)
            AND NOT EXISTS (SELECT 1 FROM jobs j WHERE j.kind = $JobKind AND j.pipeline = 'review-article'
                            AND j.target_id = a.slug)
            AND ($goal::text IS NULL OR EXISTS (SELECT 1 FROM goal_links g WHERE g.goal_slug = $goal
                                                 AND g.kind = 'article' AND g.ref = a.slug))
            AND NOT EXISTS (SELECT 1 FROM manager_items i WHERE i.manager_id = $managerId AND i.kind = $kind
                            AND i.target = a.slug)
          ORDER BY a.updated_at, a.slug LIMIT ${math.max(1, limit)}""".query[String].run().toList

  /** The review manager's stale candidates: its converged review items (by when they converged: their item's
    * `updated_at`, with the filter hits recorded then), then articles whose ledger holds no open MAJOR and that no item
    * of it drives (by the ledger's `updated_at`) — oldest first, `tags`/`goal` narrowing as in the selection. (slug,
    * converged at, filter hits then, the done item if any).
    */
  def staleCandidates(managerId: com.histmersion.domain.automation.managers.ManagerRepo.StaleCandidatesManagerId, kind: com.histmersion.domain.automation.managers.ManagerRepo.StaleCandidatesKind, tags: List[com.histmersion.domain.automation.managers.ManagerRepo.StaleCandidatesTags], goal: Option[com.histmersion.domain.automation.managers.ManagerRepo.StaleCandidatesGoal], limit: com.histmersion.domain.automation.managers.ManagerRepo.StaleCandidatesLimit)(using
      DbCon
  ): List[(com.histmersion.domain.automation.managers.ManagerRepo.StaleCandidatesResultPart1, com.histmersion.domain.automation.managers.ManagerRepo.StaleCandidatesResultPart2, Option[com.histmersion.domain.automation.managers.ManagerRepo.StaleCandidatesResultPart3], Option[com.histmersion.domain.automation.managers.ManagerRepo.StaleCandidatesResultPart4])] =
    val lim = math.max(1, limit)
    val g = goal.map(_.trim).getOrElse("")
    val done = Sql
      .of(
        s"""SELECT i.target, ${at("i.updated_at")}, (i.state->>'filterHits')::int, i.id FROM manager_items i
          JOIN articles a ON a.slug = i.target
          WHERE i.manager_id = ? AND i.kind = ? AND i.status = 'done' AND a.tags @> ?::text[]
            AND (? = '' OR EXISTS (SELECT 1 FROM goal_links g WHERE g.goal_slug = ?
                                   AND g.kind = 'article' AND g.ref = a.slug))
          ORDER BY i.updated_at, i.id LIMIT ?""",
        Param.S(managerId),
        Param.S(kind),
        Param.A(tags),
        Param.S(g),
        Param.S(g),
        Param.I(lim)
      )
      .frag
      .query[(String, String, Option[Int], Long)]
      .run()
      .toList
      .map((s, t, n, id) => (s, t, n, Some(id)))
    val ledgers = Sql
      .of(
        s"""SELECT l.slug, ${at("l.updated_at")} FROM review_ledgers l JOIN articles a ON a.slug = l.slug
          WHERE a.kind <> 'index' AND a.tags @> ?::text[]
            AND NOT EXISTS (SELECT 1 FROM jsonb_array_elements(coalesce(l.ledger->'entries', '[]'::jsonb)) e
                            WHERE coalesce(e->>'status', 'open') = 'open' AND e->'finding'->>'severity' = 'major')
            AND (? = '' OR EXISTS (SELECT 1 FROM goal_links g WHERE g.goal_slug = ?
                                   AND g.kind = 'article' AND g.ref = a.slug))
            AND NOT EXISTS (SELECT 1 FROM manager_items i WHERE i.manager_id = ? AND i.kind = ? AND i.target = a.slug)
          ORDER BY l.updated_at, l.slug LIMIT ?""",
        Param.A(tags),
        Param.S(g),
        Param.S(g),
        Param.S(managerId),
        Param.S(kind),
        Param.I(lim)
      )
      .frag
      .query[(String, String)]
      .run()
      .toList
      .map((s, t) => (s, t, None, None))
    (done ++ ledgers).sortBy(_._2).take(lim)

  /** Articles that record a coverage filter and that no item of this manager has open (pending, held, active), with
    * when their last extension item settled (None: never extended) — the ones never extended first, then the longest
    * ago; `tags`/`goal` narrow as in the selection.
    */
  def extendCandidates(managerId: com.histmersion.domain.automation.managers.ManagerRepo.ExtendCandidatesManagerId, tags: List[com.histmersion.domain.automation.managers.ManagerRepo.ExtendCandidatesTags], goal: Option[com.histmersion.domain.automation.managers.ManagerRepo.ExtendCandidatesGoal], limit: com.histmersion.domain.automation.managers.ManagerRepo.ExtendCandidatesLimit)(using
      DbCon
  ): List[(com.histmersion.domain.automation.managers.ManagerRepo.ExtendCandidatesResultPart1, Option[com.histmersion.domain.automation.managers.ManagerRepo.ExtendCandidatesResultPart2])] =
    val g = goal.map(_.trim).getOrElse("")
    Sql
      .of(
        s"""SELECT a.slug, ${at("x.updated_at")} FROM articles a
          LEFT JOIN manager_items x ON x.manager_id = ? AND x.kind = ? AND x.target = a.slug
          WHERE a.coverage_filter IS NOT NULL AND a.kind <> 'index' AND a.tags @> ?::text[]
            AND (? = '' OR EXISTS (SELECT 1 FROM goal_links gl WHERE gl.goal_slug = ?
                                   AND gl.kind = 'article' AND gl.ref = a.slug))
            AND NOT EXISTS (SELECT 1 FROM manager_items i WHERE i.manager_id = ? AND i.target = a.slug
                            AND i.status IN ('pending', 'held', 'active'))
          ORDER BY x.updated_at NULLS FIRST, a.slug LIMIT ?""",
        Param.S(managerId),
        Param.S(ArticleExtend),
        Param.A(tags),
        Param.S(g),
        Param.S(g),
        Param.S(managerId),
        Param.I(math.max(1, limit))
      )
      .frag
      .query[(String, Option[String])]
      .run()
      .toList

  // ── the autopilot's views (few queries for every manager at once) ──────────────────────────────

  def light()(using DbCon): List[ManagerLight] =
    Sql
      .of(
        s"""SELECT m.id, m.kind, m.goal_slug, m.state, m.cron, m.config::text, m.status_note,
                      EXISTS (SELECT 1 FROM manager_items i WHERE i.manager_id = m.id AND i.kind = ?)
               FROM agent_managers m ORDER BY m.id""",
        Param.S(PlanGoalItem)
      )
      .frag
      .query[ManagerLight]
      .run()
      .toList

  /** Per manager: its open items (pending, held, active), the items it settled since `since`, and when it last recorded
    * an event. A watcher's ingest backlog is not counted: it drains by the day's ingest caps, not at the pace of its
    * runs (it has its own estimate, [[com.histmersion.domain.IngestBacklog.daysToDrain]]).
    */
  def pace(since: java.time.Instant)(using DbCon): Map[com.histmersion.domain.automation.managers.ManagerRepo.PaceResultPart1, (com.histmersion.domain.automation.managers.ManagerRepo.PaceResultPart2Part1, com.histmersion.domain.automation.managers.ManagerRepo.PaceResultPart2Part2, Option[com.histmersion.domain.automation.managers.ManagerRepo.PaceResultPart2Part3])] =
    val t = since.toString
    Sql
      .of(
        s"""SELECT m.id,
                      (SELECT count(*)::int FROM manager_items i WHERE i.manager_id = m.id AND i.kind <> ?
                         AND i.status IN ('pending', 'held', 'active')),
                      (SELECT count(*)::int FROM manager_items i WHERE i.manager_id = m.id AND i.kind <> ?
                         AND i.status IN ('done', 'stopped', 'failed') AND i.updated_at >= ?::timestamptz),
                      (SELECT ${at("max(e.at)")} FROM manager_events e WHERE e.manager_id = m.id)
               FROM agent_managers m""",
        Param.S(OaIngest),
        Param.S(OaIngest),
        Param.S(t)
      )
      .frag
      .query[(String, Int, Int, Option[String])]
      .run()
      .toList
      .map((id, o, d, l) => id -> (o, d, l))
      .toMap

  /** Open items of `kind` in manager `id` (pending, held or active) — e.g. a goal's compiles. `focusHeld` = an item the
    * focus holds counts as held (false: as the pending item it really is).
    */
  def openOf(
      managerId: com.histmersion.domain.automation.managers.ManagerRepo.OpenOfManagerId,
      kinds: List[com.histmersion.domain.automation.managers.ManagerRepo.OpenOfKinds],
      statuses: List[com.histmersion.domain.automation.managers.ManagerRepo.OpenOfStatuses],
      focusHeld: com.histmersion.domain.automation.managers.ManagerRepo.OpenOfFocusHeld = true
  )(using DbCon): com.histmersion.domain.automation.managers.ManagerRepo.OpenOfResult =

    Sql
      .of(
        "SELECT count(*)::int FROM manager_items WHERE manager_id = ? AND kind = ANY(?::text[]) AND status = ANY(?::text[])" +
          (if focusHeld then "" else " AND NOT (status = 'held' AND hold->>'focus' IS NOT NULL)"),
        Param.S(managerId),
        Param.A(kinds),
        Param.A(statuses)
      )
      .frag
      .query[Int]
      .run()
      .head

  /** Open items on `target` in manager `id`, any kind. */
  def openOn(managerId: com.histmersion.domain.automation.managers.ManagerRepo.OpenOnManagerId, target: com.histmersion.domain.automation.managers.ManagerRepo.OpenOnTarget)(using DbCon): com.histmersion.domain.automation.managers.ManagerRepo.OpenOnResult =
    (sql"""SELECT 1 FROM manager_items WHERE manager_id = $managerId AND target = $target
                      AND status IN ('pending', 'held', 'active') LIMIT 1""".query[Int].run().nonEmpty)

  /** An article's tags and the goals it is linked to (what a hint's effect matches). */
  def articleFacets(slug: com.histmersion.domain.automation.managers.ManagerRepo.ArticleFacetsSlug)(using DbCon): (List[com.histmersion.domain.automation.managers.ManagerRepo.ArticleFacetsResultPart1], List[com.histmersion.domain.automation.managers.ManagerRepo.ArticleFacetsResultPart2]) =
    val tags = sql"SELECT tags FROM articles WHERE slug = $slug".query[List[String]].run().headOption.getOrElse(Nil)
    val goals =
      sql"SELECT goal_slug FROM goal_links WHERE kind = 'article' AND ref = $slug".query[String].run().toList
    (tags, goals)

  /** Is there a source with this citekey or DOI (a hold waiting for it is released)? */
  def sourceExists(ref: com.histmersion.domain.automation.managers.ManagerRepo.SourceExistsRef)(using DbCon): com.histmersion.domain.automation.managers.ManagerRepo.SourceExistsResult =
    val r = ref.trim
    val doi = r.toLowerCase.stripPrefix("https://doi.org/").stripPrefix("doi:")
    (
      sql"""SELECT 1 FROM sources WHERE citekey = $r OR lower(doi) = $doi LIMIT 1""".query[Int].run().nonEmpty
    )

  // ── the watchers' ingest backlog (`oa-ingest` items) ───────────────────────────────────────────

  def backlogPendingIn()(using DbCon): List[ManagerItem] =
    (itemSelect ++ Sql.of(
      """ WHERE kind = ? AND status = 'pending'
          AND manager_id IN (SELECT id FROM agent_managers WHERE kind = 'watch' AND state = 'active')
        ORDER BY priority DESC, id""",
      Param.S(OaIngest)
    )).frag.query[ItemRow].run().toList.map(toItem)

  def backlogOpenIn()(using DbCon): List[ManagerItem] =
    (itemSelect ++ Sql.of(
      " WHERE kind = ? AND status IN ('pending', 'held', 'active') ORDER BY id",
      Param.S(OaIngest)
    )).frag.query[ItemRow].run().toList.map(toItem)

  def backlogOpenForIn(doi: com.histmersion.domain.library.Doi)(using DbCon): Option[ManagerItem] =
    (itemSelect ++ Sql.of(
      " WHERE kind = ? AND target = ? AND status IN ('pending', 'held', 'active') ORDER BY id LIMIT 1",
      Param.S(OaIngest),
      Param.S(doi)
    )).frag.query[ItemRow].run().headOption.map(toItem)

  /** Per watcher: its backlog items by status (pending, held) — what the status lines count. */
  def backlogCounts()(using DbCon): Map[com.histmersion.domain.automation.managers.ManagerRepo.BacklogCountsResultPart1, (com.histmersion.domain.automation.managers.ManagerRepo.BacklogCountsResultPart2Part1, com.histmersion.domain.automation.managers.ManagerRepo.BacklogCountsResultPart2Part2)] =
    sql"""SELECT manager_id, count(*) FILTER (WHERE status = 'pending')::int, count(*) FILTER (WHERE status = 'held')::int
          FROM manager_items WHERE kind = $OaIngest AND status IN ('pending', 'held') GROUP BY manager_id"""
      .query[(String, Int, Int)]
      .run()
      .toList
      .map((m, p, h) => m -> (p, h))
      .toMap

  def countEventsByIn(kind: com.histmersion.domain.automation.managers.ManagerRepo.CountEventsByInKind, since: java.time.Instant)(using DbCon): Map[com.histmersion.domain.automation.managers.ManagerRepo.CountEventsByInResultPart1, com.histmersion.domain.automation.managers.ManagerRepo.CountEventsByInResultPart2] =
    val at = since.toString
    sql"""SELECT manager_id, count(*)::int FROM manager_events WHERE kind = $kind AND at >= $at::timestamptz
          GROUP BY manager_id""".query[(String, Int)].run().toMap

  def setItemStateIn(itemId: com.histmersion.domain.automation.managers.ManagerRepo.SetItemStateInItemId, state: ItemState, from: List[ItemStatus])(using DbCon): com.histmersion.domain.automation.managers.ManagerRepo.SetItemStateInResult =
    val fs = from.map(_.wire)
    sql"""UPDATE manager_items SET state = $state::jsonb, updated_at = now()
          WHERE id = $itemId AND status = ANY($fs::text[])""".update.run() > 0

  def backlogRetryIn(itemId: com.histmersion.domain.automation.managers.ManagerRepo.BacklogRetryInItemId, failures: com.histmersion.domain.automation.managers.ManagerRepo.BacklogRetryInFailures, notBefore: java.time.Instant, outcome: com.histmersion.domain.automation.managers.ManagerRepo.BacklogRetryInOutcome)(using
      DbCon
  ): com.histmersion.domain.automation.managers.ManagerRepo.BacklogRetryInResult =
    val nb = notBefore.toString
    sql"""UPDATE manager_items SET failures = $failures, attempts = attempts + 1, not_before = $nb::timestamptz,
                                   outcome = $outcome, updated_at = now()
          WHERE id = $itemId AND status = 'pending'""".update.run() > 0

  /** The citekey of the source holding this DOI, if one does. */
  def citekeyOfDoi(doi: com.histmersion.domain.library.Doi)(using DbCon): Option[com.histmersion.domain.automation.managers.ManagerRepo.CitekeyOfDoiResult] =
    val d = doi.trim.toLowerCase
    (
      sql"SELECT citekey FROM sources WHERE lower(doi) = $d ORDER BY citekey LIMIT 1".query[String].run().headOption
    )

  /** One open acquisition item a WATCHER'S SWEEP created, found by structure, not by its wording: an
    * `acquisition-sweep` run dispatched by a watcher (`jobs.manager_id`) picked the DOI (its `rank` step's checked
    * answer), the item was created while that run's `queue` step ran (the only step that adds acquisition items), and
    * the run linked it to its goal by that DOI (`goal_links` kind `queue-item`, note = the DOI). A person's item, a
    * plan's, a currency check's match none of the three. `isOa` = the ranking marked the work open access.
    */

  def sweepAcquireItemsIn()(using DbCon): List[SweepAcquireItem] =
    sql"""WITH picks AS (
              SELECT j.id AS job, j.target_id AS goal, j.manager_id, lower(p->'candidate'->>'doi') AS doi,
                     coalesce((p->'candidate'->>'isOa')::boolean, false) AS oa, p->'candidate'->>'title' AS title,
                     (p->'candidate'->>'year')::int AS year,
                     ARRAY(SELECT jsonb_array_elements_text(coalesce(p->'candidate'->'authors', '[]'::jsonb))) AS authors,
                     p->'candidate'->>'venue' AS venue, p->>'why' AS why
              FROM jobs j
              JOIN agent_steps s ON s.job_id = j.id AND s.name = 'rank' AND s.status = 'done'
              CROSS JOIN LATERAL jsonb_array_elements(CASE WHEN jsonb_typeof(s.output) = 'array' THEN s.output
                                                            ELSE '[]'::jsonb END) p
              WHERE j.kind = $JobKind AND j.pipeline = 'acquisition-sweep' AND j.manager_id LIKE 'watch:%')
            SELECT DISTINCT ON (q.id, p.goal) q.id, q.text, q.topic, p.goal, p.manager_id, p.job, p.doi, p.oa, p.title,
                   p.year, p.authors, p.venue, p.why
            FROM queue_items q
            JOIN goal_links gl ON gl.kind = 'queue-item' AND gl.ref = q.id::text
            JOIN picks p ON p.goal = gl.goal_slug AND lower(gl.note) = p.doi
            JOIN agent_steps qs ON qs.job_id = p.job AND qs.name = 'queue' AND qs.finished_at IS NOT NULL
                               AND q.created_at BETWEEN qs.started_at - interval '2 seconds'
                                                    AND qs.finished_at + interval '2 seconds'
            WHERE q.list = 'acquire' AND q.status = 'open'
            ORDER BY q.id, p.goal, p.oa DESC, p.job""".query[SweepAcquireItem].run().toList

  /** How many acquisition items are open. */
  def openAcquireCount(using DbCon): com.histmersion.domain.automation.managers.ManagerRepo.OpenAcquireCountResult =
    (
      sql"SELECT count(*)::int FROM queue_items WHERE list = 'acquire' AND status = 'open'".query[Int].run().head
    )

  // ── events ─────────────────────────────────────────────────────────────────────────────────────

  private given DbCodec[ManagerEvent] = DbCodec.derived

  private val eventSelect =
    Sql.raw(s"SELECT id, manager_id, item_id, run_id, ${at("at")}, kind, text FROM manager_events")

  def eventIn(managerId: com.histmersion.domain.automation.managers.ManagerRepo.EventInManagerId, itemId: Option[com.histmersion.domain.automation.managers.ManagerRepo.EventInItemId], runId: Option[com.histmersion.domain.automation.Run.Id], kind: com.histmersion.domain.automation.managers.ManagerRepo.EventInKind, text: com.histmersion.domain.automation.managers.ManagerRepo.EventInText)(using
      DbCon
  ): Unit =
    val _ = sql"""INSERT INTO manager_events (manager_id, item_id, run_id, kind, text)
                  VALUES ($managerId, $itemId, $runId, $kind, ${text.take(2000)})""".update.run()

  def event(
      managerId: com.histmersion.domain.automation.managers.ManagerRepo.EventManagerId,
      itemId: Option[com.histmersion.domain.automation.managers.ManagerRepo.EventItemId],
      runId: Option[com.histmersion.domain.automation.Run.Id],
      kind: com.histmersion.domain.automation.managers.ManagerRepo.EventKind,
      text: com.histmersion.domain.automation.managers.ManagerRepo.EventText
  )(using DbCon): Unit =
    eventIn(managerId, itemId, runId, kind, text)

  /** Newest first; `before` = only events older than that id. */
  def events(managerId: com.histmersion.domain.automation.managers.ManagerRepo.EventsManagerId, before: Option[com.histmersion.domain.automation.managers.ManagerRepo.EventsBefore], limit: com.histmersion.domain.automation.managers.ManagerRepo.EventsLimit)(using DbCon): List[ManagerEvent] =
    (eventSelect ++ Sql.of(" WHERE manager_id = ?", Param.S(managerId)) ++
      before.fold(Sql.empty)(b => Sql.of(" AND id < ?", Param.L(b))) ++
      Sql.of(" ORDER BY id DESC LIMIT ?", Param.I(math.max(1, limit)))).frag.query[ManagerEvent].run().toList

  def lastEvent(managerId: com.histmersion.domain.automation.managers.ManagerRepo.LastEventManagerId)(using DbCon): Option[ManagerEvent] = events(managerId, None, 1).headOption

  /** How many events of `kind` a manager has recorded since `since` (a watcher's ingests today). */
  def countEvents(managerId: com.histmersion.domain.automation.managers.ManagerRepo.CountEventsManagerId, kind: com.histmersion.domain.automation.managers.ManagerRepo.CountEventsKind, since: java.time.Instant)(using DbCon): com.histmersion.domain.automation.managers.ManagerRepo.CountEventsResult =
    val at = since.toString
    (sql"""SELECT count(*)::int FROM manager_events WHERE manager_id = $managerId AND kind = $kind
                      AND at >= $at::timestamptz""".query[Int].run().head)

  /** How many events of `kind` ALL managers recorded since `since` (every watcher's ingests today: the autopilot's
    * shared cap).
    */
  def countEventsAll(kind: com.histmersion.domain.automation.managers.ManagerRepo.CountEventsAllKind, since: java.time.Instant)(using DbCon): com.histmersion.domain.automation.managers.ManagerRepo.CountEventsAllResult =
    val at = since.toString

    sql"""SELECT count(*)::int FROM manager_events WHERE kind = $kind AND at >= $at::timestamptz"""
      .query[Int]
      .run()
      .head

  /** The newest event of `kind` (a starved note is logged once per change). */
  def lastEventOf(managerId: com.histmersion.domain.automation.managers.ManagerRepo.LastEventOfManagerId, kind: com.histmersion.domain.automation.managers.ManagerRepo.LastEventOfKind)(using DbCon): Option[ManagerEvent] =

    (eventSelect ++ Sql.of(
      " WHERE manager_id = ? AND kind = ? ORDER BY id DESC LIMIT 1",
      Param.S(managerId),
      Param.S(kind)
    )).frag.query[ManagerEvent].run().headOption

  def pruneEvents(days: com.histmersion.domain.automation.managers.ManagerRepo.PruneEventsDays)(using DbCon): com.histmersion.domain.automation.managers.ManagerRepo.PruneEventsResult =
    if days <= 0 then 0
    else (sql"DELETE FROM manager_events WHERE at < now() - make_interval(days => $days)".update.run())

  // ── hints ──────────────────────────────────────────────────────────────────────────────────────

  final private case class HintRow(
      id: HintRow.Id,
      managerId: HintRow.ManagerId,
      itemId: Option[HintRow.ItemId],
      text: HintRow.Text,
      effect: Option[HintEffect],
      author: HintRow.Author,
      standing: HintRow.Standing,
      createdAt: HintRow.CreatedAt,
      retiredAt: Option[HintRow.RetiredAt],
      uses: HintRow.Uses,
      proposal: Option[HintRow.Proposal],
      proposed: Option[HintEffect]
  ) derives DbCodec

  object HintRow:
    type Id = Id.T
    object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ManagerId = ManagerId.T
    object ManagerId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ItemId = ItemId.T
    object ItemId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Text = Text.T
    object Text extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Author = Author.T
    object Author extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Standing = Standing.T
    object Standing extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CreatedAt = CreatedAt.T
    object CreatedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RetiredAt = RetiredAt.T
    object RetiredAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Uses = Uses.T
    object Uses extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Proposal = Proposal.T
    object Proposal extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** A hint, with the newest OPEN `hint-effect` approval about it (a model's proposed action waiting for a person) and
    * that action.
    */
  private val hintSelect = Sql.raw(
    s"""SELECT h.id, h.manager_id, h.item_id, h.text, h.effect::text, h.author, h.standing, ${at("h.created_at")},
              ${at("h.retired_at")}, (SELECT count(*)::int FROM manager_hint_uses u WHERE u.hint_id = h.id),
              p.id, (p.proposal->'effect')::text
       FROM manager_hints h
       LEFT JOIN LATERAL (SELECT a.id, a.proposal FROM approvals a
                          WHERE a.kind = 'hint-effect' AND a.state = 'open' AND a.subject = h.manager_id
                            AND (a.proposal->>'hint')::bigint = h.id ORDER BY a.id DESC LIMIT 1) p ON true"""
  )

  private def toHint(r: HintRow): ManagerHint =
    ManagerHint(
      r.id,
      r.managerId,
      r.itemId,
      r.text,
      r.effect,
      r.author,
      r.standing,
      r.createdAt,
      r.retiredAt,
      r.uses,
      r.proposal,
      r.proposed
    )

  def addHint(
      managerId: com.histmersion.domain.automation.managers.ManagerRepo.AddHintManagerId,
      itemId: Option[com.histmersion.domain.automation.managers.ManagerRepo.AddHintItemId],
      text: com.histmersion.domain.automation.managers.ManagerRepo.AddHintText,
      effect: Option[HintEffect],
      author: com.histmersion.domain.automation.managers.ManagerRepo.AddHintAuthor,
      standing: com.histmersion.domain.automation.managers.ManagerRepo.AddHintStanding
  )(using DbCon): ManagerHint =
    val id =
      sql"""INSERT INTO manager_hints (manager_id, item_id, text, effect, author, standing)
            VALUES ($managerId, $itemId, $text, $effect::jsonb, $author, $standing) RETURNING id"""
        .query[Long]
        .run()
        .head
    hint(id).get

  def hint(id: com.histmersion.domain.automation.managers.ManagerRepo.HintId)(using DbCon): Option[ManagerHint] =
    ((hintSelect ++ Sql.of(" WHERE h.id = ?", Param.L(id))).frag.query[HintRow].run().headOption.map(toHint))

  /** A manager's hints, oldest first; `live` = only the ones not retired. */
  def hints(managerId: com.histmersion.domain.automation.managers.ManagerRepo.HintsManagerId, live: com.histmersion.domain.automation.managers.ManagerRepo.HintsLive)(using DbCon): List[ManagerHint] =
    (hintSelect ++ Sql.of(" WHERE h.manager_id = ?", Param.S(managerId)) ++
      (if live then Sql.of(" AND h.retired_at IS NULL") else Sql.empty) ++ Sql
        .of(" ORDER BY h.id")).frag.query[HintRow].run().toList.map(toHint)

  /** The action a hint carries now (a model's proposal a person approved). */
  def setHintEffect(id: com.histmersion.domain.automation.managers.ManagerRepo.SetHintEffectId, effect: Option[HintEffect])(using DbCon): com.histmersion.domain.automation.managers.ManagerRepo.SetHintEffectResult =
    (sql"UPDATE manager_hints SET effect = $effect::jsonb WHERE id = $id".update.run() > 0)

  def retireHint(managerId: com.histmersion.domain.automation.managers.ManagerRepo.RetireHintManagerId, id: com.histmersion.domain.automation.managers.ManagerRepo.RetireHintId)(using DbCon): com.histmersion.domain.automation.managers.ManagerRepo.RetireHintResult =
    sql"""UPDATE manager_hints SET retired_at = now() WHERE id = $id AND manager_id = $managerId
          AND retired_at IS NULL""".update.run() > 0

  /** In the dispatch transaction: the hints a run was given, and the once-hints among them retired. */
  def usedIn(hints: List[ManagerHint], runId: com.histmersion.domain.automation.Run.Id)(using DbCon): Unit =
    hints.foreach { h =>
      sql"""INSERT INTO manager_hint_uses (hint_id, run_id) VALUES (${h.id}, $runId) ON CONFLICT DO NOTHING""".update
        .run()
      if !h.standing then
        sql"UPDATE manager_hints SET retired_at = now() WHERE id = ${h.id} AND retired_at IS NULL".update.run()
    }
