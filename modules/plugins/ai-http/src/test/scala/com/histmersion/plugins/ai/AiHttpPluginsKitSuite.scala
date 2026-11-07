package com.histmersion.plugins.ai

import com.histmersion.plugin.*
import com.histmersion.plugin.testkit.{PluginHarness, ScriptedAiHttp}

/** Histmersion's own HTTP AI providers run as any plugin author runs theirs: through the plugin testkit, which loads a
  * plugin from the class path as the server does (the host's own `agents.providers.<name>` supplied to a first-party
  * plugin, its key from the environment) and answers its calls from a scripted client — never the network.
  */
class AiHttpPluginsKitSuite extends munit.FunSuite:

  private def load(id: String, env: Map[String, String] = Map.empty, config: String = "") =
    PluginHarness.load(id, config, env = env).fold(e => fail(e), identity)

  private val keys = Map("ANTHROPIC_API_KEY" -> "k-test", "OPENAI_API_KEY" -> "k-test")

  private def request(iteration: Int = 1, tools: List[AiTool] = Nil) = AiTurn.Request(
    com.histmersion.plugin.AiTurn.Request.Model("m-1"),
    com.histmersion.plugin.AiTurn.Request.System("SYSTEM"),
    List(AiMessage.User(com.histmersion.plugin.AiMessage.User.Text("USER"))),
    tools,
    Some("""{"type":"object","properties":{"word":{"type":"string"}},"required":["word"]}"""),
    com.histmersion.plugin.AiTurn.Request.MaxTokens(100),
    iteration,
    com.histmersion.plugin.AiTurn.Request.MaxIterations(2),
    Some(
      """{"$schema":"https://json-schema.org/draft/2020-12/schema","type":"object","properties":{"word":{"type":"string"}},"required":["word"]}"""
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

  private def json(body: String) = Right(AiHttp.Response(com.histmersion.plugin.AiHttp.Response.Status(200), body, Map("content-type" -> "application/json")))

  test("each plugin loads from the class path with its defaults and the host's settings, and registers its provider"):
    Map("ai-anthropic" -> "anthropic", "ai-openai" -> "openai", "ai-local" -> "local").foreach { (id, name) =>
      val h = load(id, keys)
      assertEquals(h.contributions.aiProviders, List(name), id)
      assertEquals(h.plugin.info.version, "0.1.0", id)
      assertEquals(h.requests, Nil, s"$id asked an upstream while registering")
    }

  test("a provider plugin has no setting the host owns: a base URL in its block is refused"):
    val refused = PluginHarness.load(com.histmersion.plugin.Plugin.Info.Id("ai-openai"), "base-url = \"http://127.0.0.1:1/v1\"", env = Map.empty)
    assert(refused.left.exists(_.contains("unknown setting")), refused.toString)

  test("without a key the hosted providers refuse every model, saying where the key goes; a local server needs none"):
    assertEquals(
      load("ai-anthropic").aiTurn("anthropic", request()).check,
      Left("no Anthropic API key — set ANTHROPIC_API_KEY in the server's environment")
    )
    assertEquals(
      load("ai-openai").aiTurn("openai", request()).check,
      Left("no OpenAI API key — set OPENAI_API_KEY in the server's environment")
    )
    val http = ScriptedAiHttp(json(chatAnswer))
    val local = load("ai-local").aiTurn("local", request(), http)
    assertEquals(local.check, Right(()))
    assertEquals(
      http.requests.map(r => (r.url, r.headers("Authorization"))),
      List(("http://127.0.0.1:11434/v1/chat/completions", "***"))
    )
    assertEquals(local.turn.flatMap(_.toOption).map(_.text), Some("""{"word":"hi"}"""))

  test("anthropic: one call to the Messages API, keyed by x-api-key, the answer read as a turn"):
    val http = ScriptedAiHttp(json(claudeAnswer))
    val r = load("ai-anthropic", keys).aiTurn("anthropic", request(), http)
    val sent = http.requests.head
    assertEquals(sent.url, "https://api.anthropic.com/v1/messages")
    assertEquals(
      sent.headers,
      Map("x-api-key" -> "***", "anthropic-version" -> "2023-06-01", "content-type" -> "application/json")
    )
    assert(sent.body.exists(_.contains("\"output_config\":{\"format\":{\"type\":\"json_schema\"")), sent.body)
    assertEquals(
      r.turn,
      Some(
        Right(
          AiTurn(
            com.histmersion.plugin.AiTurn.Text("Let me look."),
            List(AiTool.Call(com.histmersion.plugin.AiTool.Call.Id("toolu_01"), com.histmersion.plugin.AiTool.Call.Tool("facets"), """{"field":"tags"}""")),
            AiStop.ToolUse,
            Some(AiUsage(2000, 80, 500, 300)),
            Some(com.histmersion.plugin.AiTurn.ServedBy("claude-sonnet-4-5-20250929"))
          )
        )
      )
    )

  test("openai: one call to chat completions, keyed by a bearer token; an error answer is the provider's failure"):
    val http = ScriptedAiHttp(
      Right(
        AiHttp.Response(
          com.histmersion.plugin.AiHttp.Response.Status(429),
          """{"error":{"message":"Rate limit reached","type":"tokens","code":"rate_limit_exceeded"}}"""
        )
      )
    )
    val r = load("ai-openai", keys).aiTurn("openai", request(), http)
    assertEquals(http.requests.head.url, "https://api.openai.com/v1/chat/completions")
    assertEquals(r.turn, Some(Left(AiFailure.Error(com.histmersion.plugin.AiFailure.Error.Message("RuntimeException: OpenAI API error: Rate limit reached")))))

  test("a client that cannot connect is described as the providers always described it"):
    val http = ScriptedAiHttp(Left(com.histmersion.plugin.testkit.ScriptedAiHttp.Problem("ConnectException: ")))
    val r = load("ai-anthropic", keys).aiTurn("anthropic", request(), http)
    assertEquals(
      r.turn,
      Some(
        Left(
          AiFailure.Error(
            "ConnectException: Exception when sending request: POST https://api.anthropic.com/v1/messages"
          )
        )
      )
    )

  test("the providers ask for the loop's notes and verbatim failures, and offer tools"):
    val settings = ProviderSettings(com.histmersion.plugins.ai.ProviderSettings.BaseUrl("http://127.0.0.1:1"), com.histmersion.plugins.ai.ProviderSettings.ApiKey(""), com.histmersion.plugins.ai.ProviderSettings.StrictTools(false), com.histmersion.plugins.ai.ProviderSettings.TimeoutSeconds(1))
    List(AnthropicProvider(settings), ChatCompletionsProvider.openAi(settings), ChatCompletionsProvider.local(settings))
      .foreach { p =>
        assertEquals(p.capabilities, AiCapabilities(com.histmersion.plugin.AiCapabilities.Tools(true), com.histmersion.plugin.AiCapabilities.StructuredOutput(true), com.histmersion.plugin.AiCapabilities.IterationMarkers(true), com.histmersion.plugin.AiCapabilities.VerbatimFailures(true)), p.title)
        assert(!p.spendsPersonalPlan, p.title)
      }
