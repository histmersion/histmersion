package com.histmersion
package plugin

import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** What one call answered: text (the final answer is JSON matching the answer schema), the tools the model called (the
  * host runs them and calls again), why it stopped, its usage, and the model that actually served it, when the provider
  * says.
  */
final case class AiTurn(
    text: AiTurn.Text,
    toolCalls: List[AiTool.Call] = Nil,
    stop: AiStop = AiStop.EndTurn,
    usage: Option[AiUsage] = None,
    servedBy: Option[AiTurn.ServedBy] = None
)

object AiTurn:

  /** One model call.
    *
    * @param model
    *   the model id, as the reference names it after the colon
    * @param system
    *   the system prompt (the host's; carries no untrusted data)
    * @param messages
    *   the conversation so far, the user's message first
    * @param tools
    *   the tools offered on THIS call (none on the last allowed iteration, so the model answers instead of calling a
    *   tool whose result would be thrown away)
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
    *   the same schema as the host's loop renders it, its `$schema` marker kept (`answerSchema` drops it): for a
    *   provider that must send it exactly so
    * @param answerDescription
    *   what the answer is, when the step's schema says (a provider's structured-output field for it)
    */
  type Text = Text.T
  object Text extends RefinedType[String, Pure]
  type ServedBy = ServedBy.T
  object ServedBy extends RefinedType[String, Pure]

  final case class Request(
      model: Request.Model,
      system: Request.System,
      messages: List[AiMessage],
      tools: List[AiTool],
      answerSchema: Option[Request.AnswerSchema],
      maxTokens: Request.MaxTokens,
      iteration: Request.Iteration,
      maxIterations: Request.MaxIterations,
      answerSchemaRendered: Option[Request.AnswerSchemaRendered] = None,
      answerDescription: Option[Request.AnswerDescription] = None
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
    ) = this(Request.Model(model), Request.System(system), messages, tools, (answerSchema).map(refactorValue => Request.AnswerSchema(refactorValue)), Request.MaxTokens(maxTokens), Request.Iteration(iteration), Request.MaxIterations(maxIterations), None, None)

  object Request:
    /** The request as a host or a test compiled before the rendered schema and its description were passed wrote it. */
    type ApplyModel = Model
    val ApplyModel: Model.type = Model
    type ApplySystem = System
    val ApplySystem: System.type = System
    type ApplyAnswerSchema = AnswerSchema
    val ApplyAnswerSchema: AnswerSchema.type = AnswerSchema
    type ApplyMaxTokens = MaxTokens
    val ApplyMaxTokens: MaxTokens.type = MaxTokens
    type ApplyIteration = Iteration
    val ApplyIteration: Iteration.type = Iteration
    type ApplyMaxIterations = MaxIterations
    val ApplyMaxIterations: MaxIterations.type = MaxIterations

    type Model = Model.T
    object Model extends RefinedType[String, Pure]
    type System = System.T
    object System extends RefinedType[String, Pure]
    type AnswerSchema = AnswerSchema.T
    object AnswerSchema extends RefinedType[String, Pure]
    type MaxTokens = MaxTokens.T
    object MaxTokens extends RefinedType[Int, Pure]
    type Iteration = Iteration.T
    object Iteration extends RefinedType[Int, Pure]
    type MaxIterations = MaxIterations.T
    object MaxIterations extends RefinedType[Int, Pure]
    type AnswerSchemaRendered = AnswerSchemaRendered.T
    object AnswerSchemaRendered extends RefinedType[String, Pure]
    type AnswerDescription = AnswerDescription.T
    object AnswerDescription extends RefinedType[String, Pure]

    def apply(
        model: ApplyModel,
        system: ApplySystem,
        messages: List[AiMessage],
        tools: List[AiTool],
        answerSchema: Option[ApplyAnswerSchema],
        maxTokens: ApplyMaxTokens,
        iteration: ApplyIteration,
        maxIterations: ApplyMaxIterations
    ): Request =
      new Request(model, system, messages, tools, answerSchema, maxTokens, iteration, maxIterations, None, None)
