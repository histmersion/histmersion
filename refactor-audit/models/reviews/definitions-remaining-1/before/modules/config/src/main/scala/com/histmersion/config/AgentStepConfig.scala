package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader

/** One LLM step's loop: iterations of the tool loop, output tokens per call, and how often a typed answer that fails
  * code validation is retried with the validator's errors appended.
  */
final case class AgentStepConfig(maxIterations: Int, maxTokens: Int, maxRetries: Int) derives ConfigReader
