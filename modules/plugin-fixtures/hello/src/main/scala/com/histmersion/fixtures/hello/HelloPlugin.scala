package com.histmersion.fixtures.hello

import com.histmersion.plugin.*
import com.histmersion.plugin.Plugin.Config.given

/** The provider (META-INF/services/com.histmersion.plugin.Plugin): one open-access source fetcher that has no copy of
  * anything.
  */
final class HelloPlugin extends Plugin:
  type Config = HelloConfig
  val info = Plugin.Info("hello", "0.1.0", Spi.Version)

  def register(config: HelloConfig, ctx: Plugin.Context): Either[com.histmersion.fixtures.hello.HelloPlugin.RegisterResultPart1, Unit] =
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

object HelloPlugin:
  type RegisterResultPart1 = RegisterResultPart1.T
  object RegisterResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
