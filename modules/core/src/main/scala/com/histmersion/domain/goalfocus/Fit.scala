package com.histmersion
package domain.goalfocus

import hearth.kindlings.optics.syntax.*
import com.histmersion.domain.goalfocus.Stage
import com.histmersion.domain.GoalFocus.Rationale
import com.histmersion.domain.GoalFocus

enum Fit:
  case Finished
  case Candidate(stage: Stage)
  case Waits(code: Fit.Waits.Code, why: Rationale)

object Fit:
  object Waits:
    export GoalFocus.WaitCode as Code
