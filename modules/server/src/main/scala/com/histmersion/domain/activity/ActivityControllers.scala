package com.histmersion.domain.activity

import com.histmersion.api.{Activity, ApiError}

import java.time.Instant

/** `GET /api/activity`, the `activity` tool and `histmersion activity`: the filters checked, then a page. */
final class ActivityControllers(activity: ActivityService, now: () => Instant):

  def page(
      show: Option[String],
      since: Option[String],
      goal: Option[String],
      limit: Option[Int],
      before: Option[String]
  ): Either[ApiError, Activity.Page] =
    val at = now()
    ActivityRules.query(show, since, goal, limit, before, at).map(activity.page(_, at))
