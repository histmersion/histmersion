package com.histmersion.domain.automation.agents

import com.histmersion.api.automation.Agents.ModelRef
import sttp.ai.core.agent.*

import scala.concurrent.duration.*

/** The model's PLAN said no (the `claude-code` CLI's "usage limit reached"): not a broken provider but an exhausted one
  * — the engine skips the model until `until` (or `limit-cooldown-minutes`) and tries the chain's next one.
  */
final class ModelLimitReached(val model: ModelRef, val until: Option[java.time.Instant], message: ModelLimitReached.Message)
    extends RuntimeException(message)

object ModelLimitReached:
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Message = com.histmersion.domain.automation.agents.ModelLimitReached.Problem
  val Message: _root_.com.histmersion.domain.automation.agents.ModelLimitReached.Problem.type = _root_.com.histmersion.domain.automation.agents.ModelLimitReached.Problem
