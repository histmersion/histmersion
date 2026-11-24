package com.histmersion
package plugins.events

import com.histmersion.plugin.Plugin.Config.given
import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{LintRule, tolerated}

/** `histmersion.events.noaa`: NCEI's hazard service, its page size, the pace and the datasets read (comma-separated:
  * `earthquakes`, `volcanoes`, `tsunamis`).
  */
final case class NoaaSettings(
    url: NoaaSettings.Url,
    pageSize: NoaaSettings.PageSize,
    requestIntervalMs: NoaaSettings.RequestIntervalMs,
    datasets: NoaaSettings.Datasets
) derives ConfigReader:
  /** The datasets named, in order; a name NCEI has no dataset of is left out. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def datasetList: List[NoaaCatalogue.Dataset] =
    datasets.value.split(",").map(_.trim).flatMap(d => NoaaCatalogue.Datasets.find(_.value == d)).toList

object NoaaSettings:

  type Url = Url.T
  object Url extends Newtype[String, Pure]
  type PageSize = PageSize.T
  object PageSize extends Newtype[Int, Pure]
  type RequestIntervalMs = RequestIntervalMs.T
  object RequestIntervalMs extends Newtype[Int, Pure]
  type Datasets = Datasets.T
  object Datasets extends Newtype[String, Pure]
