package com.histmersion.plugins.ai

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.plugin.*

/** The chat-completions API's request and answer, as pure functions. */
class ChatWireSuite extends munit.FunSuite:

  private val tool =
    AiTool(com.histmersion.plugin.AiTool.Name("facets"), com.histmersion.plugin.AiTool.Description("Values"), AiTool.InputSchema("""{"type":"object","properties":{"field":{"type":"string"}},"required":["field"]}"""))

  private val r = AiTurn.Request(
    com.histmersion.plugin.AiTurn.Request.Model("gpt-x"),
    com.histmersion.plugin.AiTurn.Request.System("SYS"),
    List(
      AiMessage.User(com.histmersion.plugin.AiMessage.User.Text("u")),
      AiMessage.Assistant(com.histmersion.plugin.AiMessage.Assistant.Text("looking"), List(AiTool.Call(com.histmersion.plugin.AiTool.Call.Id("c1"), com.histmersion.plugin.AiTool.Call.Tool("facets"), AiTool.Call.Input("""{"field":"tags"}""")))),
      AiMessage.ToolResult(com.histmersion.plugin.AiMessage.ToolResult.CallId("c1"), com.histmersion.plugin.AiMessage.ToolResult.Tool("facets"), com.histmersion.plugin.AiMessage.ToolResult.Output("ok"))
    ),
    List(tool),
    Some(AiTurn.Request.AnswerSchema("""{"type":"object","properties":{"w":{"type":"string"}}}""")),
    com.histmersion.plugin.AiTurn.Request.MaxTokens(64),
    com.histmersion.plugin.AiTurn.Request.Iteration(2),
    com.histmersion.plugin.AiTurn.Request.MaxIterations(3),
    None,
    Some(com.histmersion.plugin.AiTurn.Request.AnswerDescription("one word"))
  )

  test("a turn's body: the system message first, the tool calls and results, the strict answer format, the cap"):
    assertEquals(
      ChatRequest.of(r, strictTools = com.histmersion.plugins.ai.ProviderSettings.StrictTools(false)).body,
      """{"messages":[{"content":"SYS","role":"system"},{"content":"u","role":"user"},""" +
        """{"content":"looking","tool_calls":[{"type":"function","function":{"arguments":"{\"field\":\"tags\"}","name":"facets"},"id":"c1"}],"role":"assistant"},""" +
        """{"content":"ok","tool_call_id":"c1","role":"tool"}],"model":"gpt-x",""" +
        """"response_format":{"type":"json_schema","json_schema":{"name":"final_response","strict":true,"schema":""" +
        """{"additionalProperties":false,"required":["w"],"type":"object","properties":{"w":{"type":["string","null"]}}},"description":"one word"}},""" +
        """"tools":[{"type":"function","function":{"name":"facets","description":"Values","parameters":""" +
        """{"type":"object","properties":{"field":{"type":"string"}},"required":["field"]}}}],"max_completion_tokens":64}"""
    )

  test("strict tools: the schema in strict form and the function marked strict; no tools, no cap: left out"):
    val strict = ChatRequest.of(r, strictTools = com.histmersion.plugins.ai.ProviderSettings.StrictTools(true)).body
    assert(strict.value.contains(""""strict":true}}],"max_completion_tokens":64}"""), strict)
    assert(
      strict.value.contains(
        """"parameters":{"additionalProperties":false,"required":["field"],"type":"object","properties":{"field":{"type":"string"}}}"""
      ),
      strict
    )
    val bare = ChatRequest.of(r.copy(tools = Nil, maxTokens = com.histmersion.plugin.AiTurn.Request.MaxTokens(0), answerSchema = None), com.histmersion.plugins.ai.ProviderSettings.StrictTools(false)).body
    assert(!bare.value.contains("tools") && !bare.value.contains("max_completion") && !bare.value.contains("response_format"), bare)

  private val ok =
    """{"id":"c","object":"chat.completion","created":1,"model":"gpt-y","choices":[{"index":0,"message":{"role":"assistant",""" +
      """"content":null,"tool_calls":[{"type":"function","function":{"name":"a","arguments":"{}"}},{"id":"k","type":"function","function":{"arguments":"{}"}}]},""" +
      """"finish_reason":"tool_calls"}],"usage":{"prompt_tokens":9,"completion_tokens":3,"total_tokens":12,""" +
      """"prompt_tokens_details":{"cached_tokens":4},"completion_tokens_details":{"reasoning_tokens":2}}}"""

  test("an answer: the first choice's text and calls (call_<n>, unknown when unnamed), its usage, its model"):
    assertEquals(
      ChatAnswer(com.histmersion.plugins.ai.ChatAnswer.Status(200), ChatAnswer.Body(ok)).turn,
      Right(
        AiTurn(
          com.histmersion.plugin.AiTurn.Text(""),
          List(AiTool.Call(com.histmersion.plugin.AiTool.Call.Id("call_0"), com.histmersion.plugin.AiTool.Call.Tool("a"), com.histmersion.plugin.AiTool.Call.Input("{}")), AiTool.Call(com.histmersion.plugin.AiTool.Call.Id("k"), com.histmersion.plugin.AiTool.Call.Tool("unknown"), com.histmersion.plugin.AiTool.Call.Input("{}"))),
          AiStop.ToolUse,
          Some(AiUsage(com.histmersion.plugin.AiUsage.Input(9), com.histmersion.plugin.AiUsage.Output(3), com.histmersion.plugin.AiUsage.CacheRead(4), com.histmersion.plugin.AiUsage.CacheWrite(0), com.histmersion.plugin.AiUsage.Reasoning(2))),
          Some(com.histmersion.plugin.AiTurn.ServedBy("gpt-y"))
        )
      )
    )

  test("a success answer that cannot be read says why, at the field"):
    assertEquals(
      ChatAnswer(com.histmersion.plugins.ai.ChatAnswer.Status(200), com.histmersion.plugins.ai.ChatAnswer.Body("[]")).turn,
      Left("DecodingFailure at : Got value '[]' with wrong type, expecting object")
    )
    assertEquals(
      ChatAnswer(com.histmersion.plugins.ai.ChatAnswer.Status(200), ChatAnswer.Body(ok.replace(""""role":"assistant",""", ""))).turn,
      Left("DecodingFailure at .choices[0].message.role: Missing required field")
    )

  test("an error status: its message; none, the status and body; another shape, its body's first 500 characters"):
    assertEquals(ChatAnswer(com.histmersion.plugins.ai.ChatAnswer.Status(429), ChatAnswer.Body("""{"error":{"message":"slow down"}}""")).turn, Left("slow down"))
    assertEquals(
      ChatAnswer(com.histmersion.plugins.ai.ChatAnswer.Status(500), ChatAnswer.Body("""{"error":{"type":"server_error"}}""")).turn,
      Left("""statusCode: 500, response: {"error":{"type":"server_error"}}""")
    )
    assertEquals(ChatAnswer(com.histmersion.plugins.ai.ChatAnswer.Status(500), ChatAnswer.Body("""{"error":null}""")).turn, Left("""{"error":null}"""))
    assertEquals(ChatAnswer(com.histmersion.plugins.ai.ChatAnswer.Status(400), ChatAnswer.Body("x" * 700)).turn, Left("x" * 500))
