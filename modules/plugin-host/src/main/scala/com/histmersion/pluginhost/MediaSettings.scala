package com.histmersion.pluginhost

import com.histmersion.config.{MediaCollectionConfig, MediaCommonsConfig, MediaDepictsConfig, MediaIllustrationsConfig,
  MediaPluginCollectionsConfig, MediaWikidataConfig}
import com.histmersion.domain.{FigurePick, MediaLicence, MediaRoles}

extension (c: MediaCommonsConfig) def allowed: Set[String] = MediaLicence.allowList(c.licences)
extension (c: MediaDepictsConfig) def allowed: Set[String] = MediaLicence.allowList(c.licences)
extension (c: MediaCollectionConfig) def allowed: Set[String] = MediaLicence.allowList(c.licences)
extension (c: MediaPluginCollectionsConfig)
  def allowed(collection: String): Set[String] = MediaLicence.allowList(c.perCollection.getOrElse(collection, c.licences))
extension (c: MediaWikidataConfig)
  def placeRoles: List[(String, String)] = MediaRoles.parse(c.placeProperties)
  def eventRoles: List[(String, String)] = MediaRoles.parse(c.eventProperties)
extension (c: MediaIllustrationsConfig)
  def figureRules: FigurePick.Rules = FigurePick.Rules(c.minSide, c.minArea, c.maxAspect, c.maxRefs, c.earlyPages, c.pageScanMin)
