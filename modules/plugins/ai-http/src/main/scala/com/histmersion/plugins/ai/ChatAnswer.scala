package com.histmersion
package plugins.ai
import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.histmersion.util.json.RawJson
import com.histmersion.util.json.RawJson.*
import com.histmersion.util.json.JsonCodec.given
import com.histmersion.util.json.Cursor
import com.histmersion.util.problem.{External, Problem}

import com.histmersion.plugin.{AiStop, AiTurn, AiUsage}
import com.histmersion.plugin.AiTool
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.plugin.AiMessage

/** What an OpenAI-compatible chat-completions API answered, read as these providers have always read it: the first
  * choice's text and tool calls (a call without an id is `call_<n>`, without a name `unknown`), its finish reason, the
  * usage with its cached prompt and reasoning tokens; or the failure — an error status answered by `provider` at `url`
  * with the error's message (else the start of its body), a success answer that cannot be read by why.
  */
final case class ChatAnswer(
    provider: Problem.Upstream.Name,
    url: Problem.Upstream.Url,
    status: ChatAnswer.Status,
    body: ChatAnswer.Body
):

  /** The turn the answer says, or the failure. */
  def turn: Either[Problem, AiTurn] =
    if (status).value / 100 != 2 then
      Left(
        Problem.Upstream.Http.here(
          provider,
          url,
          Problem.Upstream.Status((status).value),
          External.ReplyExcerpt.head(ChatAnswer.errorMessage(ChatAnswer.Status((status).value), body))
        )
      )
    else
      RawJson
        .parse((body).value)
        .flatMap(ChatAnswer.response(_))
        .left
        .map(why =>
          Problem.Upstream.BadReply.here(provider, url, Problem.Upstream.Shape.Json, External.Reported.of(why))
        )

