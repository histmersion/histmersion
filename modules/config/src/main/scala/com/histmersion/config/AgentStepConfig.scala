package com.histmersion
package config

import com.histmersion.config.NewtypeReaders.given
import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.constraint.any.Pure

/** One LLM step's loop: iterations of the tool loop, output tokens per call, and how often a typed answer that fails
  * code validation is retried with the validator's errors appended.
  */
final case class AgentStepConfig(maxIterations: AgentStepConfig.MaxIterations, maxTokens: AgentStepConfig.MaxTokens, maxRetries: AgentStepConfig.MaxRetries) derives ConfigReader

object AgentStepConfig:
  type MaxIterations = MaxIterations.T
  object MaxIterations extends Newtype[Int, Pure]
  type MaxTokens = MaxTokens.T
  object MaxTokens extends Newtype[Int, Pure]
  type MaxRetries = MaxRetries.T
  object MaxRetries extends Newtype[Int, Pure]
