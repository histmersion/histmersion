package com.histmersion.plugins.events

import com.histmersion.plugin.Plugin.Config.given
import hearth.kindlings.sconfigderivation.ConfigReader

/** `histmersion.events.noaa`: NCEI's hazard service, its page size, the pace and the datasets read (comma-separated:
  * `earthquakes`, `volcanoes`, `tsunamis`).
  */
final case class NoaaSettings(url: NoaaSettings.Url, pageSize: NoaaSettings.PageSize, requestIntervalMs: NoaaSettings.RequestIntervalMs, datasets: NoaaSettings.Datasets)
    derives ConfigReader:
  /** The datasets named, in order; a name NCEI has no dataset of is left out. */
  def datasetList: List[com.histmersion.plugins.events.NoaaCatalogue.Dataset] =
    datasets.split(",").map(_.trim).filter(NoaaCatalogue.Datasets.contains).toList

object NoaaSettings:

  type Url = Url.T
  object Url extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PageSize = PageSize.T
  object PageSize extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RequestIntervalMs = RequestIntervalMs.T
  object RequestIntervalMs extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Datasets = Datasets.T
  object Datasets extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
