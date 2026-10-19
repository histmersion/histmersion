package com.histmersion.server.agents

import com.histmersion.core.{HttpPolicy, Version}
import com.histmersion.core.Agents.ModelRef
import com.histmersion.plugin.*
import io.circe.syntax.*
import sttp.ai.core.agent.*
import sttp.ai.core.model.{AIModel, Capability}
import sttp.apispec.circe.*
import sttp.client4.Backend
import sttp.shared.Identity

import java.nio.charset.StandardCharsets
import scala.jdk.CollectionConverters.*
import scala.util.control.NonFatal

/** The model type a plugin provider's calls are built with: every capability, as the provider (not
  * sttp-ai) decides what it can do. */
private[agents] object PluginModel extends AIModel with Capability.All:
  def value: String = "plugin"

/** A PLUGIN's AI provider as the engine sees it (docs/research-plugins.md §22): sttp-ai's own agent loop
  * over a backend that asks the provider for ONE turn at a time. So the host keeps everything but the
  * model call — the tools (run in process under the step's allowlist), the interceptors (trace, budget,
  * LLM slots, the first-call probe, the allowance guard: each sees every turn), the typed answer's
  * decoding — exactly as for the sttp-ai providers. */
final class PluginAiClient(val model: ModelRef, provider: AiProvider, http: AiHttp) extends LlmClient:

  def run[Out](call: LlmCall[Out]): AgentResult[Either[AgentFailure, Out]] =
    val builder = AgentBuilder[Identity, PluginModel.type](conf => PluginAiClient.Turns(model, provider, http, conf))(
      using sttp.monad.IdentityMonad)
    LlmClient.configure(builder, call).run(call.user)(PluginAiClient.NoHttp)

object PluginAiClient:

  /** The sttp backend the loop is handed: the provider sends through [[AiHttp]], so it is never used. */
  private[agents] val NoHttp: Backend[Identity] = sttp.client4.testing.SyncBackendStub

  /** The client for `ref`, or why it cannot be used here: a provider that spends the person's own plan
    * beyond loopback, or one whose own check says no (no key, no URL). */
  def client(ref: ModelRef, provider: AiProvider, http: AiHttp, loopback: Boolean): Either[String, LlmClient] =
    if provider.spendsPersonalPlan && !loopback then
      Left(s"${ref.provider.wire} is refused while the server listens beyond loopback: it spends your own plan, " +
        "and another client could spend it")
    else
      val checked = try provider.check(ref.model) catch case NonFatal(e) => Left(s"its check threw ${e.getClass.getSimpleName}")
      checked.left.map(why => s"${ref.wire}: $why").map(_ => PluginAiClient(ref, provider, http))

  /** Pure: the conversation sttp-ai keeps → the provider's messages (iteration markers dropped). */
  def messages(h: ConversationHistory): List[AiMessage] =
    h.entries.toList.collect {
      case ConversationEntry.UserPrompt(t)             => AiMessage.User(t)
      case ConversationEntry.AssistantResponse(t, tcs) => AiMessage.Assistant(t, tcs.toList.map(c => AiToolCall(c.id, c.toolName, c.input)))
      case ConversationEntry.ToolResult(id, name, out) => AiMessage.ToolResult(id, name, out)
    }

  /** Pure: a tool as the provider is told about it. */
  def tool(t: AgentTool[Identity, ?]): AiTool =
    AiTool(t.name, t.description, t.rawJsonSchema.noSpaces)

  /** Pure: the answer's JSON Schema (text), the `$schema` marker dropped. */
  def schemaJson(s: ResponseSchema[?]): String =
    s.schema.asJson.deepDropNullValues.mapObject(_.remove("$schema")).noSpaces

  /** Pure: the provider's answer → sttp-ai's. */
  def response(t: AiTurn, model: ModelRef): AgentResponse =
    val stop = t.stop match
      case AiStop.EndTurn   => if t.toolCalls.nonEmpty then StopReason.ToolUse else StopReason.EndTurn
      case AiStop.ToolUse   => StopReason.ToolUse
      case AiStop.MaxTokens => StopReason.MaxTokens
      case AiStop.Other     => StopReason.EndTurn
    val usage = t.usage.map(u => TokenUsage(Tokens(math.max(0L, u.input)), Tokens(math.max(0L, u.output)),
      Tokens(math.max(0L, u.cacheRead)), Tokens.Zero, Tokens(math.max(0L, u.cacheWrite))))
    AgentResponse(Option(t.text).getOrElse(""), t.toolCalls.map(c => ToolCall(c.id, c.tool, c.input)), stop, usage,
      t.servedBy.map(_.trim).filter(_.nonEmpty).orElse(Some(model.model)))

  /** One step's turns: each `sendRequest` is one [[AiProvider.turn]]. A provider's error, exception or
    * `null` is a provider failure (an `IOException`: the run pauses with `provider`); its plan's own
    * limit is [[ModelLimitReached]] (the chain's next model takes over). */
  private[agents] final class Turns(model: ModelRef, provider: AiProvider, http: AiHttp, conf: AgentConfig[Identity])
      extends AgentBackend[Identity]:
    def tools: Seq[AgentTool[Identity, ?]] = conf.userTools
    def systemPrompt: Option[String] = conf.systemPrompt

    def sendRequest(history: ConversationHistory, backend: Backend[Identity], includeTools: Boolean,
        iterationInfo: IterationInfo): Identity[AgentResponse] =
      val offered = if includeTools && provider.capabilities.tools then conf.userTools.toList.map(tool) else Nil
      val req = AiTurnRequest(model.model, conf.systemPrompt.getOrElse(""), messages(history), offered,
        conf.responseSchema.map(schemaJson), conf.maxTokens.getOrElse(0), iterationInfo.iteration, iterationInfo.maxIterations)
      val answer =
        try Option(provider.turn(req, http)).getOrElse(Left(AiFailure.Error("the provider answered null")))
        catch
          case e: InterruptedException => throw e
          case NonFatal(e) => Left(AiFailure.Error(s"${e.getClass.getSimpleName}: ${Option(e.getMessage).getOrElse("")}"))
      answer match
        case Right(t) if t != null => response(t, model)
        case Right(_) => throw java.io.IOException(s"${model.wire}: the provider answered null")
        case Left(AiFailure.LimitReached(m, until)) =>
          throw ModelLimitReached(model, until.map(java.time.Instant.ofEpochMilli), s"${model.wire}: $m".take(300))
        case Left(AiFailure.Error(m)) => throw java.io.IOException(s"${model.wire}: $m".take(600))

