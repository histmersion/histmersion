package com.histmersion
package fixtures.hellodeps
import com.histmersion.util.problem.{Problem, Problems}

import com.histmersion.fixtures.hellolib.Shout
import com.histmersion.plugin.*
import com.histmersion.plugin.Plugin.Config.given
import com.histmersion.lint.{LintRule, tolerated}

@tolerated(LintRule.G44, "a test plugin standing in for a third-party author: it builds and reads SPI values directly")
final class HelloDepsPlugin extends Plugin:
  type Config = HelloDepsConfig
  val info = Plugin.Info(
    Plugin.Info.Id("hello-deps"),
    Plugin.Info.Version("0.1.0"),
    Plugin.Info.SpiVersion(Spi.Version)
  )

  def register(config: HelloDepsConfig, ctx: Plugin.Context): Either[Problems, Unit] =
    val greeting = Shout(Shout.ApplyText(config.greeting.value))
    ctx.log.info(Plugin.Log.Line(s"hello-deps: $greeting"))
    ctx.sourceFetchers.add(Registry.AddId("hello-deps"), HelloDepsFetcher(greeting))
