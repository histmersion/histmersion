package com.histmersion.fixtures.helloai

import com.histmersion.plugin.*
import com.histmersion.plugin.Plugin.Config.given

final class HelloAiPlugin extends Plugin:
  type Config = HelloAiConfig
  val info = Plugin.Info("hello-ai", "0.1.0", Spi.Version)

  def register(config: HelloAiConfig, ctx: Plugin.Context): Either[com.histmersion.fixtures.helloai.HelloAiPlugin.RegisterResultPart1, Unit] =
    config.token.fold[Either[String, Boolean]](Right(false))(t => ctx.secret(t).map(_.nonEmpty)).flatMap { hasToken =>
      ctx.log.info(s"hello-ai: ${config.replies.size} scripted repl(ies)${if hasToken then ", a token given" else ""}")
      ctx.aiProviders.add(config.name, HelloAiProvider(config.replies, hasToken))
    }

object HelloAiPlugin:
  type RegisterResultPart1 = RegisterResultPart1.T
  object RegisterResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
