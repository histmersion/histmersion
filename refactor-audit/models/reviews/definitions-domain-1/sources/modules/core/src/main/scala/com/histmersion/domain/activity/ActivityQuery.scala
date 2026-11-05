package com.histmersion.domain.activity

import com.histmersion.api.automation.Activity

import java.time.Instant

/** One page of the activity list asked for: what to show, from when on, of which research goal, how many entries, and
  * older than which.
  */
final case class ActivityQuery(
    show: Activity.Show = Activity.Show.Everything,
    since: Option[Instant] = None,
    goal: Option[ActivityQuery.Goal] = None,
    limit: ActivityQuery.Limit = Activity.DefaultLimit,
    before: Option[ActivityCursor] = None
)

object ActivityQuery:
  type Goal = Goal.T
  object Goal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Limit = Limit.T
  object Limit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
