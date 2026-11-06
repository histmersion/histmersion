package com.histmersion.plugins.ai

import com.histmersion.plugin.AiMessage
import io.circe.{Encoder, Json}
import io.circe.parser.parse
import com.histmersion.plugin.AiTurn

/** One call to an OpenAI-compatible chat-completions API (`POST chat/completions`), its body as the API takes it: the
  * system prompt as the first message, the conversation (tool calls on the assistant's messages, their results as
  * `tool` messages), the model, the answer's schema as a strict `json_schema` response format named `final_response`,
  * the tools offered (in OpenAI's strict form, and marked `strict`, when `strictTools` is on) and the output cap as
  * `max_completion_tokens`. Unset fields and empty lists are left out; the schemas are sent exactly as given.
  */
final case class ChatRequest(json: Json):
  def body: com.histmersion.plugins.ai.ChatRequest.BodyResult = _root_.com.histmersion.plugins.ai.ChatRequest.BodyResult(json.noSpaces)

object ChatRequest:

  /** The request for `r`. */
  type BodyResult = BodyResult.T
  object BodyResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OfStrictTools = _root_.com.histmersion.plugins.ai.ProviderSettings.StrictTools
  val OfStrictTools: _root_.com.histmersion.plugins.ai.ProviderSettings.StrictTools.type = _root_.com.histmersion.plugins.ai.ProviderSettings.StrictTools

  def of(r: AiTurn.Request, strictTools: com.histmersion.plugins.ai.ChatRequest.OfStrictTools): ChatRequest =
    val system = Json.obj("content" -> Json.fromString(r.system), "role" -> Json.fromString("system"))
    val format = r.answerSchemaRendered.orElse(r.answerSchema).flatMap(s => parse(s).toOption).map { s =>
      Json.obj(
        "type" -> Json.fromString("json_schema"),
        "json_schema" -> Json.fromFields(
          List(
            "name" -> Json.fromString("final_response"),
            "strict" -> Json.True,
            "schema" -> SchemaJson(s).strict.json
          ) ++ r.answerDescription.map(d => "description" -> Json.fromString(d))
        )
      )
    }
    val tools = Option.when(r.tools.nonEmpty)(Json.fromValues(r.tools.map { t =>
      val raw = parse(t.inputSchema).getOrElse(Json.obj())
      val schema = if (strictTools).value then SchemaJson(raw).strict.json else raw
      val parameters = Encoder[Map[String, Json]].apply(schema.asObject.map(_.toMap).getOrElse(Map.empty))
      Json.obj(
        "type" -> Json.fromString("function"),
        "function" -> Json.fromFields(
          List(
            "name" -> Json.fromString(t.name),
            "description" -> Json.fromString(t.description),
            "parameters" -> parameters
          ) ++ Option.when(strictTools)("strict" -> Json.True)
        )
      )
    }))
    val fields = List(
      "messages" -> Some(Json.fromValues(system :: r.messages.map(message))),
      "model" -> Some(Json.fromString(r.model)),
      "response_format" -> format,
      "tools" -> tools,
      "max_completion_tokens" -> Option.when(r.maxTokens > 0)(Json.fromInt(r.maxTokens))
    ).collect { case (k, Some(v)) => k -> v }
    ChatRequest(Json.fromFields(fields))

  private def message(m: AiMessage): Json = m match
    case AiMessage.User(t)             => Json.obj("content" -> Json.fromString(t), "role" -> Json.fromString("user"))
    case AiMessage.Assistant(t, calls) =>
      val toolCalls = calls.map { c =>
        Json.obj(
          "type" -> Json.fromString("function"),
          "function" -> Json.obj("arguments" -> Json.fromString(c.input), "name" -> Json.fromString(c.tool)),
          "id" -> Json.fromString(c.id)
        )
      }
      Json.fromFields(
        List("content" -> Json.fromString(t)) ++
          Option.when(toolCalls.nonEmpty)("tool_calls" -> Json.fromValues(toolCalls)) ++
          List("role" -> Json.fromString("assistant"))
      )
    case AiMessage.ToolResult(id, _, out) =>
      Json.obj(
        "content" -> Json.fromString(out),
        "tool_call_id" -> Json.fromString(id),
        "role" -> Json.fromString("tool")
      )
