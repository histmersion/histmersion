package com.histmersion.plugins.ai

import com.histmersion.plugin.*

/** The Anthropic Messages API's request and answer, as pure functions: what is sent for a turn, what an answer says. */
class AnthropicWireSuite extends munit.FunSuite:

  private val tool =
    AiTool("facets", "The values of one field", """{"type":"object","properties":{"field":{"type":"string"}}}""")

  private def request(messages: List[AiMessage], tools: List[AiTool] = Nil, maxTokens: Int = 50) =
    AiTurn.Request("claude-x", "SYS", messages, tools, None, maxTokens, 1, 3)

  test("a turn's body: the conversation as content blocks, the system prompt, the cap, the tools, nulls dropped"):
    val body = AnthropicRequest
      .of(
        request(
          List(
            AiMessage.User("u"),
            AiMessage.Assistant("", List(AiTool.Call("t1", "facets", """{"field":"tags","prefix":null}"""))),
            AiMessage.ToolResult("t1", "facets", "ok"),
            AiMessage.User("[Iteration 2 of 3]")
          ),
          List(tool)
        )
      )
      .map(_.body)
    assertEquals(
      body,
      Right(
        """{"model":"claude-x","messages":[{"role":"user","content":[{"text":"u","type":"text"}]},""" +
          """{"role":"assistant","content":[{"id":"t1","name":"facets","input":{"field":"tags"},"type":"tool_use"}]},""" +
          """{"role":"user","content":[{"tool_use_id":"t1","content":"ok","type":"tool_result"}]},""" +
          """{"role":"user","content":[{"text":"[Iteration 2 of 3]","type":"text"}]}],"system":"SYS","max_tokens":50,""" +
          """"tools":[{"name":"facets","description":"The values of one field","input_schema":{"type":"object","properties":{"field":{"type":"string"}}}}]}"""
      )
    )

  test("the answer's schema as output_config, closed; the default cap when the step names none"):
    val r = request(List(AiMessage.User("u")), maxTokens = 0).copy(
      answerSchema = Some("""{"type":"object","properties":{"w":{"type":"string"}}}"""),
      answerSchemaRendered = Some("""{"$schema":"s","type":"object","properties":{"w":{"type":"string"}}}""")
    )
    val body = AnthropicRequest.of(r).map(_.body).getOrElse("")
    assert(body.contains(""""max_tokens":4096"""), body)
    assert(
      body.endsWith(
        """"output_config":{"format":{"type":"json_schema","schema":{"$schema":"s","type":"object","properties":{"w":{"type":"string"}},"additionalProperties":false}}}}"""
      ),
      body
    )

  test("a tool call whose input is not an object cannot be sent, and says why"):
    val r = request(List(AiMessage.Assistant("", List(AiTool.Call("t", "x", "[1]")))))
    assert(AnthropicRequest.of(r).left.exists(_.startsWith("DecodingFailure")), AnthropicRequest.of(r).toString)

  private val ok =
    """{"id":"m","type":"message","role":"assistant","model":"claude-y","content":[{"type":"thinking","thinking":"…"},""" +
      """{"type":"text","text":"first"},{"type":"text","text":"second"}],"stop_reason":"max_tokens","usage":{"input_tokens":10,"output_tokens":2}}"""

  test("an answer: its first text block, its stop reason, its usage, the model that served it"):
    assertEquals(
      AnthropicAnswer(200, ok).turn,
      Right(AiTurn("first", Nil, AiStop.MaxTokens, Some(AiUsage(10, 2)), Some("claude-y")))
    )

  test("a failure: an error status's body as it came; an error in a success answer by its message; else why"):
    assertEquals(AnthropicAnswer(529, """{"type":"error"}""").turn, Left("""{"type":"error"}"""))
    assertEquals(
      AnthropicAnswer(200, """{"type":"error","error":{"type":"overloaded_error","message":"Overloaded"}}""").turn,
      Left("Overloaded")
    )
    assertEquals(AnthropicAnswer(200, """{"id":"m"""").turn, Left("exhausted input"))
    assertEquals(AnthropicAnswer(200, """{"id":"m"}""").turn, Left("DecodingFailure at .error: Missing required field"))
    // a content block of an unknown type is not a message
    assertEquals(
      AnthropicAnswer(200, ok.replace("thinking\",\"thinking", "unknown\",\"thinking")).turn,
      Left("DecodingFailure at .error: Missing required field")
    )

  test("only the models known to lack structured output are refused it"):
    assert(!AnthropicProvider.structuredOutput("claude-3-haiku-20240307"))
    assert(AnthropicProvider.structuredOutput("claude-sonnet-4-5") && AnthropicProvider.structuredOutput("claude-next"))