object ChatAnswer:
  /** Why the provider stopped the first choice: the `finish_reason` values its API documents, and any other as it came.
    */
  type FinishText = FinishText.T
  object FinishText extends Newtype[String, Pure]

  enum FinishReason derives CanEqual:
    case Stop, Length, ToolCalls, ContentFilter, FunctionCall
    case Other(raw: FinishText)

    /** The SPI's reading of it. */
    def stop: AiStop = this match
      case Stop                                    => AiStop.EndTurn
      case ToolCalls                               => AiStop.ToolUse
      case Length                                  => AiStop.MaxTokens
      case ContentFilter | FunctionCall | Other(_) => AiStop.Other

  object FinishReason:

    /** The reason a provider's answer states (`function_calls` is read as `tool_calls`, as it always has been). */
    def parse(raw: FinishText): FinishReason = raw.value match
      case "stop"                          => Stop
      case "length"                        => Length
      case "tool_calls" | "function_calls" => ToolCalls
      case "content_filter"                => ContentFilter
      case "function_call"                 => FunctionCall
      case _                               => Other(raw)

  /** The most of an unreadable error answer's body its description keeps. */

  type Status = Status.T
  object Status extends Newtype[Int, Pure]
  type Body = Body.T
  object Body extends Newtype[String, Pure]

  val MaxRawErrorBody = 500

  /** An error status's description: the `error.message`; for an error without one, the status and the body; for an
    * answer of another shape, its body's start.
    */
  def errorMessage(status: Status, body: Body): String =
    val fallback = (body).value.take(MaxRawErrorBody)
    RawJson.parse((body).value).toOption.flatMap(_.asObject).flatMap(_("error")) match
      case Some(node) =>
        error(node) match
          case Right(Some(message)) => message
          case Right(None)          => s"statusCode: $status, response: $body"
          case Left(_)              => fallback
      case None => fallback

  private val error: Cursor.Read[Option[String]] = Cursor.Read { c =>
    for
      _ <- c.anObject
      m <- c.downField("message").as[Option[String]]
      _ <- c.downField("type").as[Option[String]]
      _ <- c.downField("param").as[Option[String]]
      _ <- c.downField("code").as[Option[String]]
    yield m
  }

  /** A field with a default: missing or null is the default, anything else must decode. */
  private def orDefault[A](c: Cursor, default: A)(d: Cursor.Read[A]): Either[String, A] =
    c.read(d) match
      case r @ Right(_)                                            => r
      case l @ Left(_) if c.succeeded && !c.focus.exists(_.isNull) => l
      case _                                                       => Right(default)

  private def product[A](f: Cursor => Either[String, A]): Cursor.Read[A] =
    Cursor.Read(c => c.anObject.flatMap(_ => f(c)))

  private def as[A](using codec: JsonValueCodec[A]): Cursor.Read[A] = Cursor.Read(_.as[A])

  private val topLogprob: Cursor.Read[Unit] = product { c =>
    for
      _ <- c.downField("token").as[String]
      _ <- c.downField("logprob").as[Float]
      _ <- c.downField("bytes").as[Option[Seq[Int]]]
    yield ()
  }

  private val logprobData: Cursor.Read[Unit] = product { c =>
    for
      _ <- c.downField("token").as[String]
      _ <- c.downField("logprob").as[Float]
      _ <- c.downField("bytes").as[Option[Seq[Int]]]
      _ <- c.downField("top_logprobs").read(Cursor.Read.list(topLogprob))
    yield ()
  }

  private val logprobs: Cursor.Read[Unit] = product { c =>
    for
      _ <- c.downField("content").read(Cursor.Read.option(Cursor.Read.list(logprobData)))
      _ <- c.downField("refusal").read(Cursor.Read.option(Cursor.Read.list(logprobData)))
    yield ()
  }

  private val function: Cursor.Read[(AiTool.Call.Input, Option[AiTool.Call.Tool])] = product { c =>
    for
      args <- c.downField("arguments").as[String]
      name <- c.downField("name").as[Option[String]]
    yield (AiTool.Call.Input(args), name.map(AiTool.Call.Tool(_)))
  }

  private val toolCall: Cursor.Read[(Option[AiTool.Call.Id], (AiTool.Call.Input, Option[AiTool.Call.Tool]))] =
    Cursor.Read { c =>
      for
        id <- c.downField("id").as[Option[String]]
        f <- c.downField("function").read(function)
      yield (id.map(AiTool.Call.Id(_)), f)
    }

  private val audio: Cursor.Read[Unit] = product { c =>
    for
      _ <- c.downField("id").as[String]
      _ <- c.downField("expires_at").as[Int]
      _ <- c.downField("data").as[String]
      _ <- c.downField("transcript").as[String]
    yield ()
  }

  private val message: Cursor.Read[(AiMessage.Assistant.Text, List[AiTool.Call])] = product { c =>
    for
      content <- orDefault(c.downField("content"), "")(as[String])
      _ <- c.downField("refusal").as[Option[String]]
      calls <- orDefault(
        c.downField("tool_calls"),
        List.empty[(Option[AiTool.Call.Id], (AiTool.Call.Input, Option[AiTool.Call.Tool]))]
      )(Cursor.Read.list(toolCall))
      _ <- c.downField("role").as[String]
      _ <- c.downField("function_call").read(Cursor.Read.option(function))
      _ <- c.downField("audio").read(Cursor.Read.option(audio))
    yield (
      AiMessage.Assistant.Text(content),
      calls.toList.zipWithIndex.map { case ((id, (args, name)), i) =>
        AiTool.Call(id.getOrElse(AiTool.Call.Id(s"call_$i")), name.getOrElse(AiTool.Call.Tool("unknown")), args)
      }
    )
  }

  private val choice: Cursor.Read[(AiTurn.Text, List[AiTool.Call], FinishReason)] = product { c =>
    for
      m <- c.downField("message").read(message)
      finish <- c.downField("finish_reason").as[String]
      _ <- c.downField("index").as[Int]
      _ <- c.downField("logprobs").read(Cursor.Read.option(logprobs))
    yield (AiTurn.Text((m._1).value), m._2, FinishReason.parse(FinishText(finish)))
  }

  private val usage: Cursor.Read[AiUsage] = product { c =>
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
      reasoning <- c.downField("completion_tokens_details").read(Cursor.Read.option(completion))
      cached <- c.downField("prompt_tokens_details").read(Cursor.Read.option(prompt))
    yield AiUsage(
      AiUsage.Input(in.toLong),
      AiUsage.Output(out.toLong),
      AiUsage.CacheRead(cached.flatten.getOrElse(0).toLong),
      AiUsage.CacheWrite(0L),
      AiUsage.Reasoning(reasoning.flatten.getOrElse(0).toLong)
    )
  }

  /** A success answer as the turn its first choice says. */
  val response: Cursor.Read[AiTurn] = product { c =>
    for
      _ <- c.downField("id").as[String]
      choices <- c.downField("choices").read(Cursor.Read.list(choice))
      _ <- c.downField("created").as[Int]
      model <- c.downField("model").as[String]
      _ <- c.downField("object").as[String]
      u <- c.downField("usage").read(usage)
      _ <- c.downField("system_fingerprint").as[Option[String]]
      _ <- c.downField("service_tier").as[Option[String]]
    yield
      val first = choices.headOption
      AiTurn(
        first.map(_._1).getOrElse(AiTurn.Text("")),
        first.map(_._2).getOrElse(Nil),
        first.fold(AiStop.EndTurn)(_._3.stop),
        Some(u),
        Some(AiTurn.ServedBy(model))
      )
  }
