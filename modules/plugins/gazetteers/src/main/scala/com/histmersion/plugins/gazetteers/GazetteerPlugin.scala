package com.histmersion.plugins.gazetteers

import com.histmersion.plugin.{Plugin, Spi}

/** One of Histmersion's own network gazetteers: on by default (its defaults resource), switched off by
  * `histmersion.plugins.gazetteer-<source>.enabled = false`. It has no settings of its own: the host lends its download
  * the client `histmersion.places.fetch-*` sets. It registers its one source under the name `places fetch` takes.
  */
trait GazetteerPlugin(id: String, name: String, source: DownloadedGazetteer) extends Plugin:
  type Config = GazetteerSettings
  val info: Plugin.Info = Plugin.Info(id, GazetteerPlugin.Version, Spi.Version)
  def register(c: GazetteerSettings, ctx: Plugin.Context): Either[com.histmersion.plugins.gazetteers.GazetteerPlugin.RegisterResultPart1, Unit] =
    ctx.gazetteerSources.add(name, source)

object GazetteerPlugin:
  type RegisterResultPart1 = RegisterResultPart1.T
  object RegisterResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val Version = "0.1.0"
