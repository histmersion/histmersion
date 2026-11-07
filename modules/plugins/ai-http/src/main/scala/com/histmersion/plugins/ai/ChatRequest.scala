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
  def body: com.histmersion.plugin.AiHttp.PostBody = _root_.com.histmersion.plugin.AiHttp.PostBody(json.noSpaces)

object ChatRequest:

  /** The request for `r`. */
  def of(r: AiTurn.Request, strictTools: ProviderSettings.StrictTools): ChatRequest =
    val system = Json.obj("content" -> Json.fromString(r.system.value), "role" -> Json.fromString("system"))
    val format = r.answerSchemaRendered.map(_.value).orElse(r.answerSchema.map(_.value)).flatMap(s => parse(s).toOption).map { s =>
      Json.obj(
        "type" -> Json.fromString("json_schema"),
        "json_schema" -> Json.fromFields(
          List(
            "name" -> Json.fromString("final_response"),
            "strict" -> Json.True,
            "schema" -> SchemaJson(s).strict.json
          ) ++ r.answerDescription.map(d => "description" -> Json.fromString(d.value))
        )
      )
    }
    val tools = Option.when(r.tools.nonEmpty)(Json.fromValues(r.tools.map { t =>
      val raw = parse(t.inputSchema.value).getOrElse(Json.obj())
      val schema = if strictTools.value then SchemaJson(raw).strict.json else raw
      val parameters = Encoder[Map[String, Json]].apply(schema.asObject.map(_.toMap).getOrElse(Map.empty))
      Json.obj(
        "type" -> Json.fromString("function"),
        "function" -> Json.fromFields(
          List(
            "name" -> Json.fromString(t.name.value),
            "description" -> Json.fromString(t.description.value),
            "parameters" -> parameters
          ) ++ Option.when(strictTools.value)("strict" -> Json.True)
        )
      )
    }))
    val fields = List(
      "messages" -> Some(Json.fromValues(system :: r.messages.map(message))),
      "model" -> Some(Json.fromString(r.model.value)),
      "response_format" -> format,
      "tools" -> tools,
      "max_completion_tokens" -> Option.when(r.maxTokens.value > 0)(Json.fromInt(r.maxTokens.value))
    ).collect { case (k, Some(v)) => k -> v }
    ChatRequest(Json.fromFields(fields))

  private def message(m: AiMessage): Json = m match
    case AiMessage.User(t)             => Json.obj("content" -> Json.fromString(t.value), "role" -> Json.fromString("user"))
    case AiMessage.Assistant(t, calls) =>
      val toolCalls = calls.map { c =>
        Json.obj(
          "type" -> Json.fromString("function"),
          "function" -> Json.obj("arguments" -> Json.fromString(c.input.value), "name" -> Json.fromString(c.tool.value)),
          "id" -> Json.fromString(c.id.value)
        )
      }
      Json.fromFields(
        List("content" -> Json.fromString(t.value)) ++
          Option.when(toolCalls.nonEmpty)("tool_calls" -> Json.fromValues(toolCalls)) ++
          List("role" -> Json.fromString("assistant"))
      )
    case AiMessage.ToolResult(id, _, out) =>
      Json.obj(
        "content" -> Json.fromString(out.value),
        "tool_call_id" -> Json.fromString(id.value),
        "role" -> Json.fromString("tool")
      )
