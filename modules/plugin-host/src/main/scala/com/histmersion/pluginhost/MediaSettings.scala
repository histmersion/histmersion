package com.histmersion.pluginhost

import com.histmersion.config.MediaIllustrationsConfig
import com.histmersion.domain.media.figures.FigurePick
import com.histmersion.domain.media.imports.LicencePolicy

extension (c: MediaIllustrationsConfig)
  def figureRules: FigurePick.Rules =
    FigurePick.Rules(
      FigurePick.Rules.MinSide(c.minSide.value),
      FigurePick.Rules.MinArea(c.minArea.value),
      FigurePick.Rules.MaxAspect(c.maxAspect.value),
      FigurePick.Rules.MaxRefs(c.maxRefs.value),
      FigurePick.Rules.EarlyPages(c.earlyPages.value),
      FigurePick.Rules.PageScanMin(c.pageScanMin.value)
    )

extension (c: com.histmersion.config.MediaPluginCollectionsConfig)
  /** The policy a plugin's collection is judged by: its `per-collection` entry, else the default. */
  def allowed(collection: com.histmersion.config.MediaPluginCollectionsConfig.PerCollectionKey): LicencePolicy =
    LicencePolicy.of(com.histmersion.domain.Licence.AllowList(c.licencesFor(collection).value))
