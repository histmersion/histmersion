package com.histmersion
package domain.automation.agents

import com.histmersion.domain.automation.agents.VerbatimFailure
import com.histmersion.api.automation.Agents.ModelRef
import com.histmersion.plugin.*
import com.histmersion.pluginhost.PluginAi
import io.circe.syntax.*
import sttp.ai.core.agent.*
import sttp.ai.core.model.{AIModel, Capability}
import sttp.apispec.circe.*
import sttp.client4.Backend
import sttp.shared.Identity
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** A PLUGIN's AI provider as the engine sees it (docs/research-plugins.md §22): sttp-ai's own agent loop over a backend
  * that asks the provider for ONE turn at a time. So the host keeps everything but the model call — the tools (run in
  * process under the step's allowlist), the interceptors (trace, budget, LLM slots, the first-call probe, the allowance
  * guard: each sees every turn), the typed answer's decoding — exactly as for the sttp-ai providers.
  */
final class PluginAiClient(val model: ModelRef, provider: AiProvider, http: AiHttp) extends LlmClient:

  def run[Out](call: LlmCall[Out]): AgentResult[Either[AgentFailure, Out]] =
    val builder =
      AgentBuilder[Identity, PluginModel.type](conf => PluginAiClient.Turns(model, provider, http, conf))(using
        sttp.monad.IdentityMonad
      )
    LlmClient.configure(builder, call).run(call.user.value)(PluginAiClient.NoHttp)

