package com.histmersion.fixtures.helloai

import com.histmersion.plugin.Problem
import com.histmersion.plugin.*
import com.histmersion.plugin.Plugin.Config.given

final class HelloAiPlugin extends Plugin:
  type Config = HelloAiConfig
  val info = Plugin.Info(
    _root_.com.histmersion.plugin.Plugin.Info.Id("hello-ai"),
    _root_.com.histmersion.plugin.Plugin.Info.Version("0.1.0"),
    _root_.com.histmersion.plugin.Plugin.Info.SpiVersion(Spi.Version)
  )

  def register(config: HelloAiConfig, ctx: Plugin.Context): Either[Problem, Unit] =
    config.token
      .fold[Either[Problem, Boolean]](Right(false))(t => ctx.secret(t).map(_.value.nonEmpty))
      .flatMap { hasToken =>
        ctx.log.info(
          _root_.com.histmersion.plugin.Plugin.Log.Line(
            s"hello-ai: ${config.replies.size} scripted repl(ies)${if hasToken then ", a token given" else ""}"
          )
        )
        ctx.aiProviders
          .add(Registry.AddId(config.name.value), HelloAiProvider(config.replies, HelloAiProvider.HasToken(hasToken)))
      }
