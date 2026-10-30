package com.histmersion.fixtures.hello

import com.histmersion.plugin.*
import com.histmersion.plugin.PluginConfig.given

/** The provider (META-INF/services/com.histmersion.plugin.Plugin): one open-access source fetcher that has no copy of
  * anything.
  */
final class HelloPlugin extends Plugin:
  type Config = HelloConfig
  val info = PluginInfo("hello", "0.1.0", Spi.Version)

  def register(config: HelloConfig, ctx: PluginContext): Either[String, Unit] =
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
      .map(e => s"greeter '${config.greeter}': ${e.getClass.getSimpleName}: ${e.getMessage}")
      .flatMap { greeter =>
        ctx.log.info(s"hello: ${greeter.greet(config.greeting)} (${config.mirrors.size} mirror(s))")
        ctx.sourceFetchers.add("hello", HelloFetcher(config.mirrors.size))
      }
