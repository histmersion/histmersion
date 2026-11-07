package com.histmersion.plugins.gazetteers

import com.histmersion.plugin.{Plugin, Spi}

/** One of Histmersion's own network gazetteers: on by default (its defaults resource), switched off by
  * `histmersion.plugins.gazetteer-<source>.enabled = false`. It has no settings of its own: the host lends its download
  * the client `histmersion.places.fetch-*` sets. It registers its one source under the name `places fetch` takes.
  */
trait GazetteerPlugin(id: String, name: String, source: DownloadedGazetteer) extends Plugin:
  type Config = GazetteerSettings
  val info: Plugin.Info = Plugin.Info(Plugin.Info.Id(id), Plugin.Info.Version(GazetteerPlugin.Version), Plugin.Info.SpiVersion(Spi.Version))
  def register(c: GazetteerSettings, ctx: Plugin.Context): Either[Plugin.RegisterResultPart1, Unit] =
    ctx.gazetteerSources.add(com.histmersion.plugin.Registry.AddId(name), source).left.map(e => Plugin.RegisterResultPart1(e.value))

object GazetteerPlugin:
  val Version = "0.1.0"
