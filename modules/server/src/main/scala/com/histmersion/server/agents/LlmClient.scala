package com.histmersion.server.agents

import com.histmersion.core.{AgentsConfig, Agents}
import com.histmersion.core.Agents.{ModelRef, ProviderKind}
import sttp.ai.claude.ClaudeClient
import sttp.ai.claude.agent.ClaudeAgent
import sttp.ai.claude.config.ClaudeConfig
import sttp.ai.claude.models.ClaudeModel
import sttp.ai.core.agent.*
import sttp.ai.core.model.{AIModel, Capability}
import sttp.ai.openai.OpenAI
import sttp.ai.openai.agent.OpenAIAgent
import sttp.ai.openai.requests.completions.chat.ChatRequestBody.ChatCompletionModel
import sttp.client4.Backend
import sttp.client4.httpclient.HttpClientSyncBackend
import sttp.model.Uri
import sttp.shared.Identity

import scala.concurrent.duration.*

/** One LLM step's request, provider-neutral: a system prompt, ONE user message (untrusted data goes
  * here, delimited, never into the system prompt), the tools the step is allowed (its allowlist —
  * sttp-ai's loop answers "Tool not found" for anything else), the typed answer, and the loop limits.
  * `interceptors` see every LLM and tool call (tracing, the run's budget). */
final case class LlmCall[Out](
    system: String,
    user: String,
    tools: List[AgentTool[Identity, ?]],
    schema: ResponseSchema[Out],
    maxIterations: Int,
    maxTokens: Int,
    interceptors: List[AgentInterceptor[Identity]] = Nil,
    /** The canonical definition this call runs as: a CLI runtime launches it by name (the generated
      * agent carries the same prompt, rendered for that runtime), and a run token is bound to it. */
    agent: Option[AgentDef] = None,
    /** The run and step the call belongs to (None: a probe outside any run). */
    scope: Option[RunScope] = None
)

/** Which run and step an LLM call belongs to — what a run token is bound to. */
final case class RunScope(run: String, step: Int)

/** The model's PLAN said no (the `claude-code` CLI's "usage limit reached"): not a broken provider but
  * an exhausted one — the engine skips the model until `until` (or `limit-cooldown-minutes`) and tries
  * the chain's next one. */
final class ModelLimitReached(val model: ModelRef, val until: Option[java.time.Instant], message: String)
    extends RuntimeException(message)

/** The input of a step's FIRST model call — input + cache read + cache write tokens: the fixed part
  * every turn re-sends (the system prompt, the tool definitions) plus the step's own prompt. Measured
  * per step attempt so a change to that fixed part shows (`agent show`: "first call N tok").
  *
  * An HTTP provider's tool loop passes [[aroundLlmCall]] once per model call, so the first response's
  * usage is the first call's. A command-line provider runs its whole loop as ONE intercepted call
  * whose usage is the whole loop's: it [[FirstCallProbe.report]]s what its own output says (None when
  * it cannot tell) before that call returns, and the total is never taken for the first call. */
private[agents] final class FirstCallProbe extends AgentInterceptor[Identity]:
  // set once per step attempt: by the first response, or by a CLI client's report (which wins)
  private val state = java.util.concurrent.atomic.AtomicReference[Option[Option[Long]]](None)

  def tokens: Option[Long] = state.get().flatten

  def report(tokens: Option[Long]): Unit = state.set(Some(tokens))

  override def aroundLlmCall(ctx: LlmCallContext)(next: => Identity[AgentResponse]): Identity[AgentResponse] =
    val r = next
    val _ = state.compareAndSet(None, Some(r.usage.map(_.inputTokens.value).filter(_ > 0)))
    r

private[agents] object FirstCallProbe:
  /** A command-line client's reading of its first model call, to every probe among `interceptors`. */
  def report(interceptors: List[AgentInterceptor[Identity]], tokens: Option[Long]): Unit =
    interceptors.foreach { case p: FirstCallProbe => p.report(tokens); case _ => () }

/** A configured model, ready to run [[LlmCall]]s. The engine never sees a provider type: the three
  * sttp-ai builders are hidden behind [[run]], so a pinned-but-pre-1.0 library touches one file. */
trait LlmClient:
  def model: ModelRef
  def run[Out](call: LlmCall[Out]): AgentResult[Either[AgentFailure, Out]]

