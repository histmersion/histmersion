package com.histmersion.plugins.ai

import com.histmersion.plugin.*

/** The chat-completions API's request and answer, as pure functions. */
class ChatWireSuite extends munit.FunSuite:

  private val tool =
    AiTool("facets", "Values", """{"type":"object","properties":{"field":{"type":"string"}},"required":["field"]}""")

  private val r = AiTurn.Request(
    "gpt-x",
    "SYS",
    List(
      AiMessage.User("u"),
      AiMessage.Assistant("looking", List(AiTool.Call("c1", "facets", """{"field":"tags"}"""))),
      AiMessage.ToolResult("c1", "facets", "ok")
    ),
    List(tool),
    Some("""{"type":"object","properties":{"w":{"type":"string"}}}"""),
    64,
    2,
    3,
    None,
    Some("one word")
  )

  test("a turn's body: the system message first, the tool calls and results, the strict answer format, the cap"):
    assertEquals(
      ChatRequest.of(r, strictTools = false).body,
      """{"messages":[{"content":"SYS","role":"system"},{"content":"u","role":"user"},""" +
        """{"content":"looking","tool_calls":[{"type":"function","function":{"arguments":"{\"field\":\"tags\"}","name":"facets"},"id":"c1"}],"role":"assistant"},""" +
        """{"content":"ok","tool_call_id":"c1","role":"tool"}],"model":"gpt-x",""" +
        """"response_format":{"type":"json_schema","json_schema":{"name":"final_response","strict":true,"schema":""" +
        """{"additionalProperties":false,"required":["w"],"type":"object","properties":{"w":{"type":["string","null"]}}},"description":"one word"}},""" +
        """"tools":[{"type":"function","function":{"name":"facets","description":"Values","parameters":""" +
        """{"type":"object","properties":{"field":{"type":"string"}},"required":["field"]}}}],"max_completion_tokens":64}"""
    )

  test("strict tools: the schema in strict form and the function marked strict; no tools, no cap: left out"):
    val strict = ChatRequest.of(r, strictTools = true).body
    assert(strict.contains(""""strict":true}}],"max_completion_tokens":64}"""), strict)
    assert(
      strict.contains(
        """"parameters":{"additionalProperties":false,"required":["field"],"type":"object","properties":{"field":{"type":"string"}}}"""
      ),
      strict
    )
    val bare = ChatRequest.of(r.copy(tools = Nil, maxTokens = 0, answerSchema = None), false).body
    assert(!bare.contains("tools") && !bare.contains("max_completion") && !bare.contains("response_format"), bare)

  private val ok =
    """{"id":"c","object":"chat.completion","created":1,"model":"gpt-y","choices":[{"index":0,"message":{"role":"assistant",""" +
      """"content":null,"tool_calls":[{"type":"function","function":{"name":"a","arguments":"{}"}},{"id":"k","type":"function","function":{"arguments":"{}"}}]},""" +
      """"finish_reason":"tool_calls"}],"usage":{"prompt_tokens":9,"completion_tokens":3,"total_tokens":12,""" +
      """"prompt_tokens_details":{"cached_tokens":4},"completion_tokens_details":{"reasoning_tokens":2}}}"""

  test("an answer: the first choice's text and calls (call_<n>, unknown when unnamed), its usage, its model"):
    assertEquals(
      ChatAnswer(200, ok).turn,
      Right(
        AiTurn(
          "",
          List(AiTool.Call("call_0", "a", "{}"), AiTool.Call("k", "unknown", "{}")),
          AiStop.ToolUse,
          Some(AiUsage(9, 3, 4, 0, 2)),
          Some("gpt-y")
        )
      )
    )

  test("a success answer that cannot be read says why, at the field"):
    assertEquals(
      ChatAnswer(200, "[]").turn,
      Left("DecodingFailure at : Got value '[]' with wrong type, expecting object")
    )
    assertEquals(
      ChatAnswer(200, ok.replace(""""role":"assistant",""", "")).turn,
      Left("DecodingFailure at .choices[0].message.role: Missing required field")
    )

  test("an error status: its message; none, the status and body; another shape, its body's first 500 characters"):
    assertEquals(ChatAnswer(429, """{"error":{"message":"slow down"}}""").turn, Left("slow down"))
    assertEquals(
      ChatAnswer(500, """{"error":{"type":"server_error"}}""").turn,
      Left("""statusCode: 500, response: {"error":{"type":"server_error"}}""")
    )
    assertEquals(ChatAnswer(500, """{"error":null}""").turn, Left("""{"error":null}"""))
    assertEquals(ChatAnswer(400, "x" * 700).turn, Left("x" * 500))
