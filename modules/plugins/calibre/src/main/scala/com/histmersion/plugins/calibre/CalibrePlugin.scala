package com.histmersion
package plugins.calibre

import com.histmersion.plugin.{Plugin, Spi}
import com.histmersion.plugin.LocalFiles
import com.histmersion.plugin.Processes
import com.histmersion.plugin.Registry

/** `calibre`: the local Calibre library as a source collection — searched and imported from, its gaps pushed, its
  * records audited and rewritten from the library's. On by default (its defaults resource); writing to the library is
  * `write-back`, on by default, and the library's location and tools are the block's own settings.
  */
final class CalibrePlugin extends Plugin:
  type Config = Calibre.Settings
  val info: Plugin.Info = Plugin.Info(
    Plugin.Info.Id("calibre"),
    Plugin.Info.Version(CalibrePlugin.Version),
    Plugin.Info.SpiVersion(Spi.Version)
  )
  def register(c: Calibre.Settings, ctx: Plugin.Context): Either[Plugin.RegisterResultPart1, Unit] =
    given files: LocalFiles = ctx.files
    given processes: Processes = ctx.processes
    ctx.sourceCollections
      .add(
        Registry.AddId("calibre"),
        CalibreCollection(
          c.library(ctx.env(Plugin.Context.EnvName("HOME")), ctx.env(Plugin.Context.EnvName("PATH"))),
          CalibreCollection.WriteBack(c.writeBack.value),
          Shell.system
        )
      )
      .left
      .map(e => Plugin.RegisterResultPart1(e.value))

object CalibrePlugin:
  val Version = "0.1.0"
