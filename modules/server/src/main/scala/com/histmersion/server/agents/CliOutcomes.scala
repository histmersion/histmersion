package com.histmersion
package server.agents

import com.histmersion.domain.automation.agents.StrictJsonSchema

import io.circe.{parser, Json}
import io.circe.syntax.*
import sttp.ai.core.agent.*
import sttp.apispec.circe.*
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.domain.automation.agents.LlmCall
import com.histmersion.api.automation.Agents

object CliOutcomes:
  private given CanEqual[FinishReason, FinishReason] = CanEqual.derived

  /** The answer as the step's schema: the final text is JSON (code fences tolerated), a `null` field the same as an
    * absent one.
    */
  type TextProblemText = TextProblemText.T
  object TextProblemText extends Newtype[String, Pure]

  def result[Out](
      o: CliOutcome,
      user: LlmCall.User,
      schema: ResponseSchema[Out],
      modelId: Option[Agents.ModelRef.Model]
  ): AgentResult[Either[AgentFailure, Out]] =
    val raw = o.finalText.map(_.value).getOrElse("")
    val parsed = parser.parse(stripFence(raw))
    val finish = o.error.fold(FinishReason.NaturalStop)(e => FinishReason.Error(e.value.take(600)))
    val answer: Either[AgentFailure, Out] = parsed match
      // a null decodes as an absent field: Codex's strict schema makes the model answer `null` for
      // every optional field it would have left out (StrictJsonSchema), and a field with a default
      // (`tags: List[String] = Nil`) would otherwise refuse it
      case Right(j) => schema.codec.decodeJson(j.deepDropNullValues).left.map(e => AgentParseError(raw, e))
      // #941: an answer that ended normally but is not JSON keeps the parser's error as its cause
      case Left(pf) =>
        Left(AgentIncomplete(raw, finish, Option.when(finish == FinishReason.NaturalStop && raw.trim.nonEmpty)(pf)))
    val entries = ConversationEntry.UserPrompt(user.value) +: o.entries
    AgentResult(
      answer,
      math.max(1, o.turns.value),
      o.toolCalls,
      finish,
      o.usage,
      Seq(LlmCallUsage(modelId.map(_.value), o.usage)),
      ConversationHistory(entries)
    )

  /** #941: why a text answer that ended normally holds no JSON object — the parser's own error (where the JSON broke
    * off: a truncated answer, an unescaped quote), or that there is no object in it at all. `None` for an empty text
    * (nothing was answered) or a text that is a JSON object. What the step records and the retry is told, instead of
    * "not the JSON the schema asks for".
    */
  def textProblem(text: TextProblemText): Option[io.circe.ParsingFailure] =
    val t = text.value.trim
    if t.isEmpty then None
    else if parser.parse(stripFence(t)).exists(j => !j.isObject) then
      Some(io.circe.ParsingFailure("the answer is JSON but not an object", null))
    else
      val fenced = "(?s)```(?:json)?\\s*(\\{.*)\\s*```".r.findFirstMatchIn(t).map(_.group(1).trim)
      val start = t.indexOf('{')
      val candidate = fenced.orElse(Option.when(start >= 0)(if t.lastIndexOf('}') > start then
        t.substring(start, t.lastIndexOf('}') + 1)
      else t.substring(start)))
      candidate match
        case None    => Some(io.circe.ParsingFailure("there is no JSON object in the answer (it is prose)", null))
        case Some(c) =>
          parser.parse(c) match
            case Left(pf)                => Some(pf)
            case Right(j) if !j.isObject => Some(io.circe.ParsingFailure("the answer is JSON but not an object", null))
            case Right(_)                => None

  def stripFence(s: String): String =
    val t = s.trim
    if t.startsWith("```") then
      t.dropWhile(_ != '\n').drop(1).reverse.dropWhile(_ != '`').dropWhile(_ == '`').reverse.trim
    else t

  /** A plan's usage limit as a CLI words it ("You've hit your usage limit", "rate limit reached"). */
  def isLimit(message: ClaudeCode.Limit.Sentence): Boolean =
    val low = message.value.toLowerCase(java.util.Locale.ROOT)
    low.contains("usage limit") || low.contains("rate limit reached") || low.contains("rate_limit_reached") ||
    low.contains("hit your limit") || low.contains("quota exceeded")
