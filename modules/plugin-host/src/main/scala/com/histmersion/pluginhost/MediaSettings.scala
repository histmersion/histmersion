package com.histmersion.pluginhost

import com.histmersion.config.{MediaCollectionConfig, MediaCommonsConfig, MediaDepictsConfig, MediaIllustrationsConfig,
  MediaPluginCollectionsConfig, MediaWikidataConfig}
import com.histmersion.domain.{Europeana, FigurePick, MediaLicence, MediaRoles}

extension (c: MediaCommonsConfig) def allowed: Set[String] = MediaLicence.allowList(c.licences)
extension (c: MediaDepictsConfig) def allowed: Set[String] = MediaLicence.allowList(c.licences)
extension (c: MediaCollectionConfig)
  def allowed: Set[String] = MediaLicence.allowList(c.licences)
  /** `max-year` as the last year an object may be made in; 0 sets none. */
  def latestYear: Option[Int] = Option.when(c.maxYear != 0)(c.maxYear)
  def excludedProviders: List[String] = MediaSettings.items(c.excludeProviders)
  def europeanaExclusions: Europeana.Exclusions =
    Europeana.Exclusions(excludedProviders, MediaSettings.items(c.excludeDataProviders), MediaSettings.items(c.excludeTypes))

object MediaSettings:
  /** Pure: the entries of a comma-separated setting, trimmed, blanks dropped. */
  def items(list: String): List[String] = Option(list).toList.flatMap(_.split(",")).map(_.trim).filter(_.nonEmpty)

extension (c: MediaPluginCollectionsConfig)
  def allowed(collection: String): Set[String] = MediaLicence.allowList(c.perCollection.getOrElse(collection, c.licences))
extension (c: MediaWikidataConfig)
  def placeRoles: List[(String, String)] = MediaRoles.parse(c.placeProperties)
  def eventRoles: List[(String, String)] = MediaRoles.parse(c.eventProperties)
extension (c: MediaIllustrationsConfig)
  def figureRules: FigurePick.Rules = FigurePick.Rules(c.minSide, c.minArea, c.maxAspect, c.maxRefs, c.earlyPages, c.pageScanMin)
