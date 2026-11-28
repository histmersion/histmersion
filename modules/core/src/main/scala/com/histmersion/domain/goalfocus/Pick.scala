package com.histmersion
package domain.goalfocus

import hearth.kindlings.optics.syntax.*
import com.histmersion.domain.automation.Goal
import com.histmersion.domain.goalfocus.Stage
import com.histmersion.domain.GoalFocus.Rationale
import com.histmersion.domain.goalfocus.Facts

final case class Pick(
    slug: Goal.Slug,
    stage: Stage,
    why: Rationale,
    pinned: Facts.Pinned = Facts.Pinned(false),
    since: Option[Facts.Since] = None
)