/** The host's [[AiHttp]]: java.net.http over HTTP/1.1 (as every client of the server), the host's
  * User-Agent (no e-mail can reach it, [[HttpPolicy.userAgent]]), a timeout per call, and nothing sent
  * or received logged. */
final class HostAiHttp extends AiHttp:
  /** Built on the first call (an `HttpClient` starts a selector thread): a server that never calls a
    * plugin provider starts none. */
  private val built = java.util.concurrent.atomic.AtomicReference[java.net.http.HttpClient]()

  private def client: java.net.http.HttpClient =
    Option(built.get).getOrElse {
      val c = java.net.http.HttpClient.newBuilder()
        .version(java.net.http.HttpClient.Version.HTTP_1_1)
        .connectTimeout(java.time.Duration.ofSeconds(30))
        .build()
      if built.compareAndSet(null, c) then c else built.get
    }

  private def userAgent = HttpPolicy.userAgent(s"Histmersion/${Version.version} (agents)")

  def post(url: String, headers: Map[String, String], body: String, timeoutSeconds: Int): Either[String, AiHttpResponse] =
    send(url, headers, timeoutSeconds, _.POST(java.net.http.HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)))

  def get(url: String, headers: Map[String, String], timeoutSeconds: Int): Either[String, AiHttpResponse] =
    send(url, headers, timeoutSeconds, _.GET())

  private def send(url: String, headers: Map[String, String], timeoutSeconds: Int,
      method: java.net.http.HttpRequest.Builder => java.net.http.HttpRequest.Builder): Either[String, AiHttpResponse] =
    try
      val b = java.net.http.HttpRequest.newBuilder(java.net.URI.create(url))
        .timeout(java.time.Duration.ofSeconds(math.max(1, timeoutSeconds).toLong))
      headers.foreach((k, v) => if !k.equalsIgnoreCase("user-agent") then { val _ = b.header(k, v) })
      val _ = b.header("User-Agent", userAgent)
      val r = client.send(method(b).build(), java.net.http.HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
      Right(AiHttpResponse(r.statusCode, r.body,
        r.headers.map.asScala.toMap.collect { case (k, v) if !v.isEmpty => k.toLowerCase(java.util.Locale.ROOT) -> v.get(0) }))
    catch
      case e: InterruptedException => throw e
      // the message only: never the request, which may carry a key in a header
      case NonFatal(e) => Left(s"${e.getClass.getSimpleName}: ${Option(e.getMessage).getOrElse("")}".take(300))
