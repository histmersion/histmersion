package com.histmersion.fixtures.hello

import com.histmersion.plugin.*
import com.histmersion.plugin.Plugin.Config.given

/** Built for a FOREIGN SPI generation. Not listed in this jar's services file: the tests write a jar whose services
  * file names it, to see it refused before it is installed or linked.
  */
final class ForeignSpiPlugin extends Plugin:
  type Config = HelloConfig
  val info = Plugin.Info(
    _root_.com.histmersion.plugin.Plugin.Info.Id("foreign-spi"),
    _root_.com.histmersion.plugin.Plugin.Info.Version("0.1.0"),
    _root_.com.histmersion.plugin.Plugin.Info.SpiVersion(Spi.Version + 98)
  )
  def register(config: HelloConfig, ctx: Plugin.Context): Either[Plugin.RegisterResultPart1, Unit] = Right(())
