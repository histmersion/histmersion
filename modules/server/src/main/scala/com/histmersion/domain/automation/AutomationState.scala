package com.histmersion.domain.automation

import com.histmersion.domain.automation.autopilot.AutopilotRowState
import com.histmersion.domain.automation.goals.{GoalLinkRecord, GoalRecord, GoalScheduleRecord}

import java.time.Instant
import java.time.temporal.ChronoUnit

/** The engine's rows held in memory, one record the `InMemory` services of its features share (each reads and changes
  * its own part, through lenses): the autopilot's row; the goals, their links and the schedules not moved yet.
  */
final case class AutomationState(
    autopilot: Option[AutopilotRowState],
    goals: Map[String, GoalRecord],
    links: Vector[GoalLinkRecord],
    schedules: Vector[GoalScheduleRecord]
)

object AutomationState:
  val empty: AutomationState = AutomationState(None, Map.empty, Vector.empty, Vector.empty)

  /** An instant as the repositories read one back (`to_char` to the second, UTC). */
  def text(at: Instant): String = at.truncatedTo(ChronoUnit.SECONDS).toString
