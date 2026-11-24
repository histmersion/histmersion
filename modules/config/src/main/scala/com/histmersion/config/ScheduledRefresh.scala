package com.histmersion
package config

import com.histmersion.util.problem.Problem
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{FeaturePlace, movesTo}
import com.histmersion.lint.{LintRule, tolerated}

/** How often a plan pool is read while the autopilot is on and queued work wants it, besides the reading a run's own
  * pick takes.
  */
@movesTo(FeaturePlace.Config)
enum ScheduledRefresh:
  case Off
  case Every(interval: RefreshIntervalMinutes)

object ScheduledRefresh:

  /** The configured minutes: 0 = off. */

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def fromMinutes(minutes: Minutes): Either[Problem, ScheduledRefresh] =
    if minutes.value == 0 then Right(Off)
    else
      RefreshIntervalMinutes
        .checked(minutes.value)
        .map(Every(_))
        .left
        .map(Problem.Invalid.malformed)
