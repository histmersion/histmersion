package com.histmersion
package plugins.ai

import com.histmersion.plugin.AiMessage
import io.circe.{Encoder, Json}
import io.circe.parser.parse
import com.histmersion.plugin.AiTurn
import com.histmersion.plugin.AiHttp
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.plugin.AiTool

/** One call to the Anthropic Messages API (`POST v1/messages`), its body as the API takes it: the model, the
  * conversation as user and assistant messages of content blocks (tool calls as `tool_use`, their results as
  * `tool_result`), the system prompt, the output cap, the tools offered (each input an object schema) and the answer's
  * schema as `output_config`. Unset fields are left out; the tools' schemas are sent exactly as given.
  */
final case class AnthropicRequest(json: Json):
  def body: AiHttp.PostBody = AiHttp.PostBody(json.noSpaces)

object AnthropicRequest:
  type Problem = Problem.T
  object Problem extends Newtype[String, Pure]
  type Role = Role.T
  object Role extends Newtype[String, Pure]

  /** The output cap when the step names none. */


  val DefaultMaxTokens = 4096

  /** The request for `r`, or why it cannot be built (a tool call whose input is not a JSON object), described as the
    * exception that says so.
    */
  def of(r: AiTurn.Request): Either[Problem, AnthropicRequest] =
    messages(r.messages).map { msgs =>
      val tools = Option.when(r.tools.nonEmpty)(
        Json.fromValues(r.tools.map { t =>
          val raw = parse(t.inputSchema.value).getOrElse(Json.obj())
          Json.obj(
            "name" -> Json.fromString(t.name.value),
            "description" -> Json.fromString(t.description.value),
            "input_schema" -> SchemaJson(raw).objectTyped.json
          )
        })
      )
      val schema = r.answerSchemaRendered.map(_.value).orElse(r.answerSchema.map(_.value)).flatMap(s => parse(s).toOption)
      val output = schema.map(s =>
        Json.obj(
          "format" -> Json.obj("type" -> Json.fromString("json_schema"), "schema" -> SchemaJson(s).closed.json)
        )
      )
      val fields = List(
        "model" -> Some(Json.fromString(r.model.value)),
        "messages" -> Some(Json.fromValues(msgs)),
        "system" -> Some(Json.fromString(r.system.value)),
        "max_tokens" -> Some(Json.fromInt(if r.maxTokens.value > 0 then r.maxTokens.value else DefaultMaxTokens)),
        "tools" -> tools,
        "output_config" -> output
      ).collect { case (k, Some(v)) => k -> v }
      AnthropicRequest(Json.fromFields(fields))
    }

  private def text(t: String): Json = Json.obj("text" -> Json.fromString(t), "type" -> Json.fromString("text"))

  private def message(role: Role, content: List[Json]): Json =
    Json.obj("role" -> Json.fromString((role).value), "content" -> Json.fromValues(content))

  private def messages(ms: List[AiMessage]): Either[Problem, List[Json]] =
    @annotation.tailrec
    def go(rest: List[AiMessage], acc: List[Json]): Either[Problem, List[Json]] = rest match
      case Nil                                      => Right(acc.reverse)
      case AiMessage.User(t) :: tail                => go(tail, message(Role("user"), List(text(t.value))) :: acc)
      case AiMessage.ToolResult(id, _, out) :: tail =>
        val block = Json.obj(
          "tool_use_id" -> Json.fromString(id.value),
          "content" -> Json.fromString(out.value),
          "type" -> Json.fromString("tool_result")
        )
        go(tail, message(Role("user"), List(block)) :: acc)
      case AiMessage.Assistant(t, calls) :: tail =>
        toolUses(calls.map(c => (c.id, c.tool, c.input)), Nil) match
          case Left(why)   => Left(why)
          case Right(uses) =>
            go(tail, message(Role("assistant"), (if t.value.nonEmpty then List(text(t.value)) else Nil) ++ uses) :: acc)
    go(ms, Nil).map(dropNulls)

  @annotation.tailrec
  private def toolUses(calls: List[(AiTool.Call.Id, AiTool.Call.Tool, AiTool.Call.Input)], acc: List[Json]): Either[Problem, List[Json]] =
    calls match
      case Nil                       => Right(acc.reverse)
      case (id, name, input) :: tail =>
        parse((input).value).flatMap(_.as[Map[String, Json]]) match
          case Left(e)  => Left(Problem(s"${e.getClass.getSimpleName}: ${e.getMessage}"))
          case Right(m) =>
            val use = Json.obj(
              "id" -> Json.fromString((id).value),
              "name" -> Json.fromString((name).value),
              "input" -> Encoder[Map[String, Json]].apply(m),
              "type" -> Json.fromString("tool_use")
            )
            toolUses(tail, use :: acc)

  /** The conversation without null values (an unset field and a null in a tool call's input alike, as the API has
    * always been sent them).
    */
  private def dropNulls(ms: List[Json]): List[Json] = ms.map(_.deepDropNullValues)
