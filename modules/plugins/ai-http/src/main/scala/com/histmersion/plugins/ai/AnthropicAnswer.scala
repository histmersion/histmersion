package com.histmersion.plugins.ai

import com.histmersion.plugin.{AiStop, AiTurn, AiUsage}
import io.circe.{ACursor, Decoder, DecodingFailure, HCursor, Json}
import io.circe.DecodingFailure.Reason.WrongTypeExpectation
import io.circe.parser.parse
import com.histmersion.plugin.AiTool

/** What the Anthropic Messages API answered, read as these providers have always read it: a message (its first text
  * block the answer's text, its `tool_use` blocks the tool calls, its usage with the cache reads and writes counted in
  * the input), or the failure — an error status's body as it came, an error in a success answer by its message, an
  * answer that is neither by why it cannot be read as an error.
  */
final case class AnthropicAnswer(status: AnthropicAnswer.Status, body: AnthropicAnswer.Body):

  /** The turn the answer says, or the failure's description. */
  def turn: Either[String, AiTurn] =
    if status / 100 != 2 then Left(body)
    else
      parse(body).flatMap(_.as(using AnthropicAnswer.message)) match
        case Right(turn) => Right(turn)
        case Left(_)     =>
          parse(body).flatMap(_.as(using AnthropicAnswer.error)) match
            case Right(message) => Left(message)
            case Left(e)        => Left(e.getMessage)

object AnthropicAnswer:

  type Status = Status.T
  object Status extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Body = Body.T
  object Body extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private def objectAt(c: HCursor): Either[DecodingFailure, Unit] =
    if c.value.isObject then Right(()) else Left(DecodingFailure(WrongTypeExpectation("object", c.value), c.history))

  /** An error answer's message: `{"error": {"type": …, "message": …}}`. */
  val error: Decoder[String] = Decoder.instance { c =>
    for
      _ <- objectAt(c)
      m <- c.downField("error").as(using errorDetail)
    yield m
  }

  private val errorDetail: Decoder[String] = Decoder.instance { c =>
    for
      _ <- objectAt(c)
      _ <- c.downField("type").as[String]
      m <- c.downField("message").as[String]
    yield m
  }

  /** One content block: its text, or its tool call, or neither (a thinking block, an image …); a block of an unknown
    * type is not a message.
    */
  private enum Block:
    case Text(text: String)
    case ToolUse(call: AiTool.Call)
    case Other

  private val Known = Set("image", "tool_result", "document", "web_search_tool_result")

  private val block: Decoder[Block] = Decoder.instance { c =>
    c.downField("type").as[Option[String]].flatMap {
      case Some("text")     => objectAt(c).flatMap(_ => c.downField("text").as[String]).map(Block.Text(_))
      case Some("thinking") => objectAt(c).flatMap(_ => c.downField("thinking").as[String]).map(_ => Block.Other)
      case Some(t @ ("tool_use" | "server_tool_use")) =>
        for
          _ <- objectAt(c)
          id <- c.downField("id").as[String]
          name <- c.downField("name").as[String]
          input <- c.downField("input").as[Map[String, Json]]
        yield
          if t == "tool_use" then Block.ToolUse(AiTool.Call(id, name, Json.fromFields(input).noSpaces)) else Block.Other
      case Some(t) if Known(t) => Right(Block.Other)
      case other               => Left(DecodingFailure(s"no content block of type $other", c.history))
    }
  }

  private def optionalInt(c: ACursor): Either[DecodingFailure, Option[Int]] = c.as[Option[Int]]

  private val usage: Decoder[AiUsage] = Decoder.instance { c =>
    for
      _ <- objectAt(c)
      in <- c.downField("input_tokens").as[Int]
      out <- c.downField("output_tokens").as[Int]
      read <- optionalInt(c.downField("cache_read_input_tokens"))
      write <- optionalInt(c.downField("cache_creation_input_tokens"))
    yield
      val (r, w) = (read.getOrElse(0), write.getOrElse(0))
      AiUsage((in + r + w).toLong, out.toLong, r.toLong, w.toLong)
  }

  /** A message answer as the turn it says. */
  val message: Decoder[AiTurn] = Decoder.instance { c =>
    for
      _ <- objectAt(c)
      _ <- c.downField("id").as[String]
      _ <- c.downField("type").as[String]
      _ <- c.downField("role").as[String]
      blocks <- c.downField("content").as(using Decoder.decodeList(using block))
      model <- c.downField("model").as[String]
      stop <- c.downField("stop_reason").as[Option[String]]
      _ <- c.downField("stop_sequence").as[Option[String]]
      u <- c.downField("usage").as(using usage)
    yield AiTurn(
      blocks.collectFirst { case Block.Text(t) => t }.getOrElse(""),
      blocks.collect { case Block.ToolUse(call) => call },
      stop match
        case Some("end_turn") | None => AiStop.EndTurn
        case Some("tool_use")        => AiStop.ToolUse
        case Some("max_tokens")      => AiStop.MaxTokens
        case Some(_)                 => AiStop.Other
      ,
      Some(u),
      Some(model)
    )
  }
