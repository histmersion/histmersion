package com.histmersion
package server.agents

import com.histmersion.domain.Problem
import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.domain.library.workflows.*

import com.histmersion.domain.automation.agents.{AnswerSchemas, Engine, Prompt}
import com.histmersion.domain.automation.managers.Zero

import sttp.ai.core.agent.*
import com.histmersion.util.storage.Stores
import com.histmersion.util.storage.Resources
import com.histmersion.util.system.JvmHome
import com.histmersion.domain.automation.agents.LlmCall
import com.histmersion.domain.automation.agents.AgentsControllers

/** How a step reads an attempt that did not produce its typed answer: an answer over the output cap (#1450, a
  * configuration error), and an answer that did not decode (#941, the decoder's path and message, for the record and
  * the retry). Pure: recorded `claude -p` results, no process, no DB.
  */
class StepFailureSuite extends munit.FunSuite:

  private given Stores = Stores.disk
  private given Resources = Resources.classpath(getClass.getClassLoader)
  private given java.time.Clock = java.time.Clock.systemUTC()
  private given JvmHome = JvmHome(java.nio.file.Path.of("target/test-home").toAbsolutePath)
  private val OverCap =
    """{"type":"result","subtype":"success","is_error":true,"num_turns":1,"result":"API Error: Claude's response exceeded """ +
      """the 4096 output token maximum. To configure this behavior, set the CLAUDE_CODE_MAX_OUTPUT_TOKENS environment variable.",""" +
      """"usage":{"input_tokens":70000,"output_tokens":16384}}"""

  private def claudeResult(lines: CliProcess.Run.Lines*) =
    ClaudeCode.result(ClaudeCode.parse(lines.toList), LlmCall.User("u"), LibraryAnswers.notes).toOption.get

  test("#1450 Claude Code's output-cap error is recognised, with the cap it names"):
    val r = claudeResult(CliProcess.Run.Lines(ClaudeStreams.Init), CliProcess.Run.Lines(OverCap))
    assert(r.finalAnswer.isLeft)
    assertEquals(Engine.outputCapHit(r), Some(4096))
    val msg = Engine.outputCapMessage(
      "cold-reader-1",
      Engine.OutputCapLimit(4096),
      Engine.Count(4096),
      AgentsControllers.ModelName("claude-code:haiku"),
      Engine.capKey(Engine.CapKeyOverridden(false)).value
    )
    assert(msg.contains("agents.step.max-tokens (HISTMERSION_AGENTS_MAX_TOKENS)") && msg.contains("resume"), msg)
    assert(Engine.capKey(Engine.CapKeyOverridden(true)).value.contains("fixer-max-tokens"))

  test("#1450 an ordinary error, a usage limit or a good answer is not an output-cap hit"):
    val ok =
      """{"type":"result","subtype":"success","is_error":false,"num_turns":1,"result":"","structured_output":{"notes":"x"},"usage":{}}"""
    assertEquals(
      Engine.outputCapHit(claudeResult(CliProcess.Run.Lines(ClaudeStreams.Init), CliProcess.Run.Lines(ok))),
      None
    )
    val err =
      """{"type":"result","subtype":"error_during_execution","is_error":true,"num_turns":1,"result":"boom","usage":{}}"""
    assertEquals(
      Engine.outputCapHit(claudeResult(CliProcess.Run.Lines(ClaudeStreams.Init), CliProcess.Run.Lines(err))),
      None
    )

  test("#941 a decoding failure names the path and the decoder's message, then the start of the answer"):
    val raw = """{"notes": 5}"""
    val f =
      LibraryAnswers.notes.codec
        .decodeJson(io.circe.parser.parse(raw).toOption.get)
        .left
        .map(AgentParseError(raw, _))
    val p = Engine.answerProblem(f.swap.toOption.get, FinishReason.NaturalStop)
    assert(p.value.startsWith("the answer does not match the schema at .notes: "), p)
    assert(p.value.contains("expecting string"), p)
    assert(p.value.endsWith(s"the answer began: $raw"), p)
    assertEquals(Engine.undecoded(f), Some(raw))

  test("#941 a missing field deep in a list is located by index"):
    val raw = """{"findings":[{"claim":"x"}]}"""
    val f =
      LibraryAnswers.findings.codec
        .decodeJson(io.circe.parser.parse(raw).toOption.get)
        .left
        .map(AgentParseError(raw, _))
    val p = Engine.answerProblem(f.swap.toOption.get, FinishReason.NaturalStop)
    assert(p.value.contains(".findings[0]."), p)

  test("#941 not JSON, no answer at all, and a long answer is quoted only in part"):
    val bad = AgentParseError("{nope", io.circe.ParsingFailure("expected json value", new RuntimeException("x")))
    assert(
      Engine
        .answerProblem(bad, FinishReason.NaturalStop)
        .value
        .startsWith("the answer is not valid JSON: expected json value")
    )
    val none = AgentIncomplete("", FinishReason.MaxIterations, None)
    assertEquals(
      Engine.answerProblem(none, FinishReason.MaxIterations),
      "the answer is not the JSON the schema asks for (iteration limit)"
    )
    assertEquals(Engine.undecoded(Left(none)), None, "nothing to hand back")
    // sttp-ai's HTTP loop: out of turns after an undecodable answer, the last decoder error is the cause
    val df = io.circe.DecodingFailure(
      "Got value '5' with wrong type, expecting string",
      List(io.circe.CursorOp.DownField("notes"))
    )
    val gaveUp = AgentIncomplete("""{"notes": 5}""", FinishReason.MaxIterations, Some(df))
    val p = Engine.answerProblem(gaveUp, FinishReason.MaxIterations)
    assert(
      p.value.startsWith("the answer does not match the schema at .notes: Got value '5'") && p.value.contains(
        "(iteration limit)"
      ),
      p
    )
    assertEquals(Engine.undecoded(Left(gaveUp)), Some("""{"notes": 5}"""))
    val long = AgentParseError("x" * 5000, RuntimeException("odd"))
    assert(Engine.answerProblem(long, FinishReason.NaturalStop).value.length < Engine.AnswerExcerpt + 100)

  test("#941 the retry prompt carries the decoder's error and the undecoded answer to amend"):
    val raw = """{"notes": 5}"""
    val problem = Problem("the answer does not match the schema at .notes: String")
    val prompt = Prompt.retry(List(problem), Some(raw))
    assert(prompt.contains(s"- $problem") && prompt.contains(s"<previous-answer>\n$raw\n</previous-answer>"), prompt)

  test("#941 a text answer that is not JSON keeps the parser's error: recorded, and the text goes back to amend"):
    // a truncated object (the answer ran out mid-string), prose, a JSON array; a well-formed object is no problem
    assert(CliOutcomes.textProblem(CliOutcomes.TextProblemText("""{"notes": "abc""")).exists(_.message.nonEmpty))
    assert(
      CliOutcomes
        .textProblem(CliOutcomes.TextProblemText("I could not find it."))
        .exists(_.message.contains("no JSON object"))
    )
    assert(CliOutcomes.textProblem(CliOutcomes.TextProblemText("[1, 2]")).exists(_.message.contains("not an object")))
    assertEquals(CliOutcomes.textProblem(CliOutcomes.TextProblemText("""```json\n{"notes":"x"}\n```""")), None)
    assertEquals(CliOutcomes.textProblem(CliOutcomes.TextProblemText("  ")), None)
    val text = """Here it is: {"notes": "abc"""
    val ok = """{"type":"result","subtype":"success","is_error":false,"num_turns":2,"result":""" +
      io.circe.Json.fromString(text).noSpaces + ""","usage":{}}"""
    val r = claudeResult(CliProcess.Run.Lines(ClaudeStreams.Init), CliProcess.Run.Lines(ok))
    val f = r.finalAnswer.swap.toOption.get
    val p = Engine.answerProblem(f, r.finishReason)
    assert(
      p.value.startsWith("the answer is not valid JSON: ") && p.value.contains("(answered)") && p.value.contains(
        "the answer began: Here it is"
      ),
      p
    )
    assert(!p.value.contains("not the JSON the schema asks for"), p)
    assertEquals(Engine.undecoded(r.finalAnswer), Some(text))
    // Codex / opencode: the final text is the answer
    val cli = CliOutcomes.result(
      CliOutcome(
        Vector.empty,
        Vector.empty,
        Some(CliOutcome.FinalText("no json")),
        TokenUsage.Zero,
        None,
        CliOutcome.Turns(1)
      ),
      LlmCall.User("u"),
      LibraryAnswers.notes,
      None
    )
    assert(
      Engine
        .answerProblem(cli.finalAnswer.swap.toOption.get, cli.finishReason)
        .value
        .startsWith("the answer is not valid JSON")
    )
    // an error result is not a parse problem: no cause, as before
    val failed = CliOutcomes.result(
      CliOutcome(
        Vector.empty,
        Vector.empty,
        Some(CliOutcome.FinalText("boom")),
        TokenUsage.Zero,
        Some(api.Problem("crash")),
        CliOutcome.Turns(1)
      ),
      LlmCall.User("u"),
      LibraryAnswers.notes,
      None
    )
    assertEquals(Engine.undecoded(failed.finalAnswer), None)
