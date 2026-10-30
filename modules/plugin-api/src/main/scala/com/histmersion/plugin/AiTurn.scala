package com.histmersion.plugin

/** What one call answered: text (the final answer is JSON matching the answer schema), the tools the model called (the
  * host runs them and calls again), why it stopped, its usage, and the model that actually served it, when the provider
  * says.
  */
final case class AiTurn(
    text: String,
    toolCalls: List[AiToolCall] = Nil,
    stop: AiStop = AiStop.EndTurn,
    usage: Option[AiUsage] = None,
    servedBy: Option[String] = None
)
