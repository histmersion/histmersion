package com.histmersion.plugins.ai

import com.histmersion.plugin.{AiStop, AiToolCall, AiTurn, AiUsage}
import io.circe.{ACursor, Decoder, DecodingFailure, HCursor, Json}
import io.circe.DecodingFailure.Reason.WrongTypeExpectation
import io.circe.parser.{decode, parse}

/** What an OpenAI-compatible chat-completions API answered, read as these providers have always read it: the first
  * choice's text and tool calls (a call without an id is `call_<n>`, without a name `unknown`), its finish reason, the
  * usage with its cached prompt and reasoning tokens; or the failure — an error status by the error's message (else the
  * first 500 characters of its body), a success answer that cannot be read by why.
  */
final case class ChatAnswer(status: Int, body: String):

  /** The turn the answer says, or the failure's description. */
  def turn: Either[String, AiTurn] =
    if status / 100 != 2 then Left(ChatAnswer.errorMessage(status, body))
    else decode(body)(using ChatAnswer.response).left.map(_.getMessage)

object ChatAnswer:

  /** The most of an unreadable error answer's body its description keeps. */
  val MaxRawErrorBody = 500

  /** An error status's description: the `error.message`; for an error without one, the status and the body; for an
    * answer of another shape, its body's start.
    */
  def errorMessage(status: Int, body: String): String =
    val fallback = body.take(MaxRawErrorBody)
    parse(body).toOption.flatMap(_.asObject).flatMap(_("error")) match
      case Some(node) =>
        node.as(using error) match
          case Right(Some(message)) => message
          case Right(None)          => s"statusCode: $status, response: $body"
          case Left(_)              => fallback
      case None => fallback

  private val error: Decoder[Option[String]] = Decoder.instance { c =>
    for
      _ <- objectAt(c)
      m <- c.downField("message").as[Option[String]]
      _ <- c.downField("type").as[Option[String]]
      _ <- c.downField("param").as[Option[String]]
      _ <- c.downField("code").as[Option[String]]
    yield m
  }

  private def objectAt(c: HCursor): Either[DecodingFailure, Unit] =
    if c.value.isObject then Right(()) else Left(DecodingFailure(WrongTypeExpectation("object", c.value), c.history))

  /** A field with a default: missing or null is the default, anything else must decode. */
  private def orDefault[A](c: ACursor, default: A)(using d: Decoder[A]): Either[DecodingFailure, A] =
    d.tryDecode(c) match
      case r @ Right(_)                                            => r
      case l @ Left(_) if c.succeeded && !c.focus.exists(_.isNull) => l
      case _                                                       => Right(default)

  private def product[A](f: HCursor => Either[DecodingFailure, A]): Decoder[A] =
    Decoder.instance(c => objectAt(c).flatMap(_ => f(c)))

  private val topLogprob: Decoder[Unit] = product { c =>
    for
      _ <- c.downField("token").as[String]
      _ <- c.downField("logprob").as[Float]
      _ <- c.downField("bytes").as[Option[Seq[Int]]]
    yield ()
  }

  private val logprobData: Decoder[Unit] = product { c =>
    for
      _ <- c.downField("token").as[String]
      _ <- c.downField("logprob").as[Float]
      _ <- c.downField("bytes").as[Option[Seq[Int]]]
      _ <- c.downField("top_logprobs").as(using Decoder.decodeSeq(using topLogprob))
    yield ()
  }

  private val logprobs: Decoder[Unit] = product { c =>
    given Decoder[Unit] = logprobData
    for
      _ <- c.downField("content").as[Option[Seq[Unit]]]
      _ <- c.downField("refusal").as[Option[Seq[Unit]]]
    yield ()
  }

  private val function: Decoder[(String, Option[String])] = product { c =>
    for
      args <- c.downField("arguments").as[String]
      name <- c.downField("name").as[Option[String]]
    yield (args, name)
  }

  private val toolCall: Decoder[(Option[String], (String, Option[String]))] = Decoder.instance { c =>
    for
      id <- c.downField("id").as[Option[String]]
      f <- c.downField("function").as(using function)
    yield (id, f)
  }

  private val audio: Decoder[Unit] = product { c =>
    for
      _ <- c.downField("id").as[String]
      _ <- c.downField("expires_at").as[Int]
      _ <- c.downField("data").as[String]
      _ <- c.downField("transcript").as[String]
    yield ()
  }

  private val message: Decoder[(String, List[AiToolCall])] = product { c =>
    given Decoder[(Option[String], (String, Option[String]))] = toolCall
    for
      content <- orDefault(c.downField("content"), "")
      _ <- c.downField("refusal").as[Option[String]]
      calls <- orDefault(c.downField("tool_calls"), Seq.empty[(Option[String], (String, Option[String]))])
      _ <- c.downField("role").as[String]
      _ <- c.downField("function_call").as(using Decoder.decodeOption(using function))
      _ <- c.downField("audio").as(using Decoder.decodeOption(using audio))
    yield (
      content,
      calls.toList.zipWithIndex.map { case ((id, (args, name)), i) =>
        AiToolCall(id.getOrElse(s"call_$i"), name.getOrElse("unknown"), args)
      }
    )
  }

  private val choice: Decoder[(String, List[AiToolCall], String)] = product { c =>
    for
      m <- c.downField("message").as(using message)
      finish <- c.downField("finish_reason").as[String]
      _ <- c.downField("index").as[Int]
      _ <- c.downField("logprobs").as(using Decoder.decodeOption(using logprobs))
    yield (m._1, m._2, finish)
  }

  private val usage: Decoder[AiUsage] = product { c =>
    val completion = product { d =>
      for
        _ <- d.downField("accepted_prediction_tokens").as[Option[Int]]
        _ <- d.downField("audio_tokens").as[Option[Int]]
        r <- d.downField("reasoning_tokens").as[Option[Int]]
        _ <- d.downField("rejected_prediction_tokens").as[Option[Int]]
      yield r
    }
    val prompt = product { d =>
      for
        _ <- d.downField("audio_tokens").as[Option[Int]]
        cached <- d.downField("cached_tokens").as[Option[Int]]
      yield cached
    }
    for
      in <- c.downField("prompt_tokens").as[Int]
      out <- c.downField("completion_tokens").as[Int]
      _ <- c.downField("total_tokens").as[Int]
      reasoning <- c.downField("completion_tokens_details").as(using Decoder.decodeOption(using completion))
      cached <- c.downField("prompt_tokens_details").as(using Decoder.decodeOption(using prompt))
    yield AiUsage(
      in.toLong,
      out.toLong,
      cached.flatten.getOrElse(0).toLong,
      0L,
      reasoning.flatten.getOrElse(0).toLong
    )
  }

  /** A success answer as the turn its first choice says. */
  val response: Decoder[AiTurn] = product { c =>
    for
      _ <- c.downField("id").as[String]
      choices <- c.downField("choices").as(using Decoder.decodeSeq(using choice))
      _ <- c.downField("created").as[Int]
      model <- c.downField("model").as[String]
      _ <- c.downField("object").as[String]
      u <- c.downField("usage").as(using usage)
      _ <- c.downField("system_fingerprint").as[Option[String]]
      _ <- c.downField("service_tier").as[Option[String]]
    yield
      val first = choices.headOption
      AiTurn(
        first.map(_._1).getOrElse(""),
        first.map(_._2).getOrElse(Nil),
        first.map(_._3) match
          case Some("stop") | None                   => AiStop.EndTurn
          case Some("tool_calls" | "function_calls") => AiStop.ToolUse
          case Some("length")                        => AiStop.MaxTokens
          case Some(_)                               => AiStop.Other
        ,
        Some(u),
        Some(model)
      )
  }