object PluginAiClient:
  type Loopback = Loopback.T
  object Loopback extends RefinedType[Boolean, Pure]
  type Problem = Problem.T
  object Problem extends RefinedType[String, Pure]

  /** The sttp backend the loop is handed: the provider sends through [[AiHttp]], so it is never used. */
  private[automation] val NoHttp: Backend[Identity] = sttp.client4.testing.SyncBackendStub

  /** The client for `ref`, or why it cannot be used here: a provider that spends the person's own plan beyond loopback,
    * or one whose own check says no (no key, no URL).
    */
  def client(ref: ModelRef, provider: AiProvider, http: AiHttp, loopback: Loopback): Either[Problem, LlmClient] =
    if provider.spendsPersonalPlan.value && !loopback.value then
      Left(
        Problem(
          s"${ref.provider.wire} is refused while the server listens beyond loopback: it spends your own plan, " +
            "and another client could spend it"
        )
      )
    else
      PluginAi
        .check(provider, PluginAi.ModelName(ref.model.value))
        .left
        .map(why => Problem(if verbatim(provider) then why.value else s"${ref.wire}: $why"))
        .map(_ => PluginAiClient(ref, provider, http))

  /** Whether the provider's failures are whole descriptions ([[AiCapabilities.verbatimFailures]]); a provider whose
    * capabilities throw is taken as not.
    */
  private def verbatim(provider: AiProvider): Boolean =
    try Option(provider.capabilities).exists(_.verbatimFailures.value)
    catch case scala.util.control.NonFatal(_) => false

  /** Pure: the conversation sttp-ai keeps → the provider's messages; the loop's iteration notes kept as user messages
    * (`[Iteration 2 of 6]`, as sttp-ai's own providers send them) when `markers`, else dropped.
    */
  def messages(h: ConversationHistory, markers: AiCapabilities.IterationMarkers = AiCapabilities.IterationMarkers(false)): List[AiMessage] =
    h.entries.toList.collect {
      case ConversationEntry.IterationMarker(n, of) if markers.value =>
        AiMessage.User(AiMessage.User.Text(s"[Iteration $n of $of]"))
      case ConversationEntry.UserPrompt(t)             => AiMessage.User(AiMessage.User.Text(t))
      case ConversationEntry.AssistantResponse(t, tcs) =>
        AiMessage.Assistant(
          AiMessage.Assistant.Text(t),
          tcs.toList.map(c => AiTool.Call(AiTool.Call.Id(c.id), AiTool.Call.Tool(c.toolName), AiTool.Call.Input(c.input)))
        )
      case ConversationEntry.ToolResult(id, name, out) =>
        AiMessage.ToolResult(AiMessage.ToolResult.CallId(id), AiMessage.ToolResult.Tool(name), AiMessage.ToolResult.Output(out))
    }

  /** Pure: a tool as the provider is told about it. */
  def tool(t: AgentTool[Identity, ?]): AiTool =
    AiTool(AiTool.Name(t.name), AiTool.Description(t.description), AiTool.InputSchema(t.rawJsonSchema.noSpaces))

  /** Pure: the answer's JSON Schema (text), the `$schema` marker dropped. */
  def schemaJson(s: ResponseSchema[?]): String =
    s.schema.asJson.deepDropNullValues.mapObject(_.remove("$schema")).noSpaces

  /** Pure: the answer's JSON Schema (text) as sttp-ai's own providers render it, nulls dropped, the `$schema` marker
    * kept.
    */
  def renderedSchema(s: ResponseSchema[?]): String = s.schema.asJson.deepDropNullValues.noSpaces

  /** Pure: the provider's answer → sttp-ai's. */
  def response(t: AiTurn, model: ModelRef): AgentResponse =
    val stop = t.stop match
      case AiStop.EndTurn   => if t.toolCalls.nonEmpty then StopReason.ToolUse else StopReason.EndTurn
      case AiStop.ToolUse   => StopReason.ToolUse
      case AiStop.MaxTokens => StopReason.MaxTokens
      case AiStop.Other     => StopReason.EndTurn
    val usage = t.usage.map(u =>
      TokenUsage(
        Tokens(math.max(0L, u.input.value)),
        Tokens(math.max(0L, u.output.value)),
        Tokens(math.max(0L, u.cacheRead.value)),
        Tokens(math.max(0L, u.reasoning.value)),
        Tokens(math.max(0L, u.cacheWrite.value))
      )
    )
    AgentResponse(
      Option(t.text.value).getOrElse(""),
      t.toolCalls.map(c => ToolCall(c.id.value, c.tool.value, c.input.value)),
      stop,
      usage,
      t.servedBy.map(_.value.trim).filter(_.nonEmpty).orElse(Some(model.model.value))
    )

  /** One step's turns: each `sendRequest` is one [[AiProvider.turn]]. A provider's error, exception or `null` is a
    * provider failure (an `IOException`: the run pauses with `provider`; a [[VerbatimFailure]] for a provider whose
    * failures are whole descriptions); its plan's own limit is [[ModelLimitReached]] (the chain's next model takes
    * over).
    */
  final private[automation] class Turns(
      model: ModelRef,
      provider: AiProvider,
      http: AiHttp,
      conf: AgentConfig[Identity]
  ) extends AgentBackend[Identity]:
    def tools: Seq[AgentTool[Identity, ?]] = conf.userTools
    def systemPrompt: Option[String] = conf.systemPrompt

    def sendRequest(
        history: ConversationHistory,
        backend: Backend[Identity],
        includeTools: Boolean,
        iterationInfo: IterationInfo
    ): Identity[AgentResponse] =
      val caps = provider.capabilities
      val offered = if includeTools && caps.tools.value then conf.userTools.toList.map(tool) else Nil
      val req = AiTurn.Request(
        AiTurn.Request.Model(model.model.value),
        AiTurn.Request.System(conf.systemPrompt.getOrElse("")),
        messages(history, caps.iterationMarkers),
        offered,
        conf.responseSchema.map(s => AiTurn.Request.AnswerSchema(schemaJson(s))),
        AiTurn.Request.MaxTokens(conf.maxTokens.getOrElse(0)),
        AiTurn.Request.Iteration(iterationInfo.iteration),
        AiTurn.Request.MaxIterations(iterationInfo.maxIterations),
        conf.responseSchema.map(s => AiTurn.Request.AnswerSchemaRendered(renderedSchema(s))),
        conf.responseSchema.flatMap(_.description).map(d => AiTurn.Request.AnswerDescription(d))
      )
      PluginAi.turn(provider, req, http) match
        case Right(t)                               => response(t, model)
        case Left(AiFailure.LimitReached(m, until)) =>
          throw new ModelLimitReached(
            model,
            until.map(u => java.time.Instant.ofEpochMilli(u.value)),
            ModelLimitReached.Message(s"${model.wire}: $m".take(300))
          )
        case Left(AiFailure.Error(m)) if caps.verbatimFailures.value => throw VerbatimFailure(VerbatimFailure.Description(m.value))
        case Left(AiFailure.Error(m)) => throw java.io.IOException(s"${model.wire}: $m".take(600))

