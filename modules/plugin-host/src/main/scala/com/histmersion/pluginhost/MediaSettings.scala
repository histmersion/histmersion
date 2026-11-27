package com.histmersion
package pluginhost

import com.histmersion.config.MediaIllustrationsConfig
import com.histmersion.domain.media.figures.FigurePick
import com.histmersion.domain.media.imports.LicencePolicy
import com.histmersion.config.MediaPluginCollectionsConfig
import com.histmersion.domain.Licence
import com.histmersion.lint.{tolerated, LintRule}

extension (c: MediaIllustrationsConfig)
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def figureRules: FigurePick.Rules =
    FigurePick.Rules(
      FigurePick.Rules.MinSide(c.minSide.value),
      FigurePick.Rules.MinArea(c.minArea.value),
      FigurePick.Rules.MaxAspect(c.maxAspect.value),
      FigurePick.Rules.MaxRefs(c.maxRefs.value),
      FigurePick.Rules.EarlyPages(c.earlyPages.value),
      FigurePick.Rules.PageScanMin(c.pageScanMin.value)
    )

extension (c: MediaPluginCollectionsConfig)
  /** The policy a plugin's collection is judged by: its `per-collection` entry, else the default. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def allowed(collection: MediaPluginCollectionsConfig.PerCollectionKey): LicencePolicy =
    LicencePolicy.of(Licence.AllowList(c.licencesFor(collection).value))
