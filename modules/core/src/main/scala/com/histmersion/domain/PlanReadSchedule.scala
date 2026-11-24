package com.histmersion
package domain

import java.time.{Duration, Instant}
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{FeaturePlace, movesTo}
import com.histmersion.lint.{LintRule, tolerated}

@movesTo(FeaturePlace.AutomationScheduler)
object PlanReadSchedule:

  /** Why a pool's plan is not read now. */
  type Rationale = Rationale.T
  object Rationale extends Newtype[String, Pure]
  type AutopilotOn = AutopilotOn.T
  object AutopilotOn extends Newtype[Boolean, Pure]
  type Wanted = Wanted.T
  object Wanted extends Newtype[Boolean, Pure]

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
        if !autopilotOn.value then ScheduledRead.NotDue(Rationale("the autopilot is off"))
        else if !wanted.value then ScheduledRead.NotDue(Rationale("no queued work wants the pool"))
        else
          age.filter(_.toMinutes < interval.toMinutes) match
            case Some(a) =>
              ScheduledRead.NotDue(
                Rationale(s"read ${a.toMinutes} min ago (every ${interval.toMinutes} min)")
              )
            case None => ScheduledRead.Due
