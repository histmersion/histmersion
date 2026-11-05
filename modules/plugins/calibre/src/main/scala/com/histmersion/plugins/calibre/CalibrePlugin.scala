package com.histmersion.plugins.calibre

import com.histmersion.plugin.{Plugin, Spi}

/** `calibre`: the local Calibre library as a source collection — searched and imported from, its gaps pushed, its
  * records audited and rewritten from the library's. On by default (its defaults resource); writing to the library is
  * `write-back`, on by default, and the library's location and tools are the block's own settings.
  */
final class CalibrePlugin extends Plugin:
  type Config = Calibre.Settings
  val info: Plugin.Info = Plugin.Info("calibre", CalibrePlugin.Version, Spi.Version)
  def register(c: Calibre.Settings, ctx: Plugin.Context): Either[String, Unit] =
    given files: com.histmersion.plugin.LocalFiles = ctx.files
    given processes: com.histmersion.plugin.Processes = ctx.processes
    ctx.sourceCollections.add(
      "calibre",
      CalibreCollection(c.library(ctx.env("HOME"), ctx.env("PATH")), c.writeBack, Shell.system)
    )

object CalibrePlugin:
  val Version = "0.1.0"
