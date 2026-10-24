package com.histmersion.plugin

/** AI providers, the fifth part of the app that plugins extend: who
  * serves a model the embedded agents call. A plugin adds an [[AiProvider]] to
  * [[PluginContext.aiProviders]] under the PROVIDER NAME — the part before the colon of a
  * `provider:model` reference (`agents.roles.extractor = "gemini:gemini-3-pro"` asks the provider
  * registered as `gemini`).
  *
  * The provider answers ONE model call at a time ([[AiProvider.turn]]). The host owns the rest: the
  * prompts, the tool loop and the tools themselves (run in process, under each step's allowlist), the
  * typed answer's decoding and validation and its retries, budgets, allowances, approvals, the trace,
  * and which model of a role's chain is picked before every call. A provider's models are METERED: they
  * need a price in `agents.prices` (`0/0` for a free one), and allowances on `<provider>:*` cap them. */

/** A tool the step offers the model: its name, what it does, and its input as a JSON Schema (text). */
final case class AiTool(name: String, description: String, inputSchema: String)

/** A tool call the model made: the call's id (echoed in its [[AiMessage.ToolResult]]), the tool, and
  * its input as JSON text. */
final case class AiToolCall(id: String, tool: String, input: String)

/** One entry of the conversation so far, oldest first. */
enum AiMessage:
  /** The user's message — the step's prompt, with the untrusted data in it. */
  case User(text: String)
  /** What the model answered on an earlier turn: its text and the tools it called. */
  case Assistant(text: String, toolCalls: List[AiToolCall])
  /** What the host's tool answered to one call. */
  case ToolResult(callId: String, tool: String, output: String)

/** Tokens of one call. `input` counts ALL prompt tokens, cached ones included; `cacheRead` and
  * `cacheWrite` are parts of it. */
final case class AiUsage(input: Long, output: Long, cacheRead: Long = 0, cacheWrite: Long = 0)

/** Why the model stopped. */
enum AiStop:
  case EndTurn, ToolUse, MaxTokens, Other

/** One model call.
  *
  * @param model        the model id, as the reference names it after the colon
  * @param system       the system prompt (the host's; carries no untrusted data)
  * @param messages     the conversation so far, the user's message first
  * @param tools        the tools offered on THIS call (none on the last allowed iteration, so the model
  *                     answers instead of calling a tool whose result would be thrown away)
  * @param answerSchema the JSON Schema (text) the final answer must match; a provider without native
  *                     structured output puts it in the prompt — the host decodes and validates anyway
  * @param maxTokens    the most output tokens this call may produce (`agents.step.max-tokens`)
  * @param iteration    this call's place in the step's loop (1-based) …
  * @param maxIterations … and the most calls the step may make */
final case class AiTurnRequest(
    model: String,
    system: String,
    messages: List[AiMessage],
    tools: List[AiTool],
    answerSchema: Option[String],
    maxTokens: Int,
    iteration: Int,
    maxIterations: Int)

/** What one call answered: text (the final answer is JSON matching the answer schema), the tools the
  * model called (the host runs them and calls again), why it stopped, its usage, and the model that
  * actually served it, when the provider says. */
final case class AiTurn(text: String, toolCalls: List[AiToolCall] = Nil, stop: AiStop = AiStop.EndTurn,
    usage: Option[AiUsage] = None, servedBy: Option[String] = None)

/** A call that did not answer. */
enum AiFailure:
  /** The provider failed (unreachable, an error answer): the run pauses, as for any provider error. */
  case Error(message: String)
  /** The plan behind the model reported its own usage limit: the host passes the model over until
    * `untilEpochMs` (or its cool-down) and tries the chain's next one. */
  case LimitReached(message: String, untilEpochMs: Option[Long] = None)

/** What a provider can do. A provider without `tools` is offered no tools (a step that needs them
  * should not chain its models); without `structuredOutput` the schema still travels in the request. */
final case class AiCapabilities(tools: Boolean = true, structuredOutput: Boolean = false)

/** One line of `agent doctor`: `level` is `ok`, `warn` or `fail`. */
final case class AiCheck(name: String, level: String, detail: String)

/** One HTTP answer. */
final case class AiHttpResponse(status: Int, body: String, headers: Map[String, String] = Map.empty)

/** The host's client for model calls: HTTP/1.1, the host's User-Agent, a timeout per call, nothing it
  * sends or receives ever logged (a key travels in a header). Failures to connect are `Left`; an HTTP
  * error status is a response (the provider reads its body). */
trait AiHttp:
  def post(url: String, headers: Map[String, String], body: String, timeoutSeconds: Int): Either[String, AiHttpResponse]
  def get(url: String, headers: Map[String, String], timeoutSeconds: Int): Either[String, AiHttpResponse]

object AiHttp:
  /** A client that sends nothing (a test, `--self-check`). */
  val Offline: AiHttp = new AiHttp:
    def post(url: String, headers: Map[String, String], body: String, timeoutSeconds: Int) = Left("offline")
    def get(url: String, headers: Map[String, String], timeoutSeconds: Int) = Left("offline")

/** One provider. Called from many runs at once (each call is independent: keep no per-call state).
  * Failures are values; an exception from [[turn]] is the provider's error. */
trait AiProvider:
  /** What to call it: "Google Gemini API". */
  def title: String

  def description: String = ""

  def capabilities: AiCapabilities = AiCapabilities()

  /** The provider spends the PERSON'S own subscription (a login, not an API key): refused while the
    * server listens beyond loopback, where another client could spend it. */
  def spendsPersonalPlan: Boolean = false

  /** Whether a client for `model` can be used now (a key configured, a URL given) — asked before a
    * model is picked, so a provider that cannot run is passed over with its reason. Spends nothing. */
  def check(model: String): Either[String, Unit] = Right(())

  /** The models the provider lists, from a FREE call (never a generation), for `agent doctor`;
    * `Right(Nil)` = it has no listing. */
  def models(http: AiHttp): Either[String, List[String]] = Right(Nil)

  /** Its own checks for `agent doctor` (`online = false`: send nothing). Spends nothing. */
  def doctor(online: Boolean, http: AiHttp): List[AiCheck] = Nil

  /** ONE model call. */
  def turn(request: AiTurnRequest, http: AiHttp): Either[AiFailure, AiTurn]
