package com.histmersion.domain.automation.agents

import sttp.ai.core.agent.*

import scala.concurrent.duration.*

/** Which run and step an LLM call belongs to — what a run token is bound to. */
final case class RunScope(run: String, step: Int)
