package com.histmersion.util.settings

import com.histmersion.config.MergeConfig
import com.histmersion.domain.MergeSettings
import io.scalaland.chimney.dsl.*

object MergeSettingsViews:
  def settings(c: MergeConfig): MergeSettings = c.transformInto[MergeSettings]
