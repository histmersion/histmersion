package com.histmersion
package server.agents

import com.histmersion.api.problem.Text.sentence
import com.histmersion.domain.library.workflows.*

import com.histmersion.domain.automation.agents.*

import com.histmersion.server.ServerPlugins

import com.histmersion.api.automation.Agents.{ModelRef, ProviderKind}
import com.histmersion.config.{AppConfig, LoadedConfig}
import com.sun.net.httpserver.{HttpExchange, HttpServer}
import org.ekrich.config.ConfigFactory
import sttp.ai.core.agent.*
import sttp.shared.Identity

import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*
import com.histmersion.util.storage.Stores
import com.histmersion.util.storage.Resources
import com.histmersion.util.system.JvmHome
import com.histmersion.domain.automation.agents.LlmCall
import com.histmersion.domain.library.acquisition.CoreBudget
import com.histmersion.util.plugins.Plugin
import com.histmersion.domain.HttpPolicy
import com.histmersion.api.instance.Version

/** The recorded wire of the HTTP AI providers (`anthropic`, `openai`, `local`): the engine's agent loop driven through
  * the server's own factory ([[LlmClient.factory]] over the generation's AI-provider registry) against a loopback
  * server set as each provider's `base-url`, which answers scripted responses in the providers' own shapes. Every
  * request — method, path and query, every header (sorted; the `Host` port and the loopback port in messages
  * normalised), the full body — is written, then what the loop returned and what the engine makes of it. Nothing
  * reaches the network; the keys are fixtures. The transcript is `traffic/ai.txt`, compared byte for byte
  * (`HISTMERSION_UPDATE_TRAFFIC=1` rewrites it): moving the providers into plugins changes no request.
  */
