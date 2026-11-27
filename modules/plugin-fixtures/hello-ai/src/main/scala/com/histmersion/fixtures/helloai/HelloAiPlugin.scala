package com.histmersion
package fixtures.helloai
import com.histmersion.util.problem.{Problem, Problems}

import com.histmersion.plugin.*
import com.histmersion.plugin.Plugin.Config.given
import com.histmersion.lint.{LintRule, tolerated}

@tolerated(LintRule.G44, "a test plugin standing in for a third-party author: it builds and reads SPI values directly")
final class HelloAiPlugin extends Plugin:
  type Config = HelloAiConfig
  val info = Plugin.Info(
    Plugin.Info.Id("hello-ai"),
    Plugin.Info.Version("0.1.0"),
    Plugin.Info.SpiVersion(Spi.Version)
  )

  def register(config: HelloAiConfig, ctx: Plugin.Context): Either[Problems, Unit] =
    config.token
      .fold[Either[Problems, Boolean]](Right(false))(t => ctx.secret(t).map(_.value.nonEmpty))
      .flatMap { hasToken =>
        ctx.log.info(
          Plugin.Log.Line(
            s"hello-ai: ${config.replies.size} scripted repl(ies)${if hasToken then ", a token given" else ""}"
          )
        )
        ctx.aiProviders
          .add(Registry.AddId(config.name.value), HelloAiProvider(config.replies, HelloAiProvider.HasToken(hasToken)))
      }
