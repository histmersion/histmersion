package com.histmersion.plugins.gazetteers

import com.histmersion.plugin.{Plugin, PluginContext, PluginInfo, Spi}

/** One of Histmersion's own network gazetteers: on by default (its defaults resource), switched off by
  * `histmersion.plugins.gazetteer-<source>.enabled = false`. It has no settings of its own: the host lends its download
  * the client `histmersion.places.fetch-*` sets. It registers its one source under the name `places fetch` takes.
  */
trait GazetteerPlugin(id: String, name: String, source: DownloadedGazetteer) extends Plugin:
  type Config = GazetteerSettings
  val info: PluginInfo = PluginInfo(id, GazetteerPlugin.Version, Spi.Version)
  def register(c: GazetteerSettings, ctx: PluginContext): Either[String, Unit] =
    ctx.gazetteerSources.add(name, source)

object GazetteerPlugin:
  val Version = "1.0.0"
