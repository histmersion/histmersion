package com.histmersion.pluginhost

import com.histmersion.config.MediaIllustrationsConfig
import com.histmersion.domain.FigurePick

extension (c: MediaIllustrationsConfig)
  def figureRules: FigurePick.Rules =
    FigurePick.Rules(c.minSide, c.minArea, c.maxAspect, c.maxRefs, c.earlyPages, c.pageScanMin)

extension (c: com.histmersion.config.MediaPluginCollectionsConfig)
  /** The families a plugin's collection is allowed: its `per-collection` entry, else the default. */
  def allowed(collection: String): Set[String] =
    com.histmersion.domain.MediaLicence.allowList(c.perCollection.getOrElse(collection, c.licences))
