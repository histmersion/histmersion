package com.histmersion
package plugins.ai
import com.histmersion.util.json.RawJson
import com.histmersion.util.json.RawJson.*
import com.histmersion.util.json.JsonCodec.given
import com.histmersion.util.json.Cursor
import hearth.kindlings.jsoniterjson.Json
import com.histmersion.util.problem.{External, Problem}

import com.histmersion.plugin.{AiStop, AiTurn, AiUsage}
import com.histmersion.plugin.AiTool
import io.github.iltotore.iron.constraint.any.Pure

/** What the Anthropic Messages API answered, read as these providers have always read it: a message (its first text
  * block the answer's text, its `tool_use` blocks the tool calls, its usage with the cache reads and writes counted in
  * the input), or the failure — an error status's body as it came, an error in a success answer by its message, an
  * answer that is neither by why it cannot be read as an error.
  */
final case class AnthropicAnswer(
    provider: Problem.Upstream.Name,
    url: Problem.Upstream.Url,
    status: AnthropicAnswer.Status,
    body: AnthropicAnswer.Body
):

  /** The turn the answer says, or the failure: `provider`'s answer at `url`, its words the start of the body. */
  def turn: Either[Problem, AiTurn] =
    def answered(said: String) =
      Problem.Upstream.Http.here(
        provider,
        url,
        Problem.Upstream.Status((status).value),
        External.ReplyExcerpt.head(said)
      )
    if (status).value / 100 != 2 then Left(answered((body).value))
    else
      RawJson.parse((body).value).flatMap(AnthropicAnswer.message(_)) match
        case Right(turn) => Right(turn)
        case Left(_)     =>
          RawJson.parse((body).value).flatMap(AnthropicAnswer.error(_)) match
            case Right(message) => Left(answered(message))
            case Left(e)        =>
              Left(Problem.Upstream.BadReply.here(provider, url, Problem.Upstream.Shape.Json, External.Reported.of(e)))

object AnthropicAnswer:

  type Status = Status.T
  object Status extends Newtype[Int, Pure]
  type Body = Body.T
  object Body extends Newtype[String, Pure]

  /** An error answer's message: `{"error": {"type": …, "message": …}}`. */
  val error: Cursor.Read[String] = Cursor.Read { c =>
    for
      _ <- c.anObject
      m <- c.downField("error").read(errorDetail)
    yield m
  }

  private val errorDetail: Cursor.Read[String] = Cursor.Read { c =>
    for
      _ <- c.anObject
      _ <- c.downField("type").as[String]
      m <- c.downField("message").as[String]
    yield m
  }

  /** One content block: its text, or its tool call, or neither (a thinking block, an image …); a block of an unknown
    * type is not a message.
    */
  private enum Block:
    case Text(text: AiTurn.Text)
    case ToolUse(call: AiTool.Call)
    case Other

  private val Known = Set("image", "tool_result", "document", "web_search_tool_result")

  private val block: Cursor.Read[Block] = Cursor.Read { c =>
    c.downField("type").as[Option[String]].flatMap {
      case Some("text") => c.anObject.flatMap(_ => c.downField("text").as[String]).map(s => Block.Text(AiTurn.Text(s)))
      case Some("thinking") => c.anObject.flatMap(_ => c.downField("thinking").as[String]).map(_ => Block.Other)
      case Some(t @ ("tool_use" | "server_tool_use")) =>
        for
          _ <- c.anObject
          id <- c.downField("id").as[String]
          name <- c.downField("name").as[String]
          input <- c.downField("input").as[Map[String, Json]]
        yield
          if t == "tool_use" then
            Block.ToolUse(
              AiTool
                .Call(AiTool.Call.Id(id), AiTool.Call.Tool(name), AiTool.Call.Input(Json.fromFields(input).noSpaces))
            )
          else Block.Other
      case Some(t) if Known(t) => Right(Block.Other)
      case other               => Left(s"no content block of type $other")
    }
  }

  private def optionalInt(c: Cursor): Either[String, Option[Int]] = c.as[Option[Int]]

  private val usage: Cursor.Read[AiUsage] = Cursor.Read { c =>
    for
      _ <- c.anObject
      in <- c.downField("input_tokens").as[Int]
      out <- c.downField("output_tokens").as[Int]
      read <- optionalInt(c.downField("cache_read_input_tokens"))
      write <- optionalInt(c.downField("cache_creation_input_tokens"))
    yield
      val (r, w) = (read.getOrElse(0), write.getOrElse(0))
      AiUsage(
        AiUsage.Input((in + r + w).toLong),
        AiUsage.Output(out.toLong),
        AiUsage.CacheRead(r.toLong),
        AiUsage.CacheWrite(w.toLong)
      )
  }

  /** A message answer as the turn it says. */
  val message: Cursor.Read[AiTurn] = Cursor.Read { c =>
    for
      _ <- c.anObject
      _ <- c.downField("id").as[String]
      _ <- c.downField("type").as[String]
      _ <- c.downField("role").as[String]
      blocks <- c.downField("content").read(Cursor.Read.list(block))
      model <- c.downField("model").as[String]
      stop <- c.downField("stop_reason").as[Option[String]]
      _ <- c.downField("stop_sequence").as[Option[String]]
      u <- c.downField("usage").read(usage)
    yield AiTurn(
      blocks.collectFirst { case Block.Text(t) => t }.getOrElse(AiTurn.Text("")),
      blocks.collect { case Block.ToolUse(call) => call },
      stop match
        case Some("end_turn") | None => AiStop.EndTurn
        case Some("tool_use")        => AiStop.ToolUse
        case Some("max_tokens")      => AiStop.MaxTokens
        case Some(_)                 => AiStop.Other
      ,
      Some(u),
      Some(AiTurn.ServedBy(model))
    )
  }
