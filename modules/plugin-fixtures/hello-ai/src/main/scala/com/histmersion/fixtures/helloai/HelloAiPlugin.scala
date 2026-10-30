package com.histmersion.fixtures.helloai

import com.histmersion.plugin.*
import com.histmersion.plugin.PluginConfig.given

final class HelloAiPlugin extends Plugin:
  type Config = HelloAiConfig
  val info = PluginInfo("hello-ai", "0.1.0", Spi.Version)

  def register(config: HelloAiConfig, ctx: PluginContext): Either[String, Unit] =
    config.token.fold[Either[String, Boolean]](Right(false))(t => ctx.secret(t).map(_.nonEmpty)).flatMap { hasToken =>
      ctx.log.info(s"hello-ai: ${config.replies.size} scripted repl(ies)${if hasToken then ", a token given" else ""}")
      ctx.aiProviders.add(config.name, HelloAiProvider(config.replies, hasToken))
    }
