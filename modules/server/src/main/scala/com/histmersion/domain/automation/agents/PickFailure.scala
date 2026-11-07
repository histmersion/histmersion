package com.histmersion.domain.automation.agents

import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.automation.Agents.*
import com.histmersion.api.automation.ModelChains.*

import java.time.Instant

/** Why no model of a role's chain can take the next call. `Exhausted` pauses the run until `freesAt` (resumed by itself
  * then); `Unavailable` is a configuration problem (no chain, no priced or buildable model) and fails the step as
  * before chains existed.
  */
enum PickFailure:
  case Unavailable(message: PickFailure.Unavailable.Message)
  case Exhausted(message: PickFailure.Exhausted.Message, freesAt: Option[Instant], reason: PauseReason = PauseReason.Allowance)

object PickFailure:
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  object Unavailable:
    type Message = com.histmersion.domain.automation.agents.PickFailure.Problem
    val Message: _root_.com.histmersion.domain.automation.agents.PickFailure.Problem.type = _root_.com.histmersion.domain.automation.agents.PickFailure.Problem
  object Exhausted:
    type Message = com.histmersion.domain.automation.agents.PickFailure.Problem
    val Message: _root_.com.histmersion.domain.automation.agents.PickFailure.Problem.type = _root_.com.histmersion.domain.automation.agents.PickFailure.Problem
