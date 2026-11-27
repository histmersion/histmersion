package com.histmersion
package domain.automation.agents
import com.histmersion.util.problem.Problem
import com.histmersion.util.problem.Text.sentence

import com.histmersion.api.automation.Agents.ModelRef
import sttp.ai.core.agent.*

import scala.concurrent.duration.*
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}

/** The model's PLAN said no (the `claude-code` CLI's "usage limit reached"): not a broken provider but an exhausted one
  * — the engine skips the model until `until` (or `limit-cooldown-minutes`) and tries the chain's next one.
  */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
final class ModelLimitReached(
    val model: ModelRef,
    val until: Option[java.time.Instant],
    val reason: Problem
) extends RuntimeException(reason.sentence)
