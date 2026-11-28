package com.histmersion
package domain.goalfocus

import hearth.kindlings.optics.syntax.*
import com.histmersion.domain.automation.Goal
import com.histmersion.domain.goalfocus.Work
import com.histmersion.domain.GoalFocus.Rationale

final case class Holds(hold: List[(Work.ItemId, Goal.Slug, Rationale)] = Nil, release: List[Work.ItemId] = Nil)
