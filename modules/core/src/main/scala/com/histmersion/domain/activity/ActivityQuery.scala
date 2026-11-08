package com.histmersion
package domain.activity

import com.histmersion.api.automation.Activity

import java.time.Instant
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** One page of the activity list asked for: what to show, from when on, of which research goal, how many entries, and
  * older than which.
  */
final case class ActivityQuery(
    show: Activity.Show = Activity.Show.Everything,
    since: Option[Instant] = None,
    goal: Option[ActivityQuery.Goal] = None,
    limit: ActivityQuery.Limit = ActivityQuery.Limit(Activity.DefaultLimit),
    before: Option[ActivityCursor] = None
)

object ActivityQuery:
  type Show = Show.T
  object Show extends RefinedType[String, Pure]
  type Since = Since.T
  object Since extends RefinedType[String, Pure]
  type Goal = Goal.T
  object Goal extends RefinedType[String, Pure]
  type Limit = Limit.T
  object Limit extends RefinedType[Int, Pure]
