package com.histmersion
package domain.activity

import com.histmersion.api.automation.Activity

import java.time.Instant
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{FeaturePlace, movesTo}
import com.histmersion.lint.{LintRule, tolerated}

/** One page of the activity list asked for: what to show, from when on, of which research goal, how many entries, and
  * older than which.
  */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
@movesTo(FeaturePlace.AutomationActivity)
final case class ActivityQuery(
    show: Activity.Show = Activity.Show.Everything,
    since: Option[Instant] = None,
    goal: Option[ActivityQuery.Goal] = None,
    limit: ActivityQuery.Limit = ActivityQuery.Limit(Activity.DefaultLimit),
    before: Option[ActivityCursor] = None
)

object ActivityQuery:
  type Show = Show.T
  object Show extends Newtype[String, Pure]
  type Since = Since.T
  object Since extends Newtype[String, Pure]
  type Goal = Goal.T
  object Goal extends Newtype[String, Pure]
  type Limit = Limit.T
  object Limit extends Newtype[Int, Pure]
