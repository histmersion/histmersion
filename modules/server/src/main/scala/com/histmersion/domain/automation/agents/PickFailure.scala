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
  object Unavailable:
    type Message = Message.T
    object Message extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  object Exhausted:
    type Message = Message.T
    object Message extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
