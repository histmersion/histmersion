package com.histmersion.domain.automation

import com.histmersion.domain.automation.agents.{ApprovalRecord, MessageRecordState, StepRecordState}
import com.histmersion.domain.automation.autopilot.AutopilotRowState
import com.histmersion.domain.automation.goals.{FocusRecord, GoalLinkRecord, GoalRecord, GoalScheduleRecord}
import com.histmersion.domain.automation.managers.{ManagerEventRecord, ManagerItemRecord, ManagerRecord}

import java.time.Instant
import java.time.temporal.ChronoUnit

/** The engine's rows held in memory, one record the `InMemory` services of its features share (each reads and changes
  * its own part, through lenses): the autopilot's row; the goals, their links, the schedules not moved yet and what the
  * focus recorded; the approvals.
  */
final case class AutomationState(
    autopilot: Option[AutopilotRowState],
    goals: Map[String, GoalRecord],
    links: Vector[GoalLinkRecord],
    schedules: Vector[GoalScheduleRecord],
    focus: Map[String, FocusRecord],
    approvals: Vector[ApprovalRecord],
    lastApprovalId: Long,
    steps: Vector[StepRecordState],
    messages: Vector[MessageRecordState],
    managers: Vector[ManagerRecord],
    items: Vector[ManagerItemRecord],
    lastItemId: Long,
    events: Vector[ManagerEventRecord],
    lastEventId: Long
)

object AutomationState:
  val empty: AutomationState =
    AutomationState(
      None,
      Map.empty,
      Vector.empty,
      Vector.empty,
      Map.empty,
      Vector.empty,
      0L,
      Vector.empty,
      Vector.empty,
      Vector.empty,
      Vector.empty,
      0L,
      Vector.empty,
      0L
    )

  /** An instant as the repositories read one back (`to_char` to the second, UTC). */
  def text(at: Instant): String = at.truncatedTo(ChronoUnit.SECONDS).toString
