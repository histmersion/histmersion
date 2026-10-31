package com.histmersion.plugins.calibre

import com.histmersion.plugin.{Plugin, PluginContext, PluginInfo, Spi}

/** `calibre`: the local Calibre library as a source collection — searched and imported from, its gaps pushed, its
  * records audited and rewritten from the library's. On by default (its defaults resource); writing to the library is
  * `write-back`, on by default, and the library's location and tools are the block's own settings.
  */
final class CalibrePlugin extends Plugin:
  type Config = CalibreSettings
  val info: PluginInfo = PluginInfo("calibre", CalibrePlugin.Version, Spi.Version)
  def register(c: CalibreSettings, ctx: PluginContext): Either[String, Unit] =
    ctx.sourceCollections.add(
      "calibre",
      CalibreCollection(c.library(ctx.env("HOME"), ctx.env("PATH")), c.writeBack, Shell.System)
    )

object CalibrePlugin:
  val Version = "1.0.0"
