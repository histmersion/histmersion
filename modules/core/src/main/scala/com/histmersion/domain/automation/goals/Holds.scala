package com.histmersion
package domain.automation.goals

import hearth.kindlings.optics.syntax.*
import com.histmersion.domain.automation.Goal
import com.histmersion.domain.automation.goals.Work
import com.histmersion.domain.GoalFocus.Rationale

final case class Holds(hold: List[(Work.ItemId, Goal.Slug, Rationale)] = Nil, release: List[Work.ItemId] = Nil)
