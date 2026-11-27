package com.histmersion
package fixtures.hello
import com.histmersion.util.problem.{Problem, Problems}

import com.histmersion.plugin.*
import com.histmersion.plugin.Plugin.Config.given
import com.histmersion.lint.{tolerated, LintRule}

@tolerated(LintRule.G44, "a test plugin standing in for a third-party author: it builds and reads SPI values directly")
final class HelloPlugin extends Plugin:
  type Config = HelloConfig
  val info = Plugin.Info(
    Plugin.Info.Id("hello"),
    Plugin.Info.Version("0.1.0"),
    Plugin.Info.SpiVersion(Spi.Version)
  )

  def register(config: HelloConfig, ctx: Plugin.Context): Either[Problems, Unit] =
    scala.util
      .Try(
        Class
          .forName(s"com.histmersion.fixtures.hello.${config.greeter}")
          .getDeclaredConstructor()
          .newInstance()
          .asInstanceOf[Greeter]
      )
      .toEither
      .left
      .map(e => Problem.from(e, Problem.Where(s"building the greeter '${config.greeter}'"), Problem.Internal.Id.mint()))
      .flatMap { greeter =>
        ctx.log.info(
          Plugin.Log.Line(
            s"hello: ${greeter.greet(Greeter.Who(config.greeting.value)).value} (${config.mirrors.size} mirror(s))"
          )
        )
        ctx.sourceFetchers
          .add(
            Registry.AddId("hello"),
            HelloFetcher(HelloFetcher.Mirrors(config.mirrors.size))
          )
      }
