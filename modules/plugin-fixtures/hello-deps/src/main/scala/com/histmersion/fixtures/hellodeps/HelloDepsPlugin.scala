package com.histmersion.fixtures.hellodeps

import com.histmersion.fixtures.hellolib.Shout
import com.histmersion.plugin.*
import com.histmersion.plugin.Plugin.Config.given

final class HelloDepsPlugin extends Plugin:
  type Config = HelloDepsConfig
  val info = Plugin.Info(
    _root_.com.histmersion.plugin.Plugin.Info.Id("hello-deps"),
    _root_.com.histmersion.plugin.Plugin.Info.Version("0.1.0"),
    _root_.com.histmersion.plugin.Plugin.Info.SpiVersion(Spi.Version)
  )

  def register(
      config: HelloDepsConfig,
      ctx: Plugin.Context
  ): Either[com.histmersion.fixtures.hellodeps.HelloDepsPlugin.RegisterResultPart1, Unit] =
    ctx.log.info(
      _root_.com.histmersion.plugin.Plugin.Log.InfoMessage(
        s"hello-deps: ${Shout(_root_.com.histmersion.fixtures.hellolib.Shout.ApplyText((config.greeting).value))}"
      )
    )
    (ctx.sourceFetchers
      .add(
        _root_.com.histmersion.plugin.Registry.AddId("hello-deps"),
        HelloDepsFetcher(Shout(_root_.com.histmersion.fixtures.hellolib.Shout.ApplyText((config.greeting).value)))
      ))
      .left
      .map(refactorError => _root_.com.histmersion.plugin.Plugin.RegisterResultPart1((refactorError).value))

object HelloDepsPlugin:
  type RegisterResultPart1 = _root_.com.histmersion.plugin.Plugin.RegisterResultPart1
  val RegisterResultPart1: _root_.com.histmersion.plugin.Plugin.RegisterResultPart1.type =
    _root_.com.histmersion.plugin.Plugin.RegisterResultPart1
