package com.histmersion
package util.settings

import com.histmersion.config.MergeConfig
import com.histmersion.domain.MergeSettings
import io.scalaland.chimney.dsl.*
import com.histmersion.lint.{LintRule, tolerated}

object MergeSettingsViews:
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def settings(c: MergeConfig): MergeSettings = c.transformInto[MergeSettings]
