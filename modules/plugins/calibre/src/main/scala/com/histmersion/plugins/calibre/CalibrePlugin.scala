package com.histmersion.plugins.calibre

import com.histmersion.plugin.{Plugin, Spi}

/** `calibre`: the local Calibre library as a source collection — searched and imported from, its gaps pushed, its
  * records audited and rewritten from the library's. On by default (its defaults resource); writing to the library is
  * `write-back`, on by default, and the library's location and tools are the block's own settings.
  */
final class CalibrePlugin extends Plugin:
  type Config = Calibre.Settings
  val info: Plugin.Info = Plugin.Info(_root_.com.histmersion.plugin.Plugin.Info.Id("calibre"), _root_.com.histmersion.plugin.Plugin.Info.Version(CalibrePlugin.Version), _root_.com.histmersion.plugin.Plugin.Info.SpiVersion(Spi.Version))
  def register(c: Calibre.Settings, ctx: Plugin.Context): Either[com.histmersion.plugins.calibre.CalibrePlugin.RegisterResultPart1, Unit] =
    given files: com.histmersion.plugin.LocalFiles = ctx.files
    given processes: com.histmersion.plugin.Processes = ctx.processes
    ctx.sourceCollections.add(
      _root_.com.histmersion.plugin.Registry.AddId("calibre"),
      CalibreCollection(c.library(ctx.env(_root_.com.histmersion.plugin.Plugin.Context.EnvName("HOME")), ctx.env(_root_.com.histmersion.plugin.Plugin.Context.EnvName("PATH"))), c.writeBack, Shell.system)
    )

object CalibrePlugin:
  type RegisterResultPart1 = RegisterResultPart1.T
  object RegisterResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val Version = "0.1.0"
