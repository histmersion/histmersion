package com.histmersion.fixtures.helloai

import com.histmersion.plugin.*
import com.histmersion.plugin.Plugin.Config.given

final class HelloAiProvider(replies: List[Reply], hasToken: HelloAiProvider.HasToken) extends AiProvider:
  val title = "Hello AI (scripted test provider)"
  override val description = "answers from its config; calls no model"
  override val capabilities = AiCapabilities(tools = true, structuredOutput = false)

  override def check(model: String): Either[String, Unit] =
    Either.cond(model != "broken", (), "the model 'broken' cannot be built (a test of the check)")

  override def models(http: AiHttp): Either[String, List[String]] = Right(List("echo", "limited"))

  override def doctor(online: Boolean, http: AiHttp): List[AiCheck] =
    List(
      AiCheck("script", if replies.isEmpty then "warn" else "ok", s"${replies.size} scripted repl(ies)"),
      AiCheck("token", "ok", if hasToken then "given (never shown)" else "none asked")
    )

  def turn(request: AiTurn.Request, http: AiHttp): Either[AiFailure, AiTurn] =
    val usage = Some(AiUsage(input = 100, output = 20))
    if request.model == "limited" then Left(AiFailure.LimitReached("hello-ai: usage limit reached"))
    else
      val schema = request.answerSchema.getOrElse("")
      replies.find(r => schema.contains(r.when)) match
        case None    => Left(AiFailure.Error(s"no scripted reply for this step (${replies.size} repl(ies))"))
        case Some(r) =>
          val answered = request.messages.exists(_.isInstanceOf[AiMessage.ToolResult])
          r.tool.filter(t => !answered && request.tools.exists(_.name == t)) match
            case Some(t) =>
              Right(
                AiTurn(
                  "",
                  List(AiTool.Call("hello-1", t, r.input.getOrElse("{}"))),
                  AiStop.ToolUse,
                  usage,
                  Some(s"${request.model}-scripted")
                )
              )
            case None => Right(AiTurn(r.text, Nil, AiStop.EndTurn, usage, Some(s"${request.model}-scripted")))

object HelloAiProvider:
  type HasToken = HasToken.T
  object HasToken extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
