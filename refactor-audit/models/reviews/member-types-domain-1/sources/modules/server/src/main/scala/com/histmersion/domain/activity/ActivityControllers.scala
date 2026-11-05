package com.histmersion.domain.activity

import com.histmersion.api.automation.Activity

import java.time.Instant
import com.histmersion.api.Api

/** `GET /api/activity`, the `activity` tool and `histmersion activity`: the filters checked, then a page. */
final class ActivityControllers(activity: ActivityService, now: () => Instant):

  def page(
      show: Option[com.histmersion.domain.activity.ActivityControllers.PageShow],
      since: Option[com.histmersion.domain.activity.ActivityControllers.PageSince],
      goal: Option[com.histmersion.domain.activity.ActivityControllers.PageGoal],
      limit: Option[com.histmersion.domain.activity.ActivityControllers.PageLimit],
      before: Option[com.histmersion.domain.activity.ActivityControllers.PageBefore]
  ): Either[Api.Error, Activity.Page] =
    val at = now()
    ActivityRules.query(show, since, goal, limit, before, at).map(activity.page(_, at))

object ActivityControllers:
  type PageShow = PageShow.T
  object PageShow extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PageSince = PageSince.T
  object PageSince extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PageGoal = PageGoal.T
  object PageGoal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PageLimit = PageLimit.T
  object PageLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PageBefore = PageBefore.T
  object PageBefore extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
