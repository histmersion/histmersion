package com.histmersion.domain.automation

import com.histmersion.domain.automation.agents.{ApprovalRecord, MessageRecordState, StepRecordState}
import com.histmersion.domain.automation.autopilot.AutopilotRowState
import com.histmersion.domain.automation.goals.{FocusRecord, GoalLinkRecord, GoalRecord, GoalScheduleRecord}
import com.histmersion.domain.automation.managers.{ManagerEventRecord, ManagerItemRecord, ManagerRecord}

import java.time.Instant
import java.time.temporal.ChronoUnit

/** The engine's rows held in memory, one record the `InMemory` services of its features share (each reads and changes
  * its own part, through lenses): the autopilot's row; the goals, their links, the schedules not moved yet and what the
  * focus recorded, the goals' old slugs; the approvals; the agents' scratch state.
  */
final case class AutomationState(
    autopilot: Option[AutopilotRowState],
    goals: Map[AutomationState.GoalsKey, GoalRecord],
    links: Vector[GoalLinkRecord],
    schedules: Vector[GoalScheduleRecord],
    focus: Map[AutomationState.FocusKey, FocusRecord],
    approvals: Vector[ApprovalRecord],
    lastApprovalId: AutomationState.LastApprovalId,
    steps: Vector[StepRecordState],
    messages: Vector[MessageRecordState],
    managers: Vector[ManagerRecord],
    items: Vector[ManagerItemRecord],
    lastItemId: AutomationState.LastItemId,
    events: Vector[ManagerEventRecord],
    lastEventId: AutomationState.LastEventId,
    goalAliases: Map[AutomationState.GoalAliasesKey, AutomationState.GoalAliasesValue] = Map.empty,
    agentState: Map[AutomationState.AgentStateKey, com.histmersion.domain.automation.agents.AgentStateRecord] = Map.empty
)

object AutomationState:
  type Timestamp = Timestamp.T
  object Timestamp extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  type GoalsKey = GoalsKey.T
  object GoalsKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FocusKey = FocusKey.T
  object FocusKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LastApprovalId = LastApprovalId.T
  object LastApprovalId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LastItemId = LastItemId.T
  object LastItemId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LastEventId = LastEventId.T
  object LastEventId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GoalAliasesKey = GoalAliasesKey.T
  object GoalAliasesKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GoalAliasesValue = GoalAliasesValue.T
  object GoalAliasesValue extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AgentStateKey = AgentStateKey.T
  object AgentStateKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  import hearth.kindlings.optics.syntax.*

  def insertApproval(state: java.util.concurrent.atomic.AtomicReference[AutomationState])(conflicts: ApprovalRecord => Boolean)(make: com.histmersion.domain.automation.Approval.Id => ApprovalRecord): Option[com.histmersion.domain.automation.Approval.Id] =
    var made: Option[com.histmersion.domain.automation.Approval.Id] = None
    val _ = state.updateAndGet { s =>
      if s.approvals.exists(conflicts) then { made = None; s }
      else
        val id = s.lastApprovalId + 1
        made = Some(id)
        s.modify(_.approvals).using(_ :+ make(id)).modify(_.lastApprovalId).setTo(id)
    }
    made

  val empty: AutomationState =
    AutomationState(
      None,
      Map.empty,
      Vector.empty,
      Vector.empty,
      Map.empty,
      Vector.empty,
      com.histmersion.domain.automation.AutomationState.LastApprovalId(0L),
      Vector.empty,
      Vector.empty,
      Vector.empty,
      Vector.empty,
      com.histmersion.domain.automation.AutomationState.LastItemId(0L),
      Vector.empty,
      com.histmersion.domain.automation.AutomationState.LastEventId(0L)
    )

  /** An instant as the repositories read one back (`to_char` to the second, UTC). */
  def text(at: Instant): com.histmersion.domain.automation.AutomationState.Timestamp = at.truncatedTo(ChronoUnit.SECONDS).toString
