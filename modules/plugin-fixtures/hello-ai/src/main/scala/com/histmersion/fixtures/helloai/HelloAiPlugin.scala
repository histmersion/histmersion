package com.histmersion.fixtures.helloai

import com.histmersion.plugin.*
import com.histmersion.plugin.Plugin.Config.given

final class HelloAiPlugin extends Plugin:
  type Config = HelloAiConfig
  val info = Plugin.Info(_root_.com.histmersion.plugin.Plugin.Info.Id("hello-ai"), _root_.com.histmersion.plugin.Plugin.Info.Version("0.1.0"), _root_.com.histmersion.plugin.Plugin.Info.SpiVersion(Spi.Version))

  def register(config: HelloAiConfig, ctx: Plugin.Context): Either[com.histmersion.fixtures.helloai.HelloAiPlugin.RegisterResultPart1, Unit] =
    config.token.fold[Either[String, Boolean]](Right(false))(t => ctx.secret(t).map(_.nonEmpty)).flatMap { hasToken =>
      ctx.log.info(_root_.com.histmersion.plugin.Plugin.Log.InfoMessage(s"hello-ai: ${config.replies.size} scripted repl(ies)${if hasToken then ", a token given" else ""}"))
      ctx.aiProviders.add(config.name, HelloAiProvider(config.replies, _root_.com.histmersion.fixtures.helloai.HelloAiProvider.HasToken(hasToken)))
    }

object HelloAiPlugin:
  type RegisterResultPart1 = RegisterResultPart1.T
  object RegisterResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
