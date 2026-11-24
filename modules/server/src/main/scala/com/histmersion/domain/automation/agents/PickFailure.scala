package com.histmersion
package domain.automation.agents
import com.histmersion.util.problem.{Problem, Problems}

import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.automation.Agents.*
import com.histmersion.api.automation.ModelChains.*

import java.time.Instant
import io.github.iltotore.iron.constraint.any.Pure

/** Why no model of a role's chain can take the next call. `Exhausted` pauses the run until `freesAt` (resumed by itself
  * then); `Unavailable` is a configuration problem (no chain, no priced or buildable model) and fails the step as
  * before chains existed.
  */
enum PickFailure:
  case Unavailable(problems: Problems)
  case Exhausted(
      message: Problem.Run.NoModel,
      freesAt: Option[Instant],
      reason: PauseReason = PauseReason.Allowance
  )
