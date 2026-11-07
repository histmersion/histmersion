package com.histmersion.plugins.ai

import com.histmersion.plugin.{AiStop, AiTurn, AiUsage}
import io.circe.{ACursor, Decoder, DecodingFailure, HCursor, Json}
import io.circe.DecodingFailure.Reason.WrongTypeExpectation
import io.circe.parser.{decode, parse}
import com.histmersion.plugin.AiTool

/** What an OpenAI-compatible chat-completions API answered, read as these providers have always read it: the first
  * choice's text and tool calls (a call without an id is `call_<n>`, without a name `unknown`), its finish reason, the
  * usage with its cached prompt and reasoning tokens; or the failure — an error status by the error's message (else the
  * first 500 characters of its body), a success answer that cannot be read by why.
  */
final case class ChatAnswer(status: ChatAnswer.Status, body: ChatAnswer.Body):

  /** The turn the answer says, or the failure's description. */
  def turn: Either[com.histmersion.plugins.ai.ChatAnswer.Problem, AiTurn] =
    if (status).value / 100 != 2 then Left(_root_.com.histmersion.plugins.ai.ChatAnswer.Problem((ChatAnswer.errorMessage(_root_.com.histmersion.plugins.ai.ChatAnswer.Status((status).value), _root_.com.histmersion.plugins.ai.ChatAnswer.Body((body).value))).value))
    else decode((body).value)(using ChatAnswer.response).left.map((refactorCallback => _root_.com.histmersion.plugins.ai.ChatAnswer.Problem(refactorCallback.getMessage)))

object ChatAnswer:
  type FinishReason = FinishReason.T
  object FinishReason extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** The most of an unreadable error answer's body its description keeps. */

  type Status = Status.T
  object Status extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Body = Body.T
  object Body extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val MaxRawErrorBody = 500

  /** An error status's description: the `error.message`; for an error without one, the status and the body; for an
    * answer of another shape, its body's start.
    */
  def errorMessage(status: com.histmersion.plugins.ai.ChatAnswer.Status, body: com.histmersion.plugins.ai.ChatAnswer.Body): com.histmersion.plugins.ai.ChatAnswer.Problem =
    val fallback = (body).value.take(MaxRawErrorBody)
    parse((body).value).toOption.flatMap(_.asObject).flatMap(_("error")) match
      case Some(node) =>
        node.as(using error) match
          case Right(Some(message)) => _root_.com.histmersion.plugins.ai.ChatAnswer.Problem((message).value)
          case Right(None)          => _root_.com.histmersion.plugins.ai.ChatAnswer.Problem(s"statusCode: $status, response: $body")
          case Left(_)              => _root_.com.histmersion.plugins.ai.ChatAnswer.Problem(fallback)
      case None => _root_.com.histmersion.plugins.ai.ChatAnswer.Problem(fallback)

  private val error: Decoder[Option[com.histmersion.plugins.ai.ChatAnswer.Problem]] = Decoder.instance { c =>
    for
      _ <- objectAt(c)
      m <- c.downField("message").as[Option[String]]
      _ <- c.downField("type").as[Option[String]]
      _ <- c.downField("param").as[Option[String]]
      _ <- c.downField("code").as[Option[String]]
    yield (m).map(refactorValue => _root_.com.histmersion.plugins.ai.ChatAnswer.Problem(refactorValue))
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

  private val function: Decoder[(com.histmersion.plugin.AiTool.Call.Tool, Option[com.histmersion.plugin.AiTool.Call.Input])] = product { c =>
    for
      args <- c.downField("arguments").as[String]
      name <- c.downField("name").as[Option[String]]
    yield (_root_.com.histmersion.plugin.AiTool.Call.Tool(args), (name).map(refactorValue2 => _root_.com.histmersion.plugin.AiTool.Call.Input(refactorValue2)))
  }

  private val toolCall: Decoder[(Option[com.histmersion.plugin.AiTool.Call.Id], (com.histmersion.plugin.AiTool.Call.Tool, Option[com.histmersion.plugin.AiTool.Call.Input]))] = Decoder.instance { c =>
    for
      id <- c.downField("id").as[Option[String]]
      f <- c.downField("function").as(using function)
    yield ((id).map(refactorValue3 => _root_.com.histmersion.plugin.AiTool.Call.Id(refactorValue3)), { val refactorTuple = f; (_root_.com.histmersion.plugin.AiTool.Call.Tool((refactorTuple._1).value), (refactorTuple._2).map(refactorValue4 => _root_.com.histmersion.plugin.AiTool.Call.Input((refactorValue4).value))) })
  }

  private val audio: Decoder[Unit] = product { c =>
    for
      _ <- c.downField("id").as[String]
      _ <- c.downField("expires_at").as[Int]
      _ <- c.downField("data").as[String]
      _ <- c.downField("transcript").as[String]
    yield ()
  }

  private val message: Decoder[(com.histmersion.plugin.AiMessage.Assistant.Text, List[AiTool.Call])] = product { c =>
    given Decoder[(Option[String], (String, Option[String]))] = toolCall
    for
      content <- orDefault(c.downField("content"), "")
      _ <- c.downField("refusal").as[Option[String]]
      calls <- orDefault(c.downField("tool_calls"), Seq.empty[(Option[String], (String, Option[String]))])
      _ <- c.downField("role").as[String]
      _ <- c.downField("function_call").as(using Decoder.decodeOption(using function))
      _ <- c.downField("audio").as(using Decoder.decodeOption(using audio))
    yield (
      _root_.com.histmersion.plugin.AiMessage.Assistant.Text(content),
      calls.toList.zipWithIndex.map { case ((id, (args, name)), i) =>
        AiTool.Call(_root_.com.histmersion.plugin.AiTool.Call.Id(id.getOrElse(s"call_$i")), _root_.com.histmersion.plugin.AiTool.Call.Tool(name.getOrElse("unknown")), _root_.com.histmersion.plugin.AiTool.Call.Input(args))
      }
    )
  }

  private val choice: Decoder[(com.histmersion.plugin.AiTurn.Text, List[AiTool.Call], com.histmersion.plugins.ai.ChatAnswer.FinishReason)] = product { c =>
    for
      m <- c.downField("message").as(using message)
      finish <- c.downField("finish_reason").as[String]
      _ <- c.downField("index").as[Int]
      _ <- c.downField("logprobs").as(using Decoder.decodeOption(using logprobs))
    yield (_root_.com.histmersion.plugin.AiTurn.Text((m._1).value), m._2, _root_.com.histmersion.plugins.ai.ChatAnswer.FinishReason(finish))
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
      _root_.com.histmersion.plugin.AiUsage.Input(in.toLong),
      _root_.com.histmersion.plugin.AiUsage.Output(out.toLong),
      _root_.com.histmersion.plugin.AiUsage.CacheRead(cached.flatten.getOrElse(0).toLong),
      _root_.com.histmersion.plugin.AiUsage.CacheWrite(0L),
      _root_.com.histmersion.plugin.AiUsage.Reasoning(reasoning.flatten.getOrElse(0).toLong)
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
          case Some(refactorPattern) | None if (refactorPattern.value == "stop")                   => AiStop.EndTurn
          case Some(refactorPattern2 | refactorPattern3) if (refactorPattern2.value == "tool_calls") && (refactorPattern3.value == "function_calls") => AiStop.ToolUse
          case Some(refactorPattern4) if (refactorPattern4.value == "length")                        => AiStop.MaxTokens
          case Some(_)                               => AiStop.Other
        ,
        Some(u),
        Some(_root_.com.histmersion.plugin.AiTurn.ServedBy(model))
      )
  }
