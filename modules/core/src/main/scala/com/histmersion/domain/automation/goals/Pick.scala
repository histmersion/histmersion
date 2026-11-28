package com.histmersion
package domain.automation.goals

import hearth.kindlings.optics.syntax.*
import com.histmersion.domain.automation.Goal
import com.histmersion.domain.automation.goals.Stage
import com.histmersion.domain.GoalFocus.Rationale
import com.histmersion.domain.automation.goals.Facts

final case class Pick(
    slug: Goal.Slug,
    stage: Stage,
    why: Rationale,
    pinned: Facts.Pinned = Facts.Pinned(false),
    since: Option[Facts.Since] = None
)
