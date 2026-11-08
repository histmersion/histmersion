package com.histmersion
package domain.automation.agents

import sttp.ai.core.agent.*

import scala.concurrent.duration.*
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** Which run and step an LLM call belongs to — what a run token is bound to. */
final case class RunScope(run: RunScope.Run, step: RunScope.Step)

object RunScope:
  type Run = Run.T
  object Run extends RefinedType[String, Pure]
  type Step = Step.T
  object Step extends RefinedType[Int, Pure]
