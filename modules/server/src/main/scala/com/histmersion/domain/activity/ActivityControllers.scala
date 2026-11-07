package com.histmersion.domain.activity

import com.histmersion.api.automation.Activity

import java.time.Instant
import com.histmersion.api.Api

/** `GET /api/activity`, the `activity` tool and `histmersion activity`: the filters checked, then a page. */
final class ActivityControllers(activity: ActivityService, now: () => Instant):

  def page(
      show: Option[com.histmersion.domain.activity.ActivityQuery.Show],
      since: Option[com.histmersion.domain.activity.ActivityQuery.Since],
      goal: Option[com.histmersion.domain.activity.ActivityQuery.Goal],
      limit: Option[com.histmersion.domain.activity.ActivityQuery.Limit],
      before: Option[com.histmersion.domain.activity.ActivityCursor.Wire]
  ): Either[Api.Error, Activity.Page] =
    val at = now()
    ActivityRules.query(show, since, goal, limit, before, at).map(activity.page(_, at))

