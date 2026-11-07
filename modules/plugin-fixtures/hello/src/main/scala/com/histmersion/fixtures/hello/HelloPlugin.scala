package com.histmersion.fixtures.hello

import com.histmersion.plugin.*
import com.histmersion.plugin.Plugin.Config.given

/** The provider (META-INF/services/com.histmersion.plugin.Plugin): one open-access source fetcher that has no copy of
  * anything.
  */
final class HelloPlugin extends Plugin:
  type Config = HelloConfig
  val info = Plugin.Info(_root_.com.histmersion.plugin.Plugin.Info.Id("hello"), _root_.com.histmersion.plugin.Plugin.Info.Version("0.1.0"), _root_.com.histmersion.plugin.Plugin.Info.SpiVersion(Spi.Version))

  def register(config: HelloConfig, ctx: Plugin.Context): Either[Plugin.RegisterResultPart1, Unit] =
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
      .map(e => Plugin.RegisterResultPart1(s"greeter '${config.greeter}': ${e.getClass.getSimpleName}: ${e.getMessage}"))
      .flatMap { greeter =>
        ctx.log.info(Plugin.Log.InfoMessage(s"hello: ${greeter.greet(Greeter.Who(config.greeting.value)).value} (${config.mirrors.size} mirror(s))"))
        ctx.sourceFetchers.add(_root_.com.histmersion.plugin.Registry.AddId("hello"), HelloFetcher(HelloFetcher.Mirrors(config.mirrors.size))).left.map(e => Plugin.RegisterResultPart1(e.value))
      }

