package com.histmersion
package domain

import java.time.{Duration, Instant}
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{movesTo, FeaturePlace}
import com.histmersion.lint.{tolerated, LintRule}
import com.histmersion.TwoCases

@movesTo(FeaturePlace.AutomationScheduler)
object PlanReadSchedule:

  /** Why a pool's plan is not read now. */
  type Rationale = Rationale.T
  object Rationale extends Newtype[String, Pure]
  enum AutopilotOn derives CanEqual:
    case On, Off
  object AutopilotOn extends TwoCases[AutopilotOn]:
    extension (autopilotOn: AutopilotOn) def autopilotOn: Boolean = autopilotOn == On
  enum Wanted derives CanEqual:
    case Wanted, Unwanted
  object Wanted extends TwoCases[Wanted]:
    extension (wanted: Wanted) def wanted: Boolean = wanted == Wanted

  /** Whether the schedule takes a fresh reading of a pool now: its schedule is on, the autopilot is on, queued work
    * wants the pool, and neither its newest reading (whoever took it) nor the last attempt to take one is younger than
    * the interval.
    */

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def scheduledRead(
      schedule: ReadSchedule,
      autopilotOn: AutopilotOn,
      wanted: Wanted,
      newestReading: Option[Instant],
      lastAttempt: Option[Instant],
      now: Instant
  ): ScheduledRead =
    schedule match
      case ReadSchedule.Off             => ScheduledRead.NotDue(Rationale("the schedule is off"))
      case ReadSchedule.Every(interval) =>
        val latest = List(newestReading, lastAttempt).flatten.maxOption
        val age = latest.map(Duration.between(_, now))
        if !autopilotOn.autopilotOn then ScheduledRead.NotDue(Rationale("the autopilot is off"))
        else if !wanted.wanted then ScheduledRead.NotDue(Rationale("no queued work wants the pool"))
        else
          age.filter(_.toMinutes < interval.toMinutes) match
            case Some(a) =>
              ScheduledRead.NotDue(
                Rationale(s"read ${a.toMinutes} min ago (every ${interval.toMinutes} min)")
              )
            case None => ScheduledRead.Due
