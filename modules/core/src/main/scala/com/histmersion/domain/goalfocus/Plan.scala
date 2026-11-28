package com.histmersion
package domain.goalfocus

import hearth.kindlings.optics.syntax.*
import com.histmersion.domain.automation.Goal
import com.histmersion.domain.goalfocus.Fit
import com.histmersion.domain.GoalFocus.Rationale
import com.histmersion.domain.goalfocus.Pick

final case class Plan(
    active: List[Pick] = Nil,
    queue: List[Pick] = Nil,
    joined: List[Goal.Slug] = Nil,
    left: List[(Goal.Slug, Rationale)] = Nil,
    waiting: List[(Goal.Slug, Fit.Waits.Code, Rationale)] = Nil
):
  lazy val activeSet: Set[Goal.Slug] = active.map(_.slug).toSet
