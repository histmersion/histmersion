package com.histmersion.fixtures.hellodeps

import com.histmersion.fixtures.hellolib.Shout
import com.histmersion.plugin.*
import com.histmersion.plugin.PluginConfig.given

final class HelloDepsPlugin extends Plugin:
  type Config = HelloDepsConfig
  val info = PluginInfo("hello-deps", "0.1.0", Spi.Version)

  def register(config: HelloDepsConfig, ctx: PluginContext): Either[String, Unit] =
    ctx.log.info(s"hello-deps: ${Shout(config.greeting)}")
    ctx.sourceFetchers.add("hello-deps", HelloDepsFetcher(Shout(config.greeting)))
