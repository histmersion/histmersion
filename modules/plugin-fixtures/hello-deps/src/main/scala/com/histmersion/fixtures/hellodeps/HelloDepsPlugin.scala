package com.histmersion.fixtures.hellodeps

import com.histmersion.fixtures.hellolib.Shout
import com.histmersion.plugin.*
import com.histmersion.plugin.Plugin.Config.given

final class HelloDepsPlugin extends Plugin:
  type Config = HelloDepsConfig
  val info = Plugin.Info("hello-deps", "0.1.0", Spi.Version)

  def register(config: HelloDepsConfig, ctx: Plugin.Context): Either[com.histmersion.fixtures.hellodeps.HelloDepsPlugin.RegisterResultPart1, Unit] =
    ctx.log.info(s"hello-deps: ${Shout(config.greeting)}")
    ctx.sourceFetchers.add("hello-deps", HelloDepsFetcher(Shout(config.greeting)))

object HelloDepsPlugin:
  type RegisterResultPart1 = RegisterResultPart1.T
  object RegisterResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
