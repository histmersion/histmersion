package com.histmersion
package plugins.ai
import com.histmersion.util.problem.{External, Problem}

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.plugin.*
import com.histmersion.lint.{LintRule, tolerated}

/** The chat-completions API's request and answer, as pure functions. */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class ChatWireSuite extends munit.FunSuite:

  private val tool =
    AiTool(
      AiTool.Name("facets"),
      AiTool.Description("Values"),
      AiTool.InputSchema("""{"type":"object","properties":{"field":{"type":"string"}},"required":["field"]}""")
    )

  private val r = AiTurn.Request(
    AiTurn.Request.Model("gpt-x"),
    AiTurn.Request.System("SYS"),
    List(
      AiMessage.User(AiMessage.User.Text("u")),
      AiMessage.Assistant(
        AiMessage.Assistant.Text("looking"),
        List(AiTool.Call(AiTool.Call.Id("c1"), AiTool.Call.Tool("facets"), AiTool.Call.Input("""{"field":"tags"}""")))
      ),
      AiMessage.ToolResult(
        AiMessage.ToolResult.CallId("c1"),
        AiMessage.ToolResult.Tool("facets"),
        AiMessage.ToolResult.Output("ok")
      )
    ),
    List(tool),
    Some(AiTurn.Request.AnswerSchema("""{"type":"object","properties":{"w":{"type":"string"}}}""")),
    AiTurn.Request.MaxTokens(64),
    AiTurn.Request.Iteration(2),
    AiTurn.Request.MaxIterations(3),
    None,
    Some(AiTurn.Request.AnswerDescription("one word"))
  )

  test("a turn's body: the system message first, the tool calls and results, the strict answer format, the cap"):
    assertEquals(
      ChatRequest.of(r, strictTools = ProviderSettings.StrictTools(false)).body,
      """{"messages":[{"content":"SYS","role":"system"},{"content":"u","role":"user"},""" +
        """{"content":"looking","tool_calls":[{"type":"function","function":{"arguments":"{\"field\":\"tags\"}","name":"facets"},"id":"c1"}],"role":"assistant"},""" +
        """{"content":"ok","tool_call_id":"c1","role":"tool"}],"model":"gpt-x",""" +
        """"response_format":{"type":"json_schema","json_schema":{"name":"final_response","strict":true,"schema":""" +
        """{"additionalProperties":false,"required":["w"],"type":"object","properties":{"w":{"type":["string","null"]}}},"description":"one word"}},""" +
        """"tools":[{"type":"function","function":{"name":"facets","description":"Values","parameters":""" +
        """{"type":"object","properties":{"field":{"type":"string"}},"required":["field"]}}}],"max_completion_tokens":64}"""
    )

  test("strict tools: the schema in strict form and the function marked strict; no tools, no cap: left out"):
    val strict = ChatRequest.of(r, strictTools = ProviderSettings.StrictTools(true)).body
    assert(strict.value.contains(""""strict":true}}],"max_completion_tokens":64}"""), strict)
    assert(
      strict.value.contains(
        """"parameters":{"additionalProperties":false,"required":["field"],"type":"object","properties":{"field":{"type":"string"}}}"""
      ),
      strict
    )
    val bare = ChatRequest
      .of(
        r.copy(tools = Nil, maxTokens = AiTurn.Request.MaxTokens(0), answerSchema = None),
        ProviderSettings.StrictTools(false)
      )
      .body
    assert(
      !bare.value.contains("tools") && !bare.value.contains("max_completion") && !bare.value.contains(
        "response_format"
      ),
      bare
    )

  private val ok =
    """{"id":"c","object":"chat.completion","created":1,"model":"gpt-y","choices":[{"index":0,"message":{"role":"assistant",""" +
      """"content":null,"tool_calls":[{"type":"function","function":{"name":"a","arguments":"{}"}},{"id":"k","type":"function","function":{"arguments":"{}"}}]},""" +
      """"finish_reason":"tool_calls"}],"usage":{"prompt_tokens":9,"completion_tokens":3,"total_tokens":12,""" +
      """"prompt_tokens_details":{"cached_tokens":4},"completion_tokens_details":{"reasoning_tokens":2}}}"""

  test("an answer: the first choice's text and calls (call_<n>, unknown when unnamed), its usage, its model"):
    assertEquals(
      chat(ChatAnswer.Status(200), ChatAnswer.Body(ok)).turn,
      Right(
        AiTurn(
          AiTurn.Text(""),
          List(
            AiTool.Call(AiTool.Call.Id("call_0"), AiTool.Call.Tool("a"), AiTool.Call.Input("{}")),
            AiTool.Call(AiTool.Call.Id("k"), AiTool.Call.Tool("unknown"), AiTool.Call.Input("{}"))
          ),
          AiStop.ToolUse,
          Some(
            AiUsage(
              AiUsage.Input(9),
              AiUsage.Output(3),
              AiUsage.CacheRead(4),
              AiUsage.CacheWrite(0),
              AiUsage.Reasoning(2)
            )
          ),
          Some(AiTurn.ServedBy("gpt-y"))
        )
      )
    )

  private def chat(status: ChatAnswer.Status, body: ChatAnswer.Body) = ChatAnswer(
    Problem.Upstream.Name("openai"),
    Problem.Upstream.Url("https://api.example/v1/chat/completions"),
    status,
    body
  )

  /** What the answer said: an error status and its words, or why a success answer cannot be read. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def said(p: Problem): String = p match
    case Problem.Upstream.Http(_, _, status, body, _)    => s"$status $body"
    case Problem.Upstream.BadReply(_, _, _, reported, _) => s"$reported"
    case other                                           => s"$other"

  test("a success answer that cannot be read says why, at the field"):
    assertEquals(
      chat(ChatAnswer.Status(200), ChatAnswer.Body("[]")).turn.left.map(said),
      Left("expected an object")
    )
    assertEquals(
      chat(ChatAnswer.Status(200), ChatAnswer.Body(ok.replace(""""role":"assistant",""", ""))).turn.left
        .map(said),
      Left("choices[0].message.role: missing required field")
    )

  test("an error status: its message; none, the status and body; another shape, its body's start"):
    assertEquals(
      chat(ChatAnswer.Status(429), ChatAnswer.Body("""{"error":{"message":"slow down"}}""")).turn.left
        .map(said),
      Left("429 slow down")
    )
    assertEquals(
      chat(ChatAnswer.Status(500), ChatAnswer.Body("""{"error":{"type":"server_error"}}""")).turn.left
        .map(said),
      Left("""500 statusCode: 500, response: {"error":{"type":"server_error"}}""")
    )
    assertEquals(
      chat(ChatAnswer.Status(500), ChatAnswer.Body("""{"error":null}""")).turn.left.map(said),
      Left("""500 {"error":null}""")
    )
    assertEquals(
      chat(ChatAnswer.Status(400), ChatAnswer.Body("x" * 700)).turn.left.map(said),
      Left("400 " + "x" * External.ReplyExcerpt.Limit)
    )
