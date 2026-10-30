package com.histmersion.fixtures.helloai

import com.histmersion.plugin.*
import com.histmersion.plugin.PluginConfig.given
import hearth.kindlings.sconfigderivation.ConfigReader

/** A TEST FIXTURE, not a product: the smallest third-party AI provider, compiled against plugin-api alone and packaged
  * as its own jar (docs/research-plugins.md §22–§23). It calls no model and opens no socket: its answers are SCRIPTED
  * in its config, so the suites can run a real pipeline through the host's tool loop with it.
  *
  * Each turn answers with the first reply whose `when` occurs in the step's answer schema. A reply with a `tool` calls
  * that tool once first (when the step offers it), then answers. The model `limited` reports its plan's usage limit, so
  * a chain can be seen to move on.
  */
final case class Reply(when: String, text: String, tool: Option[String] = None, input: Option[String] = None)
    derives ConfigReader

/** `name`: the provider name it registers (`hello-ai`); `token`: an optional secret it is given (a reference, resolved
  * by the host — the provider only learns whether it got one).
  */
final case class HelloAiConfig(name: String, token: Option[Secret], replies: List[Reply]) derives ConfigReader

final class HelloAiPlugin extends Plugin:
  type Config = HelloAiConfig
  val info = PluginInfo("hello-ai", "0.1.0", Spi.Version)

  def register(config: HelloAiConfig, ctx: PluginContext): Either[String, Unit] =
    config.token.fold[Either[String, Boolean]](Right(false))(t => ctx.secret(t).map(_.nonEmpty)).flatMap { hasToken =>
      ctx.log.info(s"hello-ai: ${config.replies.size} scripted repl(ies)${if hasToken then ", a token given" else ""}")
      ctx.aiProviders.add(config.name, HelloAiProvider(config.replies, hasToken))
    }

final class HelloAiProvider(replies: List[Reply], hasToken: Boolean) extends AiProvider:
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

  def turn(request: AiTurnRequest, http: AiHttp): Either[AiFailure, AiTurn] =
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
                  List(AiToolCall("hello-1", t, r.input.getOrElse("{}"))),
                  AiStop.ToolUse,
                  usage,
                  Some(s"${request.model}-scripted")
                )
              )
            case None => Right(AiTurn(r.text, Nil, AiStop.EndTurn, usage, Some(s"${request.model}-scripted")))
