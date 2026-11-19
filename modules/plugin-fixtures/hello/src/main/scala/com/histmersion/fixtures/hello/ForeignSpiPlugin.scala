package com.histmersion
package fixtures.hello
import com.histmersion.util.problem.{Problem, Problems}

import com.histmersion.plugin.*
import com.histmersion.plugin.Plugin.Config.given

/** Built for a FOREIGN SPI generation. Not listed in this jar's services file: the tests write a jar whose services
  * file names it, to see it refused before it is installed or linked.
  */
final class ForeignSpiPlugin extends Plugin:
  type Config = HelloConfig
  val info = Plugin.Info(
    Plugin.Info.Id("foreign-spi"),
    Plugin.Info.Version("0.1.0"),
    Plugin.Info.SpiVersion(Spi.Version + 98)
  )
  def register(config: HelloConfig, ctx: Plugin.Context): Either[Problems, Unit] = Right(())
