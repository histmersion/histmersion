package com.histmersion
package fixtures.helloai
import com.histmersion.util.problem.{External, Problem}

import com.histmersion.Newtype
import com.histmersion.plugin.*
import com.histmersion.plugin.Plugin.Config.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{LintRule, tolerated}

@tolerated(LintRule.G44, "a test plugin standing in for a third-party author: it builds and reads SPI values directly")
final class HelloAiProvider(replies: List[Reply], hasToken: HelloAiProvider.HasToken) extends AiProvider:
  val title = AiProvider.Title("Hello AI (scripted test provider)")
  override val description =
    AiProvider.Description("answers from its config; calls no model")
  override val capabilities =
    AiCapabilities(tools = AiCapabilities.Tools(true), structuredOutput = AiCapabilities.StructuredOutput(false))

  override def check(model: AiProvider.CheckModel): Either[Problem, Unit] =
    Either.cond(
      model.value != "broken",
      (),
      Problem.Plugin.Said(
        Problem.Plugin.Id("hello-ai"),
        Problem.Plugin.Claim.Unavailable,
        External.PluginSaid.of("the model 'broken' cannot be built (a test of the check)")
      )
    )

  override def models(http: AiHttp): Either[Problem, List[AiProvider.ModelName]] =
    Right(List(AiProvider.ModelName("echo"), AiProvider.ModelName("limited")))

  override def doctor(online: AiProvider.DoctorOnline, http: AiHttp): List[AiCheck] =
    List(
      AiCheck(
        AiCheck.Name("script"),
        if replies.isEmpty then AiCheck.Level("warn")
        else AiCheck.Level("ok"),
        AiCheck.Detail(s"${replies.size} scripted repl(ies)")
      ),
      AiCheck(
        AiCheck.Name("token"),
        AiCheck.Level("ok"),
        if (hasToken).value then AiCheck.Detail("given (never shown)")
        else AiCheck.Detail("none asked")
      )
    )

  def turn(request: AiTurn.Request, http: AiHttp): Either[Problem, AiTurn] =
    val usage = Some(
      AiUsage(
        input = AiUsage.Input((100).toLong),
        output = AiUsage.Output((20).toLong)
      )
    )
    if request.model == AiTurn.Request.Model("limited") then
      Left(
        Problem.Upstream.Limited(Problem.Upstream.Name("hello-ai"), None)
      )
    else
      val schema = request.answerSchema.fold("")(_.value)
      replies.find(r => schema.contains(r.when.value)) match
        case None =>
          Left(
            Problem.Plugin.Said(
              Problem.Plugin.Id("hello-ai"),
              Problem.Plugin.Claim.Failed,
              External.PluginSaid.of(s"no scripted reply for this step (${replies.size} repl(ies))")
            )
          )
        case Some(r) =>
          val answered = request.messages.exists(_.isInstanceOf[AiMessage.ToolResult])
          r.tool.filter(t => !answered && request.tools.exists(_.name.value == t.value)) match
            case Some(t) =>
              Right(
                AiTurn(
                  AiTurn.Text(""),
                  List(
                    AiTool.Call(
                      AiTool.Call.Id("hello-1"),
                      AiTool.Call.Tool(t.value),
                      AiTool.Call.Input(r.input.fold("{}")(_.value))
                    )
                  ),
                  AiStop.ToolUse,
                  usage,
                  Some(AiTurn.ServedBy(s"${request.model}-scripted"))
                )
              )
            case None =>
              Right(
                AiTurn(
                  AiTurn.Text(r.text.value),
                  Nil,
                  AiStop.EndTurn,
                  usage,
                  Some(AiTurn.ServedBy(s"${request.model}-scripted"))
                )
              )

object HelloAiProvider:
  type HasToken = HasToken.T
  object HasToken extends Newtype[Boolean, Pure]
