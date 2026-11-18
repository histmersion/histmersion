package com.histmersion
package domain.activity

import com.histmersion.api.automation.{Activity, ListFilters}

import java.time.Instant
import com.histmersion.api.Api
import com.histmersion.util.problem.Problems

/** `GET /api/activity`, the `activity` tool and `histmersion activity`: the filters checked, then a page. */
final class ActivityControllers(activity: ActivityService, now: () => Instant):

  def page(
      show: Option[Activity.Show.Wire],
      since: Option[ListFilters.TimeBound],
      goal: Option[ActivityQuery.Goal],
      limit: Option[ActivityQuery.Limit],
      before: Option[ActivityCursor.Wire]
  ): Either[Problems, Activity.Page] =
    val at = now()
    ActivityRules.query(show, since, goal, limit, before, at).map(activity.page(_, at))
