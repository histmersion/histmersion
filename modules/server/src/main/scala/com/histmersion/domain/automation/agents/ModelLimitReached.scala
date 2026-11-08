package com.histmersion
package domain.automation.agents

import com.histmersion.api.Problem
import com.histmersion.api.automation.Agents.ModelRef
import sttp.ai.core.agent.*

import scala.concurrent.duration.*
import io.github.iltotore.iron.constraint.any.Pure

/** The model's PLAN said no (the `claude-code` CLI's "usage limit reached"): not a broken provider but an exhausted one
  * — the engine skips the model until `until` (or `limit-cooldown-minutes`) and tries the chain's next one.
  */
final class ModelLimitReached(
    val model: ModelRef,
    val until: Option[java.time.Instant],
    message: Problem
) extends RuntimeException(message.value)

