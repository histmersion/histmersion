package com.histmersion
package fixtures.hello
import com.histmersion.util.problem.{Problem, Problems}

import com.histmersion.plugin.*
import com.histmersion.plugin.Plugin.Config.given
import com.histmersion.lint.{LintRule, tolerated}

@tolerated(LintRule.G44, "a test plugin standing in for a third-party author: it builds and reads SPI values directly")
final class ForeignSpiPlugin extends Plugin:
  type Config = HelloConfig
  val info = Plugin.Info(
    Plugin.Info.Id("foreign-spi"),
    Plugin.Info.Version("0.1.0"),
    Plugin.Info.SpiVersion(Spi.Version + 98)
  )
  def register(config: HelloConfig, ctx: Plugin.Context): Either[Problems, Unit] = Right(())
