package com.histmersion
package domain

import io.github.iltotore.iron.constraint.any.Pure
import java.time.Instant
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule

import java.time.Duration
import com.histmersion.lint.{movesTo, FeaturePlace}

@movesTo(FeaturePlace.AutomationScheduler)
enum ReadSchedule:
  case Off
  case Every(interval: Duration)

object ReadSchedule:
  type Rationale = Rationale.T
  object Rationale extends Newtype[String, Pure]

  type AutopilotOn = AutopilotOn.T
  object AutopilotOn extends Newtype[Boolean, Pure]

  type Wanted = Wanted.T
  object Wanted extends Newtype[Boolean, Pure]

  extension (schedule: ReadSchedule)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def scheduledRead(
        autopilotOn: AutopilotOn,
        wanted: Wanted,
        newestReading: Option[Instant],
        lastAttempt: Option[Instant],
        now: Instant
    ): ScheduledRead =
      schedule match
        case Off             => ScheduledRead.NotDue(Rationale("the schedule is off"))
        case Every(interval) =>
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
