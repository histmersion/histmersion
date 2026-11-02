package com.histmersion.api.automation
import com.histmersion.api.given

object GoalStages:
  val all: List[(String, String)] = List(
    com.histmersion.domain.automation.Goal.Status.Planned -> "created; nothing works on it yet",
    com.histmersion.domain.automation.Goal.Status.Seeding -> "finding and reading sources, writing its articles",
    com.histmersion.domain.automation.Goal.Status.Compiled -> "its articles are written; not all have passed review yet",
    com.histmersion.domain.automation.Goal.Status.Reviewed -> "every article (and sub-goal) passed the review panel",
    com.histmersion.domain.automation.Goal.Status.Done -> "finished: only you set this, the autopilot never does"
  )

  def meaning(status: String): Option[String] = all.collectFirst { case (s, m) if s == status => m }
