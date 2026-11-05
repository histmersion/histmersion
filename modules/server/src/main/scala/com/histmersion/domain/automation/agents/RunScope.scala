package com.histmersion.domain.automation.agents

import sttp.ai.core.agent.*

import scala.concurrent.duration.*

/** Which run and step an LLM call belongs to — what a run token is bound to. */
final case class RunScope(run: RunScope.Run, step: RunScope.Step)

object RunScope:
  type Run = Run.T
  object Run extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Step = Step.T
  object Step extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
