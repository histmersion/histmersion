package com.histmersion
package plugins.ai
import com.histmersion.util.problem.Text.{sentence, sentences}
import com.histmersion.util.problem.{External, Problem}

import com.histmersion.plugin.*
import com.histmersion.plugin.testkit.{PluginHarness, ScriptedAiHttp}
import com.histmersion.pluginhost.Registries
import com.histmersion.testsupport.ComparedByValue.given
import com.histmersion.domain.CoreQuota
import com.histmersion.lint.{LintRule, tolerated}

/** Histmersion's own HTTP AI providers run as any plugin author runs theirs: through the plugin testkit, which loads a
  * plugin from the class path as the server does (the host's own `agents.providers.<name>` supplied to a first-party
  * plugin, its key from the environment) and answers its calls from a scripted client — never the network.
  */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class AiHttpPluginsKitSuite extends munit.FunSuite:

  private def load(id: Plugin.Info.Id, env: Map[String, String] = Map.empty, config: String = "") =
    PluginHarness.load(id, config, env = env).fold(e => fail(e.sentences.mkString("; ")), identity)

  private val keys = Map("ANTHROPIC_API_KEY" -> "k-test", "OPENAI_API_KEY" -> "k-test")

  private def request(iteration: AiTurn.Request.Iteration = AiTurn.Request.Iteration(1), tools: List[AiTool] = Nil) =
    AiTurn.Request(
      AiTurn.Request.Model("m-1"),
      AiTurn.Request.System("SYSTEM"),
      List(AiMessage.User(AiMessage.User.Text("USER"))),
      tools,
      Some(
        AiTurn.Request.AnswerSchema("""{"type":"object","properties":{"word":{"type":"string"}},"required":["word"]}""")
      ),
      AiTurn.Request.MaxTokens(100),
      iteration,
      AiTurn.Request.MaxIterations(2),
      Some(
        AiTurn.Request.AnswerSchemaRendered(
          """{"$schema":"https://json-schema.org/draft/2020-12/schema","type":"object","properties":{"word":{"type":"string"}},"required":["word"]}"""
        )
      ),
      None
    )

  private val claudeAnswer =
    """{"id":"msg_01","type":"message","role":"assistant","model":"claude-sonnet-4-5-20250929","content":[""" +
      """{"type":"text","text":"Let me look."},{"type":"tool_use","id":"toolu_01","name":"facets","input":{"field":"tags"}}],""" +
      """"stop_reason":"tool_use","stop_sequence":null,"usage":{"input_tokens":1200,"output_tokens":80,""" +
      """"cache_creation_input_tokens":300,"cache_read_input_tokens":500}}"""

  private val chatAnswer =
    """{"id":"chatcmpl-1","object":"chat.completion","created":1761900000,"model":"qwen3","choices":[{"index":0,""" +
      """"message":{"role":"assistant","content":"{\"word\":\"hi\"}"},"finish_reason":"stop"}],""" +
      """"usage":{"prompt_tokens":700,"completion_tokens":30,"total_tokens":730}}"""

  private def json(body: AiHttp.Response.Body) = Right(
    AiHttp.Response(
      AiHttp.Response.Status(200),
      body,
      Map(Header.Name("content-type") -> Header.Value("application/json"))
    )
  )

  test("each plugin loads from the class path with its defaults and the host's settings, and registers its provider"):
    Map(
      Plugin.Info.Id("ai-anthropic") -> "anthropic",
      Plugin.Info.Id("ai-openai") -> "openai",
      Plugin.Info.Id("ai-local") -> "local"
    ).foreach { (id, name) =>
      val h = load(id, keys)
      assertEquals(h.contributions.aiProviders, List(name), id)
      assertEquals(h.plugin.info.version, "0.1.0", id)
      assertEquals(h.requests, Nil, s"$id asked an upstream while registering")
    }

  test("a provider plugin has no setting the host owns: a base URL in its block is refused"):
    val refused =
      PluginHarness.load(Plugin.Info.Id("ai-openai"), "base-url = \"http://127.0.0.1:1/v1\"", env = Map.empty)
    assert(refused.left.exists(_.sentences.mkString.contains("is no setting of the plugin")), refused.toString)

  test("without a key the hosted providers refuse every model, saying where the key goes; a local server needs none"):
    assertEquals(
      load(Plugin.Info.Id("ai-anthropic"))
        .aiTurn(Registries.AiProvidersKey("anthropic"), request())
        .check
        .left
        .map(_.sentence),
      Left("histmersion.agents.providers.anthropic.api-key reads ANTHROPIC_API_KEY, which is not set")
    )
    assertEquals(
      load(Plugin.Info.Id("ai-openai"))
        .aiTurn(Registries.AiProvidersKey("openai"), request())
        .check
        .left
        .map(_.sentence),
      Left("histmersion.agents.providers.openai.api-key reads OPENAI_API_KEY, which is not set")
    )
    val http = ScriptedAiHttp(json(AiHttp.Response.Body(chatAnswer)))
    val local = load(Plugin.Info.Id("ai-local")).aiTurn(Registries.AiProvidersKey("local"), request(), http)
    assertEquals(local.check, Right(()))
    assertEquals(
      http.requests.map(r => (r.url, r.headers(CoreQuota.HeaderName("Authorization")))),
      List(("http://127.0.0.1:11434/v1/chat/completions", "***"))
    )
    assertEquals(local.turn.flatMap(_.toOption).map(_.text), Some("""{"word":"hi"}"""))

  test("anthropic: one call to the Messages API, keyed by x-api-key, the answer read as a turn"):
    val http = ScriptedAiHttp(json(AiHttp.Response.Body(claudeAnswer)))
    val r = load(Plugin.Info.Id("ai-anthropic"), keys).aiTurn(Registries.AiProvidersKey("anthropic"), request(), http)
    val sent = http.requests.head
    assertEquals(sent.url, "https://api.anthropic.com/v1/messages")
    assertEquals(
      sent.headers,
      Map("x-api-key" -> "***", "anthropic-version" -> "2023-06-01", "content-type" -> "application/json")
    )
    assert(sent.body.exists(_.value.contains("\"output_config\":{\"format\":{\"type\":\"json_schema\"")), sent.body)
    assertEquals(
      r.turn,
      Some(
        Right(
          AiTurn(
            AiTurn.Text("Let me look."),
            List(
              AiTool
                .Call(AiTool.Call.Id("toolu_01"), AiTool.Call.Tool("facets"), AiTool.Call.Input("""{"field":"tags"}"""))
            ),
            AiStop.ToolUse,
            Some(AiUsage(AiUsage.Input(2000), AiUsage.Output(80), AiUsage.CacheRead(500), AiUsage.CacheWrite(300))),
            Some(AiTurn.ServedBy("claude-sonnet-4-5-20250929"))
          )
        )
      )
    )

  test("openai: one call to chat completions, keyed by a bearer token; an error answer is the provider's failure"):
    val http = ScriptedAiHttp(
      Right(
        AiHttp.Response(
          AiHttp.Response.Status(429),
          AiHttp.Response.Body(
            """{"error":{"message":"Rate limit reached","type":"tokens","code":"rate_limit_exceeded"}}"""
          )
        )
      )
    )
    val r = load(Plugin.Info.Id("ai-openai"), keys).aiTurn(Registries.AiProvidersKey("openai"), request(), http)
    assertEquals(http.requests.head.url, "https://api.openai.com/v1/chat/completions")
    assertEquals(
      r.turn,
      Some(
        Left(
          Problem.Upstream.Http.here(
            Problem.Upstream.Name("openai"),
            Problem.Upstream.Url("https://api.openai.com/v1/chat/completions"),
            Problem.Upstream.Status(429),
            External.ReplyExcerpt("Rate limit reached")
          )
        )
      )
    )

  test("a client that cannot connect: the host's problem, with the URL, is the provider's failure"):
    val unreachable = Problem.Upstream.Unreachable.here(
      Problem.Upstream.Name("api.anthropic.com"),
      Problem.Upstream.Url("https://api.anthropic.com/v1/messages"),
      External.Reported("java.net.ConnectException: refused")
    )
    val http = ScriptedAiHttp(Left(unreachable))
    val r = load(Plugin.Info.Id("ai-anthropic"), keys).aiTurn(Registries.AiProvidersKey("anthropic"), request(), http)
    assertEquals(r.turn, Some(Left(unreachable)))

  test("the providers ask for the loop's notes and verbatim failures, and offer tools"):
    val settings = ProviderSettings(
      ProviderSettings.BaseUrl("http://127.0.0.1:1"),
      ProviderSettings.ApiKey(""),
      ProviderSettings.StrictTools(false),
      ProviderSettings.TimeoutSeconds(1)
    )
    List(AnthropicProvider(settings), ChatCompletionsProvider.openAi(settings), ChatCompletionsProvider.local(settings))
      .foreach { p =>
        assertEquals(
          p.capabilities,
          AiCapabilities(
            AiCapabilities.Tools(true),
            AiCapabilities.StructuredOutput(true),
            AiCapabilities.IterationMarkers(true),
            AiCapabilities.VerbatimFailures(true)
          ),
          p.title
        )
        assert(!p.spendsPersonalPlan.value, p.title)
      }
