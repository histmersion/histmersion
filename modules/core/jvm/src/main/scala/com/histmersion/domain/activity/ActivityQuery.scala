package com.histmersion.domain.activity

import com.histmersion.api.Activity

import java.time.Instant

/** One page of the activity list asked for: what to show, from when on, of which research goal, how many
  * entries, and older than which. */
final case class ActivityQuery(show: Activity.Show = Activity.Show.Everything, since: Option[Instant] = None,
    goal: Option[String] = None, limit: Int = Activity.DefaultLimit, before: Option[ActivityCursor] = None)
