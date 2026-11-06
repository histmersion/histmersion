package com.histmersion.config

import com.histmersion.config.NewtypeReaders.given
import hearth.kindlings.sconfigderivation.ConfigReader

/** One LLM step's loop: iterations of the tool loop, output tokens per call, and how often a typed answer that fails
  * code validation is retried with the validator's errors appended.
  */
final case class AgentStepConfig(maxIterations: AgentStepConfig.MaxIterations, maxTokens: AgentStepConfig.MaxTokens, maxRetries: AgentStepConfig.MaxRetries) derives ConfigReader

object AgentStepConfig:
  type MaxIterations = MaxIterations.T
  object MaxIterations extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MaxTokens = MaxTokens.T
  object MaxTokens extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MaxRetries = MaxRetries.T
  object MaxRetries extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