object LlmClient:

  /** Apply an [[LlmCall]] to any sttp-ai builder. Generic in the model type so Claude, OpenAI and the
    * test kit's scripted model all go through the same configuration. */
  def configure[M <: AIModel & Capability.All, Out](b: AgentBuilder[Identity, M, String, String],
      c: LlmCall[Out]): Agent[Identity, String, Out] =
    b.systemPrompt(c.system)
      .maxIterations(math.max(1, c.maxIterations))
      .maxTokens(math.max(1, c.maxTokens))
      .tools(c.tools)
      .interceptors(c.interceptors)
      .responseSchema(c.schema)
      .build

  /** HTTP/1.1 like every other client in the server (the netty/java.net.http HTTP/2 reuse problem);
    * the per-request read timeout is the provider's `timeout-seconds`. */
  private def httpBackend(connectSeconds: Int): Backend[Identity] =
    HttpClientSyncBackend.usingClient(
      java.net.http.HttpClient.newBuilder()
        .version(java.net.http.HttpClient.Version.HTTP_1_1)
        .connectTimeout(java.time.Duration.ofSeconds(connectSeconds.toLong))
        .build())

  /** How the engine builds a client for a model (the seam a test fills with sttp-ai's scripted model). */
  type Factory = (AgentsConfig, ModelRef) => Either[String, LlmClient]

  /** The server's factory, resolving the provider through `providers` — the AI-provider registry of the
    * plugin generation the calling thread is pinned to (docs/research-plugins.md §22): a host provider
    * (its built-in plugin on) runs today's client — the HTTP providers from `agents.providers.*`, the
    * CLI providers (`claude-code`, `codex`, `opencode`) on the generated trees, run tokens and plan pools
    * of `rt`; a plugin's provider runs through the host's tool loop ([[PluginAiClient]]); a switched-off
    * or unregistered one is refused, saying which. */
  def factory(rt: CliRuntime, providers: () => scala.collection.immutable.ListMap[String, com.histmersion.plugin.AiProvider],
      http: com.histmersion.plugin.AiHttp = HostAiHttp()): Factory =
    (cfg, ref) => BuiltinAi.lookup(ref.provider, providers()).left.map(why => s"${ref.wire}: $why").flatMap {
      case _: HostAiProvider => host(rt)(cfg, ref)
      case p                 => PluginAiClient.client(ref, p, http, rt.loopback)
    }

  /** The host's own providers, as before the registry: the CLI runtimes on `rt`, else the HTTP ones. */
  def host(rt: CliRuntime): Factory =
    (cfg, ref) => ref.provider match
      case ProviderKind.ClaudeCode => ClaudeCode.client(cfg.providers.claudeCode, rt, ref)
      case ProviderKind.Codex      => Codex.client(cfg.providers.codex, rt, ref)
      case ProviderKind.Opencode   => Opencode.client(cfg.providers.opencode, rt, ref)
      case _                       => fromConfig(cfg, ref)

  /** The client for `ref` (an HTTP provider), or why it cannot be built (no key for a hosted
    * provider, a bad URL). */
  def fromConfig(cfg: AgentsConfig, ref: ModelRef): Either[String, LlmClient] =
    cfg.provider(ref.provider).toRight(s"${ref.wire}: the ${ref.provider.wire} provider is not an HTTP provider").flatMap { p =>
    val timeout = math.max(1, p.timeoutSeconds).seconds
    Uri.parse(p.baseUrl).left.map(e => s"agents.providers.${ref.provider.wire}.base-url: $e").flatMap { uri =>
      ref.provider match
        case ProviderKind.Anthropic if p.apiKey.isEmpty =>
          Left("no Anthropic API key — set ANTHROPIC_API_KEY in the server's environment")
        case ProviderKind.OpenAI if p.apiKey.isEmpty =>
          Left("no OpenAI API key — set OPENAI_API_KEY in the server's environment")
        case ProviderKind.Anthropic =>
          val client = ClaudeClient(ClaudeConfig(apiKey = p.apiKey, baseUrl = uri, timeout = timeout))
          Right(Claude(ref, client, httpBackend(30)))
        case ProviderKind.OpenAI | ProviderKind.Local =>
          // a local server needs no key, but the client always sends the header: send a placeholder
          val key = if p.apiKey.isEmpty then "none" else p.apiKey
          Right(OpenAICompatible(ref, new OpenAI(key, uri, timeout = timeout), p.strictTools, httpBackend(30)))
        case k => Left(s"${ref.wire}: ${k.wire} is not an HTTP provider")
    }
    }

  private final class Claude(val model: ModelRef, client: ClaudeClient, backend: Backend[Identity]) extends LlmClient:
    def run[Out](call: LlmCall[Out]): AgentResult[Either[AgentFailure, Out]] =
      configure(ClaudeAgent.synchronous(client, ClaudeModel.CustomClaudeModel(model.model)), call)
        .run(call.user)(backend)

  private final class OpenAICompatible(val model: ModelRef, client: OpenAI, strictTools: Boolean,
      backend: Backend[Identity]) extends LlmClient:
    def run[Out](call: LlmCall[Out]): AgentResult[Either[AgentFailure, Out]] =
      configure(OpenAIAgent.synchronous(client, ChatCompletionModel.CustomChatCompletionModel(model.model), strictTools), call)
        .run(call.user)(backend)

  /** sttp-ai's usage → ours. */
  def usage(u: TokenUsage): Agents.Usage =
    Agents.Usage(u.inputTokens.value, u.outputTokens.value, u.cachedInputTokens.value, u.cacheWriteInputTokens.value)

  /** The model the provider reported serving a step (#992): the id each call's response named (an API
    * response's `model`, claude -p's `modelUsage` keys), the busiest (most output tokens) first, joined
    * by ` + ` when several served it (Claude Code may hand side work to a smaller model). None when no
    * call named one. */
  def servedBy(calls: Seq[LlmCallUsage]): Option[String] =
    val out = calls.flatMap(c => c.model.map(_.trim).filter(_.nonEmpty).map(_ -> c.usage.outputTokens.value))
      .groupMapReduce(_._1)(_._2)(_ + _)
    Option.when(out.nonEmpty)(out.toList.sortBy((m, n) => (-n, m)).map(_._1).mkString(" + "))

  /** The model's final text, whether or not it parsed as the typed answer. */
  def rawAnswer[Out](a: Either[AgentFailure, Out])(using io.circe.Encoder[Out]): String =
    a.fold(_.rawAnswer, out => io.circe.Encoder[Out].apply(out).noSpaces)

  /** A [[FinishReason]] as words, for a run's trace. */
  def finish(r: FinishReason): String = r match
    case FinishReason.NaturalStop    => "answered"
    case FinishReason.MaxIterations  => "iteration limit"
    case FinishReason.TokenLimit     => "token limit"
    case FinishReason.BudgetExceeded => "budget exhausted"
    case FinishReason.Custom(why)    => why
    case FinishReason.Error(m)       => s"error: $m"