class AiTrafficSuite extends munit.FunSuite:

  /** One scripted answer: a status, its headers, its body. */
  private given Stores = Stores.disk
  private given Resources = Resources.classpath(getClass.getClassLoader)
  private given java.time.Clock = java.time.Clock.systemUTC()
  private given JvmHome = JvmHome(java.nio.file.Path.of("target/test-home").toAbsolutePath)
  final private case class Answer(status: Int, body: String, headers: List[(String, String)] = Nil, delayMs: Long = 0)

  private def json(body: String, status: Int = 200, headers: List[(String, String)] = Nil): Answer =
    Answer(status, body, ("Content-Type" -> "application/json") :: headers)

  /** A loopback server answering each request with the next scripted answer (599 once they run out), every request
    * written to `log`.
    */
  final private class Upstream extends AutoCloseable:
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    private val answers = scala.collection.mutable.Queue.empty[Answer]
    private val log = StringBuilder()
    private var n = 0
    val port: Int = server.getAddress.getPort

    server.createContext(
      "/",
      (ex: HttpExchange) =>
        try
          val body = String(ex.getRequestBody.readAllBytes(), UTF_8)
          val headers = ex.getRequestHeaders.asScala.toList
            .flatMap((k, vs) => vs.asScala.map(v => k -> v))
            .sortBy((k, v) => (k.toLowerCase(java.util.Locale.ROOT), v))
          val answer = synchronized {
            n += 1
            val a = if answers.isEmpty then Answer(599, "no scripted answer") else answers.dequeue()
            log ++= s"request $n\n  ${ex.getRequestMethod} ${ex.getRequestURI.getRawPath}"
            log ++= Option(ex.getRequestURI.getRawQuery).fold("")(q => s"?$q") + "\n"
            headers.foreach((k, v) => log ++= s"  $k: $v\n")
            log ++= s"  body: $body\n"
            log ++= s"  -> ${a.status}\n"
            a
          }
          if answer.delayMs > 0 then Thread.sleep(answer.delayMs)
          answer.headers.foreach((k, v) => ex.getResponseHeaders.add(k, v))
          val bytes = answer.body.getBytes(UTF_8)
          ex.sendResponseHeaders(answer.status, if bytes.isEmpty then -1 else bytes.length.toLong)
          if bytes.nonEmpty then ex.getResponseBody.write(bytes)
        finally ex.close()
    )
    server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool())
    server.start()

    def script(as: List[Answer]): Unit = synchronized { answers.clear(); answers ++= as; log.clear(); n = 0 }
    def requests: String = synchronized(log.result())
    def close(): Unit = server.stop(0)

  // ── the step's tools and answers ───────────────────────────────────────────────────────────────

  private val search = AgentTool.fromFunction[AgentSearchInput]("search", "Search the corpus by its filters")(in =>
    s"""{"total":2,"query":${io.circe.Json
        .fromString(in.query.map(_.value).getOrElse(""))
        .noSpaces},"hits":["zz2001_a","zz2002_b"]}"""
  )
  private val facets = AgentTool.fromFunction[AgentFacetsInput]("facets", "The values of one field")(in =>
    s"""{"field":"${in.field}","values":["Tin Trade","Bronze Age"]}"""
  )
  private val tools: List[AgentTool[Identity, ?]] = List(search, facets)

  private val classification =
    """{"summary":"Tin reached the Aegean by sea.","topics":["trade"],"regions":["Anatolia"],"tags":["Tin Trade"],"period":null,"status":"current","statusEvidence":"widely cited"}"""
  private val probe = """{"citekey":"zz2001_a","title":"Tin","year":2001}"""
  private val pingSchema = ResponseSchema.derived[Ping](Some("one word back"))

  private def call[A](
      schema: ResponseSchema[A],
      iterations: LlmCall.MaxIterations,
      maxTokens: LlmCall.MaxTokens = LlmCall.MaxTokens(16000),
      offered: List[AgentTool[Identity, ?]] = tools,
      interceptors: List[AgentInterceptor[Identity]] = Nil
  ): LlmCall[A] =
    LlmCall[A](
      LlmCall.System("You are a careful research assistant. Answer with JSON only."),
      LlmCall.User("Classify the source.\n<source>Tin was essential for bronze.</source>"),
      offered,
      schema,
      iterations,
      maxTokens,
      interceptors
    )

  /** Forces the final answer from the second iteration on, as the run's budget does. */
  private val finishNow = new AgentInterceptor[Identity]:
    override def decide(state: AgentRunState): LoopDecision =
      if state.iterationsCompleted >= 1 then
        LoopDecision.FinishNow(FinishReason.Custom("zz-stop"), "Answer now with what you have.")
      else LoopDecision.Continue

  // ── the providers' answers, in their own shapes ────────────────────────────────────────────────

  private def claude(content: String, stop: String, model: String = "claude-sonnet-4-5-20250929", usage: String) =
    json(
      s"""{"id":"msg_01zz","type":"message","role":"assistant","model":"$model","content":[$content],"stop_reason":"$stop","stop_sequence":null,"usage":$usage}"""
    )
  private def claudeText(t: String) = s"""{"type":"text","text":${io.circe.Json.fromString(t).noSpaces}}"""
  private def claudeTool(id: String, name: String, input: String) =
    s"""{"type":"tool_use","id":"$id","name":"$name","input":$input}"""
  private val claudeUsage =
    """{"input_tokens":1200,"output_tokens":80,"cache_creation_input_tokens":300,"cache_read_input_tokens":500}"""
  private val claudeUsagePlain = """{"input_tokens":900,"output_tokens":40}"""
  private def claudeError(kind: String, message: String) =
    s"""{"type":"error","error":{"type":"$kind","message":"$message"}}"""

  private def openai(message: String, finish: String, model: String = "gpt-5-2025-08-07", usage: String) =
    json(
      s"""{"id":"chatcmpl-zz1","object":"chat.completion","created":1761900000,"model":"$model","choices":[{"index":0,"message":$message,"logprobs":null,"finish_reason":"$finish"}],"usage":$usage,"system_fingerprint":"fp_zz"}"""
    )
  private def openaiText(t: String) =
    s"""{"role":"assistant","content":${io.circe.Json.fromString(t).noSpaces},"refusal":null}"""
  private def openaiTools(calls: String*) = s"""{"role":"assistant","content":null,"tool_calls":[${calls
      .mkString(",")}]}"""
  private def openaiCall(id: Option[String], name: String, args: String) =
    s"""{${id.fold("")(i => s""""id":"$i",""")}"type":"function","function":{"name":"$name","arguments":${io.circe.Json
        .fromString(args)
        .noSpaces}}}"""
  private val openaiUsage =
    """{"prompt_tokens":900,"completion_tokens":40,"total_tokens":940,"prompt_tokens_details":{"cached_tokens":256,"audio_tokens":0},"completion_tokens_details":{"reasoning_tokens":12,"audio_tokens":0,"accepted_prediction_tokens":0,"rejected_prediction_tokens":0}}"""
  private val openaiUsagePlain = """{"prompt_tokens":700,"completion_tokens":30,"total_tokens":730}"""
  private def openaiError(message: String, kind: String, code: String) =
    s"""{"error":{"message":"$message","type":"$kind","param":null,"code":"$code"}}"""

  // ── one scenario ───────────────────────────────────────────────────────────────────────────────

  private def loaded(text: String): LoadedConfig =
    AppConfig.loadWithUserText(ConfigFactory.empty, text).fold(e => fail(e.sentence), identity)

  private def providers(port: Int, extra: String = "", key: Boolean = true): String =
    val k = if key then "api-key = \"k-test\"\n" else ""
    s"""histmersion.agents.providers {
       |  anthropic { base-url = "http://127.0.0.1:$port/anthropic"
       |$k  }
       |  openai { base-url = "http://127.0.0.1:$port/openai/v1"
       |$k  }
       |  local { base-url = "http://127.0.0.1:$port/local/v1" }
       |}
       |$extra""".stripMargin

  /** The loop over `ref` for `c`, as the engine runs a step: through the server's factory, then
    * `AgentsControllers.call`.
    */
  private def scenario[A: io.circe.Encoder](
      up: Upstream,
      name: String,
      ref: ModelRef,
      c: LlmCall[A],
      answers: List[Answer],
      text: String
  ): String =
    up.script(answers)
    val l = loaded(text)
    val g = ServerPlugins.load(l, core = CoreBudget(() => java.time.Instant.now(java.time.Clock.systemUTC())))
    val f = LlmClient.factory(
      CliRuntime(l.config, EngineContributions.roster),
      () => g.registries.aiProviders.map((k, p) => Plugin.Id.assume(k.value) -> p)
    )
    val sb = StringBuilder(s"## $name\n")
    f(l.config.agents, ref) match
      case Left(why)     => sb ++= s"client: refused: ${why.sentence}\n"
      case Right(client) =>
        sb ++= s"client: ${client.model.wire}\n"
        val outcome = AgentsControllers.call(client.model)(client.run(c))
        sb ++= up.requests
        outcome match
          case Left(Left(limit)) => sb ++= s"engine: plan limit: ${limit.getMessage} (until ${limit.until})\n"
          case Left(Right(e))    => sb ++= s"engine: provider error: ${e.message.sentence}\n"
          case Right(res)        =>
            sb ++= s"answer: ${res.finalAnswer.fold(af => s"failed: ${Engine.answerProblem(af, res.finishReason).sentence}", a => io.circe.Encoder[A].apply(a).noSpaces)}\n"
            sb ++= s"raw answer: ${LlmClient.rawAnswer(res.finalAnswer)}\n"
            sb ++= s"finish: ${LlmClient.finish(res.finishReason)}; iterations: ${res.iterations}\n"
            res.toolCalls.foreach(t =>
              sb ++= s"tool call ${t.id} (iteration ${t.iteration}): ${t.toolName} ${t.input} -> ${t.output}\n"
            )
            val u = res.usage
            sb ++= s"usage: input ${u.inputTokens.value}, output ${u.outputTokens.value}, cached ${u.cachedInputTokens.value}, " +
              s"reasoning ${u.reasoningTokens.value}, cache write ${u.cacheWriteInputTokens.value}\n"
            sb ++= s"engine usage: ${LlmClient.usage(u)}\n"
            res.llmCalls.foreach(lc =>
              sb ++= s"call: ${lc.model.getOrElse("-")} input ${lc.usage.inputTokens.value} output ${lc.usage.outputTokens.value}\n"
            )
            sb ++= s"served by: ${LlmClient.servedBy(res.llmCalls).getOrElse("-")}\n"
            sb ++= s"trace: ${Trace.messages(res.history, Vector.empty, 4000).map(m => s"${m.role}${m.toolName.fold("")(t => s"($t)")}").mkString(" ")}\n"
    sb.result().replace(s"127.0.0.1:${up.port}", "127.0.0.1:<port>")

  private val sonnet = ModelRef(ProviderKind.Anthropic, ModelRef.Model("claude-sonnet-4-5"))
  private val gpt = ModelRef(ProviderKind.OpenAI, ModelRef.Model("gpt-5"))
  private val qwen = ModelRef(ProviderKind.Local, ModelRef.Model("qwen3-coder"))

  private def transcript: String =
    val up = Upstream()
    try
      val base = providers(up.port)
      val closed =
        val s = java.net.ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1"))
        val p = s.getLocalPort
        s.close()
        p
      List(
        // ── anthropic ──
        scenario(
          up,
          "anthropic: two tools, three iterations (the last without tools), a typed answer",
          sonnet,
          call(LibraryAnswers.classification, LlmCall.MaxIterations(3)),
          List(
            claude(
              List(
                claudeText("Let me look."),
                claudeTool(
                  "toolu_01",
                  "search",
                  """{"query":"tin","topics":["trade"],"tags":["Tin Trade"],"authors":[],"status":["current"],"from":-3000,"to":-1000,"limit":5}"""
                ),
                claudeTool("toolu_02", "facets", """{"field":"tags","prefix":"Tin"}""")
              ).mkString(","),
              "tool_use",
              usage = claudeUsage
            ),
            claude(claudeTool("toolu_03", "facets", """{"field":"topics"}"""), "tool_use", usage = claudeUsagePlain),
            claude(
              claudeText(classification) + "," + claudeText("ignored second block"),
              "end_turn",
              model = "claude-sonnet-4-5-20250929-b",
              usage = claudeUsage
            )
          ),
          base
        ),
        scenario(
          up,
          "anthropic: a natural stop on the second iteration, a tool the step does not allow",
          sonnet,
          call(AnswerSchemas.probe, LlmCall.MaxIterations(6), maxTokens = LlmCall.MaxTokens(2000)),
          List(
            claude(
              claudeTool("toolu_09", "query", """{"sql":"DELETE FROM sources"}"""),
              "tool_use",
              usage = claudeUsage
            ),
            claude(claudeText(probe), "end_turn", usage = claudeUsagePlain)
          ),
          base
        ),
        scenario(
          up,
          "anthropic: no tools, an answer schema with a description, cut at the token limit",
          sonnet,
          call(pingSchema, LlmCall.MaxIterations(2), maxTokens = LlmCall.MaxTokens(64), offered = Nil),
          List(claude(claudeText("""{"word":"po"""), "max_tokens", usage = claudeUsagePlain)),
          base
        ),
        scenario(
          up,
          "anthropic: an answer that is not the schema's",
          sonnet,
          call(AnswerSchemas.probe, LlmCall.MaxIterations(1)),
          List(claude(claudeText("""{"title":"no citekey"}"""), "end_turn", usage = claudeUsagePlain)),
          base
        ),
        scenario(
          up,
          "anthropic: a model without structured output",
          ModelRef(ProviderKind.Anthropic, ModelRef.Model("claude-3-haiku-20240307")),
          call(AnswerSchemas.probe, LlmCall.MaxIterations(2)),
          Nil,
          base
        ),
        scenario(
          up,
          "anthropic: HTTP 500",
          sonnet,
          call(AnswerSchemas.probe, LlmCall.MaxIterations(2)),
          List(json(claudeError("api_error", "Internal server error"), 500)),
          base
        ),
        scenario(
          up,
          "anthropic: HTTP 529 overloaded",
          sonnet,
          call(AnswerSchemas.probe, LlmCall.MaxIterations(2)),
          List(json(claudeError("overloaded_error", "Overloaded"), 529)),
          base
        ),
        scenario(
          up,
          "anthropic: HTTP 429 with retry-after",
          sonnet,
          call(AnswerSchemas.probe, LlmCall.MaxIterations(2)),
          List(
            json(
              claudeError("rate_limit_error", "Number of request tokens has exceeded your per-minute rate limit"),
              429,
              List("retry-after" -> "17")
            )
          ),
          base
        ),
        scenario(
          up,
          "anthropic: HTTP 401",
          sonnet,
          call(AnswerSchemas.probe, LlmCall.MaxIterations(2)),
          List(json(claudeError("authentication_error", "invalid x-api-key"), 401)),
          base
        ),
        scenario(
          up,
          "anthropic: an error answer that is not JSON",
          sonnet,
          call(AnswerSchemas.probe, LlmCall.MaxIterations(2)),
          List(Answer(502, "<html>Bad Gateway</html>", List("Content-Type" -> "text/html"))),
          base
        ),
        scenario(
          up,
          "anthropic: a malformed body",
          sonnet,
          call(AnswerSchemas.probe, LlmCall.MaxIterations(2)),
          List(json("""{"id":"msg_01zz","type":"message","content":"oops"""")),
          base
        ),
        scenario(
          up,
          "anthropic: a body missing its usage",
          sonnet,
          call(AnswerSchemas.probe, LlmCall.MaxIterations(2)),
          List(
            json(
              s"""{"id":"msg_01zz","type":"message","role":"assistant","model":"claude-sonnet-4-5","content":[${claudeText(
                  probe
                )}],"stop_reason":"end_turn"}"""
            )
          ),
          base
        ),
        scenario(
          up,
          "anthropic: nothing listens",
          sonnet,
          call(AnswerSchemas.probe, LlmCall.MaxIterations(2)),
          Nil,
          providers(up.port) + s"""histmersion.agents.providers.anthropic.base-url = "http://127.0.0.1:$closed"\n"""
        ).replace(s"127.0.0.1:$closed", "127.0.0.1:<closed>"),
        scenario(
          up,
          "anthropic: no key",
          sonnet,
          call(AnswerSchemas.probe, LlmCall.MaxIterations(2)),
          Nil,
          providers(up.port, key = false)
        ),
        scenario(
          up,
          "anthropic: a base URL that is not one",
          sonnet,
          call(AnswerSchemas.probe, LlmCall.MaxIterations(2)),
          Nil,
          base + "histmersion.agents.providers.anthropic.base-url = \"http://127.0.0.1:port/x\"\n"
        ),
        scenario(
          up,
          "anthropic: a relative base URL",
          sonnet,
          call(AnswerSchemas.probe, LlmCall.MaxIterations(2)),
          Nil,
          base + "histmersion.agents.providers.anthropic.base-url = \"not a url\"\n"
        ),
        scenario(
          up,
          "anthropic: the final answer forced on the second iteration",
          sonnet,
          call(AnswerSchemas.probe, LlmCall.MaxIterations(5), interceptors = List(finishNow)),
          List(
            claude(claudeTool("toolu_05", "facets", """{"field":"tags"}"""), "tool_use", usage = claudeUsagePlain),
            claude(claudeText(probe), "end_turn", usage = claudeUsagePlain)
          ),
          base
        ),
        scenario(
          up,
          "anthropic: an error in a 200 answer",
          sonnet,
          call(AnswerSchemas.probe, LlmCall.MaxIterations(2)),
          List(json(claudeError("overloaded_error", "Overloaded"))),
          base
        ),
        scenario(
          up,
          "anthropic: a 200 answer that is a JSON array",
          sonnet,
          call(AnswerSchemas.probe, LlmCall.MaxIterations(2)),
          List(json("[]")),
          base
        ),
        scenario(
          up,
          "anthropic: an error answer with an empty body",
          sonnet,
          call(AnswerSchemas.probe, LlmCall.MaxIterations(2)),
          List(Answer(500, "")),
          base
        ),
        scenario(
          up,
          "anthropic: no answer within timeout-seconds",
          sonnet,
          call(AnswerSchemas.probe, LlmCall.MaxIterations(2)),
          List(Answer(200, "{}", delayMs = 2500)),
          base + "histmersion.agents.providers.anthropic.timeout-seconds = 1\n"
        ),
        scenario(
          up,
          "anthropic: a base URL with a trailing slash",
          sonnet,
          call(AnswerSchemas.probe, LlmCall.MaxIterations(1)),
          List(claude(claudeText(probe), "end_turn", usage = claudeUsagePlain)),
          base + s"""histmersion.agents.providers.anthropic.base-url = "http://127.0.0.1:${up.port}/anthropic/"\n"""
        ),
        scenario(
          up,
          "anthropic: an ftp base URL",
          sonnet,
          call(AnswerSchemas.probe, LlmCall.MaxIterations(2)),
          Nil,
          base + "histmersion.agents.providers.anthropic.base-url = \"ftp://127.0.0.1/x\"\n"
        ),
        scenario(
          up,
          "anthropic: switched off",
          sonnet,
          call(AnswerSchemas.probe, LlmCall.MaxIterations(2)),
          Nil,
          base + "histmersion.plugins.ai-anthropic.enabled = false\n"
        ),
        // ── openai ──
        scenario(
          up,
          "openai (strict tools): two tools, three iterations, a call without an id, a typed answer",
          gpt,
          call(LibraryAnswers.classification, LlmCall.MaxIterations(3)),
          List(
            openai(
              openaiTools(
                openaiCall(
                  Some("call_zzA"),
                  "search",
                  """{"query":"tin","topics":["trade"],"tags":[],"authors":[],"status":[],"from":null,"to":null,"sort":null,"limit":5,"offset":null}"""
                ),
                openaiCall(None, "facets", """{"field":"tags","prefix":null}""")
              ),
              "tool_calls",
              usage = openaiUsage
            ),
            openai(
              openaiTools(openaiCall(Some("call_zzB"), "facets", """{"field":"topics"}""")),
              "tool_calls",
              usage = openaiUsagePlain
            ),
            openai(openaiText(classification), "stop", model = "gpt-5-2025-08-07-b", usage = openaiUsage)
          ),
          base
        ),
        scenario(
          up,
          "openai (strict tools off): a natural stop, an answer schema with a description",
          gpt,
          call(pingSchema, LlmCall.MaxIterations(4), maxTokens = LlmCall.MaxTokens(500)),
          List(
            openai(
              openaiTools(openaiCall(Some("call_zzC"), "facets", """{"field":"tags"}""")),
              "tool_calls",
              usage = openaiUsagePlain
            ),
            openai(openaiText("""{"word":"pong"}"""), "stop", usage = openaiUsagePlain)
          ),
          base + "histmersion.agents.providers.openai.strict-tools = false\n"
        ),
        scenario(
          up,
          "openai: cut at the token limit",
          gpt,
          call(AnswerSchemas.probe, LlmCall.MaxIterations(2), maxTokens = LlmCall.MaxTokens(32)),
          List(openai(openaiText("""{"citekey":"zz"""), "length", usage = openaiUsagePlain)),
          base
        ),
        scenario(
          up,
          "openai: a content filter stop",
          gpt,
          call(AnswerSchemas.probe, LlmCall.MaxIterations(2)),
          List(openai("""{"role":"assistant","content":null}""", "content_filter", usage = openaiUsagePlain)),
          base
        ),
        scenario(
          up,
          "openai: HTTP 500",
          gpt,
          call(AnswerSchemas.probe, LlmCall.MaxIterations(2)),
          List(
            json(
              openaiError("The server had an error while processing your request.", "server_error", "server_error"),
              500
            )
          ),
          base
        ),
        scenario(
          up,
          "openai: HTTP 429 with retry-after",
          gpt,
          call(AnswerSchemas.probe, LlmCall.MaxIterations(2)),
          List(
            json(
              openaiError("Rate limit reached for gpt-5 on tokens per min.", "tokens", "rate_limit_exceeded"),
              429,
              List("retry-after" -> "20")
            )
          ),
          base
        ),
        scenario(
          up,
          "openai: HTTP 401",
          gpt,
          call(AnswerSchemas.probe, LlmCall.MaxIterations(2)),
          List(json(openaiError("Incorrect API key provided.", "invalid_request_error", "invalid_api_key"), 401)),
          base
        ),
        scenario(
          up,
          "openai: a malformed body",
          gpt,
          call(AnswerSchemas.probe, LlmCall.MaxIterations(2)),
          List(json("""{"id":"chatcmpl-zz1","choices":"oops"}""")),
          base
        ),
        scenario(
          up,
          "openai: an error answer that is not JSON",
          gpt,
          call(AnswerSchemas.probe, LlmCall.MaxIterations(2)),
          List(Answer(503, "upstream unavailable", List("Content-Type" -> "text/plain"))),
          base
        ),
        scenario(
          up,
          "openai: text beside a tool call, a call without a function name",
          gpt,
          call(AnswerSchemas.probe, LlmCall.MaxIterations(3)),
          List(
            openai(
              s"""{"role":"assistant","content":"Checking the tags.","tool_calls":[${openaiCall(
                  Some("call_zzD"),
                  "facets",
                  """{"field":"tags"}"""
                )},{"id":"call_zzE","type":"function","function":{"arguments":"{}"}}]}""",
              "tool_calls",
              usage = openaiUsagePlain
            ),
            openai(openaiText(probe), "stop", usage = openaiUsagePlain)
          ),
          base
        ),
        scenario(
          up,
          "openai: a 200 answer that is a JSON array",
          gpt,
          call(AnswerSchemas.probe, LlmCall.MaxIterations(2)),
          List(json("[]")),
          base
        ),
        scenario(
          up,
          "openai: a choice without its role",
          gpt,
          call(AnswerSchemas.probe, LlmCall.MaxIterations(2)),
          List(openai("""{"content":"x"}""", "stop", usage = openaiUsagePlain)),
          base
        ),
        scenario(
          up,
          "openai: an answer without its usage",
          gpt,
          call(AnswerSchemas.probe, LlmCall.MaxIterations(2)),
          List(
            json(
              s"""{"id":"chatcmpl-zz1","object":"chat.completion","created":1761900000,"model":"gpt-5","choices":[{"index":0,"message":${openaiText(
                  probe
                )},"finish_reason":"stop"}]}"""
            )
          ),
          base
        ),
        scenario(
          up,
          "openai: an error object without a message",
          gpt,
          call(AnswerSchemas.probe, LlmCall.MaxIterations(2)),
          List(json("""{"error":{"type":"server_error","code":null}}""", 500)),
          base
        ),
        scenario(
          up,
          "openai: an error that is null",
          gpt,
          call(AnswerSchemas.probe, LlmCall.MaxIterations(2)),
          List(json("""{"error":null}""", 500)),
          base
        ),
        scenario(
          up,
          "openai: HTTP 404 in another shape",
          gpt,
          call(AnswerSchemas.probe, LlmCall.MaxIterations(2)),
          List(json("""{"statusCode":404,"message":"Resource not found"}""", 404)),
          base
        ),
        scenario(
          up,
          "openai: HTTP 400 with a long body",
          gpt,
          call(AnswerSchemas.probe, LlmCall.MaxIterations(2)),
          List(Answer(400, "x" * 700)),
          base
        ),
        scenario(
          up,
          "openai: no key",
          gpt,
          call(AnswerSchemas.probe, LlmCall.MaxIterations(2)),
          Nil,
          providers(up.port, key = false)
        ),
        // ── local ──
        scenario(
          up,
          "local (no key): two tools, two iterations",
          qwen,
          call(AnswerSchemas.probe, LlmCall.MaxIterations(2)),
          List(
            openai(
              openaiTools(openaiCall(Some("call_1"), "search", """{"query":"tin"}""")),
              "tool_calls",
              model = "qwen3-coder",
              usage = openaiUsagePlain
            ),
            openai(openaiText(probe), "stop", model = "qwen3-coder", usage = openaiUsagePlain)
          ),
          base
        ),
        scenario(
          up,
          "local with a key and strict tools",
          qwen,
          call(LibraryAnswers.findings, LlmCall.MaxIterations(1)),
          List(openai(openaiText("""{"findings":[]}"""), "stop", model = "qwen3-coder", usage = openaiUsagePlain)),
          base + "histmersion.agents.providers.local { api-key = \"k-local\", strict-tools = true }\n"
        ),
        scenario(
          up,
          "local: a base URL with a trailing slash",
          qwen,
          call(AnswerSchemas.probe, LlmCall.MaxIterations(1)),
          List(openai(openaiText(probe), "stop", model = "qwen3-coder", usage = openaiUsagePlain)),
          base + s"""histmersion.agents.providers.local.base-url = "http://127.0.0.1:${up.port}/local/v1/"\n"""
        ),
        scenario(
          up,
          "local: nothing listens",
          qwen,
          call(AnswerSchemas.probe, LlmCall.MaxIterations(2)),
          Nil,
          base + s"""histmersion.agents.providers.local.base-url = "http://127.0.0.1:$closed/v1"\n"""
        ).replace(s"127.0.0.1:$closed", "127.0.0.1:<closed>")
      ).mkString("\n")
    finally up.close()

  private val transcriptFile = Path.of("modules/server/src/test/resources/traffic/ai.txt")

  /** The User-Agent the host's AI client sends (its policy: the project, never an e-mail). */
  private val hostAgent =
    HttpPolicy.userAgent(
      HttpPolicy.UserAgent(s"Histmersion/${Version.version} (agents)")
    )

  /** The transcript as the host's AI client sends it: the two headers that belonged to the transport the providers had
    * before (sttp's java.net.http backend: the JDK's default User-Agent and a gzip `Accept-Encoding`, which it decoded)
    * are the host client's own — its User-Agent, and no `Accept-Encoding` (it reads bodies as sent). Every other line,
    * every body included, is compared as recorded.
    */
  private def onHostTransport(recorded: String): String =
    recorded
      .split("\n", -1)
      .toList
      .filterNot(_ == "  Accept-encoding: gzip, deflate")
      .map(l => if l.startsWith("  User-agent: Java-http-client/") then s"  User-agent: $hostAgent" else l)
      .mkString("\n")

  test("the HTTP AI providers send exactly the recorded requests, in order, and the loop returns the same"):
    val now = transcript
    if sys.env.get("HISTMERSION_UPDATE_TRAFFIC").contains("1") then
      Files.createDirectories(transcriptFile.getParent)
      Files.writeString(transcriptFile, now, UTF_8)
    val recorded = onHostTransport(Files.readString(transcriptFile, UTF_8))
    if recorded != now then
      val (a, b) = (recorded.linesIterator.toVector, now.linesIterator.toVector)
      val at = a.indices.find(i => b.lift(i) != Some(a(i))).getOrElse(a.size)
      fail(
        s"the traffic differs from $transcriptFile at line ${at + 1}:\n  recorded: ${a.lift(at).getOrElse("<end>")}\n" +
          s"  now:      ${b.lift(at).getOrElse("<end>")}"
      )
