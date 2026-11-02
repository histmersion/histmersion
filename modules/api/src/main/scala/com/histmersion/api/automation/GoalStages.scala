package com.histmersion.api.automation
import com.histmersion.values.GoalStatus
import com.histmersion.api.given

object GoalStages:
  val all: List[(String, String)] = List(
    GoalStatus.Planned -> "created; nothing works on it yet",
    GoalStatus.Seeding -> "finding and reading sources, writing its articles",
    GoalStatus.Compiled -> "its articles are written; not all have passed review yet",
    GoalStatus.Reviewed -> "every article (and sub-goal) passed the review panel",
    GoalStatus.Done -> "finished: only you set this, the autopilot never does"
  )

  def meaning(status: String): Option[String] = all.collectFirst { case (s, m) if s == status => m }
