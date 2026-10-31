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
  * @param answerSchemaRendered
  *   the same schema as the host's loop renders it, its `$schema` marker kept (`answerSchema` drops it): for a provider
  *   that must send it exactly so
  * @param answerDescription
  *   what the answer is, when the step's schema says (a provider's structured-output field for it)
  */
final case class AiTurnRequest(
    model: String,
    system: String,
    messages: List[AiMessage],
    tools: List[AiTool],
    answerSchema: Option[String],
    maxTokens: Int,
    iteration: Int,
    maxIterations: Int,
    answerSchemaRendered: Option[String] = None,
    answerDescription: Option[String] = None
):
  /** The constructor of a host or a test compiled before the rendered schema and its description were passed. */
  def this(
      model: String,
      system: String,
      messages: List[AiMessage],
      tools: List[AiTool],
      answerSchema: Option[String],
      maxTokens: Int,
      iteration: Int,
      maxIterations: Int
  ) = this(model, system, messages, tools, answerSchema, maxTokens, iteration, maxIterations, None, None)

object AiTurnRequest:
  /** The request as a host or a test compiled before the rendered schema and its description were passed wrote it. */
  def apply(
      model: String,
      system: String,
      messages: List[AiMessage],
      tools: List[AiTool],
      answerSchema: Option[String],
      maxTokens: Int,
      iteration: Int,
      maxIterations: Int
  ): AiTurnRequest =
    new AiTurnRequest(model, system, messages, tools, answerSchema, maxTokens, iteration, maxIterations, None, None)
