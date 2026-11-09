package com.histmersion
package plugins.ai

import com.histmersion.plugin.*
import com.histmersion.testsupport.ComparedByValue.given

/** The Anthropic Messages API's request and answer, as pure functions: what is sent for a turn, what an answer says. */
class AnthropicWireSuite extends munit.FunSuite:

  private val tool =
    AiTool(
      AiTool.Name("facets"),
      AiTool.Description("The values of one field"),
      AiTool.InputSchema("""{"type":"object","properties":{"field":{"type":"string"}}}""")
    )

  private def request(
      messages: List[AiMessage],
      tools: List[AiTool] = Nil,
      maxTokens: AiTurn.Request.MaxTokens = AiTurn.Request.MaxTokens(50)
  ) =
    AiTurn.Request(
      AiTurn.Request.Model("claude-x"),
      AiTurn.Request.System("SYS"),
      messages,
      tools,
      None,
      maxTokens,
      AiTurn.Request.Iteration(1),
      AiTurn.Request.MaxIterations(3)
    )

  test("a turn's body: the conversation as content blocks, the system prompt, the cap, the tools, nulls dropped"):
    val body = AnthropicRequest
      .of(
        request(
          List(
            AiMessage.User(AiMessage.User.Text("u")),
            AiMessage.Assistant(
              AiMessage.Assistant.Text(""),
              List(
                AiTool.Call(
                  AiTool.Call.Id("t1"),
                  AiTool.Call.Tool("facets"),
                  AiTool.Call.Input("""{"field":"tags","prefix":null}""")
                )
              )
            ),
            AiMessage.ToolResult(
              AiMessage.ToolResult.CallId("t1"),
              AiMessage.ToolResult.Tool("facets"),
              AiMessage.ToolResult.Output("ok")
            ),
            AiMessage.User(AiMessage.User.Text("[Iteration 2 of 3]"))
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
    val r = request(List(AiMessage.User(AiMessage.User.Text("u"))), maxTokens = AiTurn.Request.MaxTokens(0)).copy(
      answerSchema = Some(AiTurn.Request.AnswerSchema("""{"type":"object","properties":{"w":{"type":"string"}}}""")),
      answerSchemaRendered = Some(
        AiTurn.Request.AnswerSchemaRendered("""{"$schema":"s","type":"object","properties":{"w":{"type":"string"}}}""")
      )
    )
    val body = AnthropicRequest.of(r).map(_.body.value).getOrElse("")
    assert(body.contains(""""max_tokens":4096"""), body)
    assert(
      body.endsWith(
        """"output_config":{"format":{"type":"json_schema","schema":{"$schema":"s","type":"object","properties":{"w":{"type":"string"}},"additionalProperties":false}}}}"""
      ),
      body
    )

  test("a tool call whose input is not an object cannot be sent, and says why"):
    val r = request(
      List(
        AiMessage.Assistant(
          AiMessage.Assistant.Text(""),
          List(AiTool.Call(AiTool.Call.Id("t"), AiTool.Call.Tool("x"), AiTool.Call.Input("[1]")))
        )
      )
    )
    assert(
      AnthropicRequest.of(r).left.exists(_.legacyText.startsWith("DecodingFailure")),
      AnthropicRequest.of(r).toString
    )

  private val ok =
    """{"id":"m","type":"message","role":"assistant","model":"claude-y","content":[{"type":"thinking","thinking":"…"},""" +
      """{"type":"text","text":"first"},{"type":"text","text":"second"}],"stop_reason":"max_tokens","usage":{"input_tokens":10,"output_tokens":2}}"""

  test("an answer: its first text block, its stop reason, its usage, the model that served it"):
    assertEquals(
      AnthropicAnswer(AnthropicAnswer.Status(200), AnthropicAnswer.Body(ok)).turn,
      Right(
        AiTurn(
          AiTurn.Text("first"),
          Nil,
          AiStop.MaxTokens,
          Some(AiUsage(AiUsage.Input(10), AiUsage.Output(2))),
          Some(AiTurn.ServedBy("claude-y"))
        )
      )
    )

  test("a failure: an error status's body as it came; an error in a success answer by its message; else why"):
    assertEquals(
      AnthropicAnswer(AnthropicAnswer.Status(529), AnthropicAnswer.Body("""{"type":"error"}""")).turn.left
        .map(_.legacyText),
      Left("""{"type":"error"}""")
    )
    assertEquals(
      AnthropicAnswer(
        AnthropicAnswer.Status(200),
        AnthropicAnswer.Body("""{"type":"error","error":{"type":"overloaded_error","message":"Overloaded"}}""")
      ).turn.left.map(_.legacyText),
      Left("Overloaded")
    )
    assertEquals(
      AnthropicAnswer(AnthropicAnswer.Status(200), AnthropicAnswer.Body("""{"id":"m"""")).turn.left.map(_.legacyText),
      Left("exhausted input")
    )
    assertEquals(
      AnthropicAnswer(AnthropicAnswer.Status(200), AnthropicAnswer.Body("""{"id":"m"}""")).turn.left.map(_.legacyText),
      Left("DecodingFailure at .error: Missing required field")
    )
    // a content block of an unknown type is not a message
    assertEquals(
      AnthropicAnswer(
        AnthropicAnswer.Status(200),
        AnthropicAnswer.Body(ok.replace("thinking\",\"thinking", "unknown\",\"thinking"))
      ).turn.left.map(_.legacyText),
      Left("DecodingFailure at .error: Missing required field")
    )

  test("only the models known to lack structured output are refused it"):
    assert(!AnthropicProvider.structuredOutput(AiTurn.Request.Model("claude-3-haiku-20240307")))
    assert(
      AnthropicProvider.structuredOutput(AiTurn.Request.Model("claude-sonnet-4-5")) && AnthropicProvider
        .structuredOutput(AiTurn.Request.Model("claude-next"))
    )
