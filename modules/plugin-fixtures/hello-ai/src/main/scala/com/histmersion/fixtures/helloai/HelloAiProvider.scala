package com.histmersion.fixtures.helloai

import com.histmersion.plugin.*
import com.histmersion.plugin.Plugin.Config.given

final class HelloAiProvider(replies: List[Reply], hasToken: HelloAiProvider.HasToken) extends AiProvider:
  val title = _root_.com.histmersion.plugin.AiProvider.TitleResult("Hello AI (scripted test provider)")
  override val description = _root_.com.histmersion.plugin.AiProvider.DescriptionResult("answers from its config; calls no model")
  override val capabilities = AiCapabilities(tools = true, structuredOutput = false)

  override def check(model: String): Either[String, Unit] =
    Either.cond(model != "broken", (), "the model 'broken' cannot be built (a test of the check)")

  override def models(http: AiHttp): Either[String, List[String]] = Right(List("echo", "limited"))

  override def doctor(online: Boolean, http: AiHttp): List[AiCheck] =
    List(
      AiCheck(_root_.com.histmersion.plugin.AiCheck.Name("script"), if replies.isEmpty then _root_.com.histmersion.plugin.AiCheck.Level("warn") else _root_.com.histmersion.plugin.AiCheck.Level("ok"), _root_.com.histmersion.plugin.AiCheck.Detail(s"${replies.size} scripted repl(ies)")),
      AiCheck(_root_.com.histmersion.plugin.AiCheck.Name("token"), _root_.com.histmersion.plugin.AiCheck.Level("ok"), if (hasToken).value then _root_.com.histmersion.plugin.AiCheck.Detail("given (never shown)") else _root_.com.histmersion.plugin.AiCheck.Detail("none asked"))
    )

  def turn(request: AiTurn.Request, http: AiHttp): Either[AiFailure, AiTurn] =
    val usage = Some(AiUsage(input = _root_.com.histmersion.plugin.AiUsage.Input((100).toLong), output = _root_.com.histmersion.plugin.AiUsage.Output((20).toLong)))
    if request.model == "limited" then Left(AiFailure.LimitReached(_root_.com.histmersion.plugin.AiFailure.LimitReached.Message("hello-ai: usage limit reached")))
    else
      val schema = request.answerSchema.getOrElse("")
      replies.find(r => schema.contains(r.when)) match
        case None    => Left(AiFailure.Error(_root_.com.histmersion.plugin.AiFailure.Error.Message(s"no scripted reply for this step (${replies.size} repl(ies))")))
        case Some(r) =>
          val answered = request.messages.exists(_.isInstanceOf[AiMessage.ToolResult])
          r.tool.filter(t => !answered && request.tools.exists(_.name == t)) match
            case Some(t) =>
              Right(
                AiTurn(
                  _root_.com.histmersion.plugin.AiTurn.Text(""),
                  List(AiTool.Call(_root_.com.histmersion.plugin.AiTool.Call.Id("hello-1"), t, r.input.getOrElse("{}"))),
                  AiStop.ToolUse,
                  usage,
                  Some(_root_.com.histmersion.plugin.AiTurn.ServedBy(s"${request.model}-scripted"))
                )
              )
            case None => Right(AiTurn(r.text, Nil, AiStop.EndTurn, usage, Some(_root_.com.histmersion.plugin.AiTurn.ServedBy(s"${request.model}-scripted"))))

object HelloAiProvider:
  type HasToken = HasToken.T
  object HasToken extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
