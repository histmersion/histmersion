package com.histmersion.plugin

/** One model call.
  *
  * @param model
  *   the model id, as the reference names it after the colon
  * @param system
  *   the system prompt (the host's; carries no untrusted data)
  * @param messages
  *   the conversation so far, the user's message first
  * @param tools
  *   the tools offered on THIS call (none on the last allowed iteration, so the model answers instead of calling a tool
  *   whose result would be thrown away)
  * @param answerSchema
  *   the JSON Schema (text) the final answer must match; a provider without native structured output puts it in the
  *   prompt — the host decodes and validates anyway
  * @param maxTokens
  *   the most output tokens this call may produce (`agents.step.max-tokens`)
  * @param iteration
  *   this call's place in the step's loop (1-based) …
  * @param maxIterations
  *   … and the most calls the step may make
  */
final case class AiTurnRequest(
    model: String,
    system: String,
    messages: List[AiMessage],
    tools: List[AiTool],
    answerSchema: Option[String],
    maxTokens: Int,
    iteration: Int,
    maxIterations: Int
)
