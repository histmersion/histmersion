package com.histmersion
package plugins.gazetteers
import com.histmersion.util.problem.Problem

import com.histmersion.plugin.{Plugin, Spi}
import com.histmersion.plugin.Registry

/** One of Histmersion's own network gazetteers: on by default (its defaults resource), switched off by
  * `histmersion.plugins.gazetteer-<source>.enabled = false`. It has no settings of its own: the host lends its download
  * the client `histmersion.places.fetch-*` sets. It registers its one source under the name `places fetch` takes.
  */
trait GazetteerPlugin(id: String, name: String, source: DownloadedGazetteer) extends Plugin:
  type Config = GazetteerSettings
  val info: Plugin.Info =
    Plugin.Info(Plugin.Info.Id(id), Plugin.Info.Version(GazetteerPlugin.Version), Plugin.Info.SpiVersion(Spi.Version))
  def register(c: GazetteerSettings, ctx: Plugin.Context): Either[Problem, Unit] =
    ctx.gazetteerSources.add(Registry.AddId(name), source)

object GazetteerPlugin:
  val Version = "0.1.0"
