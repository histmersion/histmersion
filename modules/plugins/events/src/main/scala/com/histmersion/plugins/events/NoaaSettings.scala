package com.histmersion.plugins.events

import com.histmersion.plugin.Plugin.Config.given
import hearth.kindlings.sconfigderivation.ConfigReader

/** `histmersion.events.noaa`: NCEI's hazard service, its page size, the pace and the datasets read (comma-separated:
  * `earthquakes`, `volcanoes`, `tsunamis`).
  */
final case class NoaaSettings(url: String, pageSize: Int, requestIntervalMs: Int, datasets: String)
    derives ConfigReader:
  /** The datasets named, in order; a name NCEI has no dataset of is left out. */
  def datasetList: List[String] =
    datasets.split(",").map(_.trim).filter(NoaaCatalogue.Datasets.contains).toList
