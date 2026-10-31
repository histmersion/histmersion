package com.histmersion.pluginhost

import com.histmersion.config.MediaIllustrationsConfig
import com.histmersion.domain.FigurePick

extension (c: MediaIllustrationsConfig)
  def figureRules: FigurePick.Rules =
    FigurePick.Rules(c.minSide, c.minArea, c.maxAspect, c.maxRefs, c.earlyPages, c.pageScanMin)
