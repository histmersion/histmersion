package com.histmersion.fixtures.hello

import com.histmersion.plugin.*
import com.histmersion.plugin.Plugin.Config.given

/** Built for a FOREIGN SPI generation. Not listed in this jar's services file: the tests write a jar whose services
  * file names it, to see it refused before it is installed or linked.
  */
final class ForeignSpiPlugin extends Plugin:
  type Config = HelloConfig
  val info = Plugin.Info("foreign-spi", "0.1.0", Spi.Version + 98)
  def register(config: HelloConfig, ctx: Plugin.Context): Either[String, Unit] = Right(())
