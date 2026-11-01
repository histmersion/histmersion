package com.histmersion.pluginhost

import com.histmersion.config.MediaIllustrationsConfig
import com.histmersion.domain.media.figures.FigurePick
import com.histmersion.domain.media.imports.LicencePolicy

extension (c: MediaIllustrationsConfig)
  def figureRules: FigurePick.Rules =
    FigurePick.Rules(c.minSide, c.minArea, c.maxAspect, c.maxRefs, c.earlyPages, c.pageScanMin)

extension (c: com.histmersion.config.MediaPluginCollectionsConfig)
  /** The policy a plugin's collection is judged by: its `per-collection` entry, else the default. */
  def allowed(collection: String): LicencePolicy =
    LicencePolicy.of(c.perCollection.getOrElse(collection, c.licences))
