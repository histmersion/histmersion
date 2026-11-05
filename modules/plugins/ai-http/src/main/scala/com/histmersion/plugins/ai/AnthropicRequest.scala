package com.histmersion.plugins.ai

import com.histmersion.plugin.AiMessage
import io.circe.{Encoder, Json}
import io.circe.parser.parse
import com.histmersion.plugin.AiTurn

/** One call to the Anthropic Messages API (`POST v1/messages`), its body as the API takes it: the model, the
  * conversation as user and assistant messages of content blocks (tool calls as `tool_use`, their results as
  * `tool_result`), the system prompt, the output cap, the tools offered (each input an object schema) and the answer's
  * schema as `output_config`. Unset fields are left out; the tools' schemas are sent exactly as given.
  */
final case class AnthropicRequest(json: Json):
  def body: com.histmersion.plugins.ai.AnthropicRequest.BodyResult = json.noSpaces

object AnthropicRequest:

  /** The output cap when the step names none. */
  type BodyResult = BodyResult.T
  object BodyResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OfResultPart1 = OfResultPart1.T
  object OfResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TextT = TextT.T
  object TextT extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MessageRole = MessageRole.T
  object MessageRole extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MessagesResultPart1 = MessagesResultPart1.T
  object MessagesResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ToolUsesResultPart1 = ToolUsesResultPart1.T
  object ToolUsesResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ToolUsesCallsPart1 = ToolUsesCallsPart1.T
  object ToolUsesCallsPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ToolUsesCallsPart2 = ToolUsesCallsPart2.T
  object ToolUsesCallsPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ToolUsesCallsPart3 = ToolUsesCallsPart3.T
  object ToolUsesCallsPart3 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val DefaultMaxTokens = 4096

  /** The request for `r`, or why it cannot be built (a tool call whose input is not a JSON object), described as the
    * exception that says so.
    */
  def of(r: AiTurn.Request): Either[com.histmersion.plugins.ai.AnthropicRequest.OfResultPart1, AnthropicRequest] =
    messages(r.messages).map { msgs =>
      val tools = Option.when(r.tools.nonEmpty)(
        Json.fromValues(r.tools.map { t =>
          val raw = parse(t.inputSchema).getOrElse(Json.obj())
          Json.obj(
            "name" -> Json.fromString(t.name),
            "description" -> Json.fromString(t.description),
            "input_schema" -> SchemaJson(raw).objectTyped.json
          )
        })
      )
      val schema = r.answerSchemaRendered.orElse(r.answerSchema).flatMap(s => parse(s).toOption)
      val output = schema.map(s =>
        Json.obj(
          "format" -> Json.obj("type" -> Json.fromString("json_schema"), "schema" -> SchemaJson(s).closed.json)
        )
      )
      val fields = List(
        "model" -> Some(Json.fromString(r.model)),
        "messages" -> Some(Json.fromValues(msgs)),
        "system" -> Some(Json.fromString(r.system)),
        "max_tokens" -> Some(Json.fromInt(if r.maxTokens > 0 then r.maxTokens else DefaultMaxTokens)),
        "tools" -> tools,
        "output_config" -> output
      ).collect { case (k, Some(v)) => k -> v }
      AnthropicRequest(Json.fromFields(fields))
    }

  private def text(t: com.histmersion.plugins.ai.AnthropicRequest.TextT): Json = Json.obj("text" -> Json.fromString(t), "type" -> Json.fromString("text"))

  private def message(role: com.histmersion.plugins.ai.AnthropicRequest.MessageRole, content: List[Json]): Json =
    Json.obj("role" -> Json.fromString(role), "content" -> Json.fromValues(content))

  private def messages(ms: List[AiMessage]): Either[com.histmersion.plugins.ai.AnthropicRequest.MessagesResultPart1, List[Json]] =
    @annotation.tailrec
    def go(rest: List[AiMessage], acc: List[Json]): Either[String, List[Json]] = rest match
      case Nil                                      => Right(acc.reverse)
      case AiMessage.User(t) :: tail                => go(tail, message("user", List(text(t))) :: acc)
      case AiMessage.ToolResult(id, _, out) :: tail =>
        val block = Json.obj(
          "tool_use_id" -> Json.fromString(id),
          "content" -> Json.fromString(out),
          "type" -> Json.fromString("tool_result")
        )
        go(tail, message("user", List(block)) :: acc)
      case AiMessage.Assistant(t, calls) :: tail =>
        toolUses(calls.map(c => (c.id, c.tool, c.input)), Nil) match
          case Left(why)   => Left(why)
          case Right(uses) =>
            go(tail, message("assistant", (if t.nonEmpty then List(text(t)) else Nil) ++ uses) :: acc)
    go(ms, Nil).map(dropNulls)

  @annotation.tailrec
  private def toolUses(calls: List[(com.histmersion.plugins.ai.AnthropicRequest.ToolUsesCallsPart1, com.histmersion.plugins.ai.AnthropicRequest.ToolUsesCallsPart2, com.histmersion.plugins.ai.AnthropicRequest.ToolUsesCallsPart3)], acc: List[Json]): Either[com.histmersion.plugins.ai.AnthropicRequest.ToolUsesResultPart1, List[Json]] =
    calls match
      case Nil                       => Right(acc.reverse)
      case (id, name, input) :: tail =>
        parse(input).flatMap(_.as[Map[String, Json]]) match
          case Left(e)  => Left(s"${e.getClass.getSimpleName}: ${e.getMessage}")
          case Right(m) =>
            val use = Json.obj(
              "id" -> Json.fromString(id),
              "name" -> Json.fromString(name),
              "input" -> Encoder[Map[String, Json]].apply(m),
              "type" -> Json.fromString("tool_use")
            )
            toolUses(tail, use :: acc)

  /** The conversation without null values (an unset field and a null in a tool call's input alike, as the API has
    * always been sent them).
    */
  private def dropNulls(ms: List[Json]): List[Json] = ms.map(_.deepDropNullValues)
