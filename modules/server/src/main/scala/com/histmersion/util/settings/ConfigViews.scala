package com.histmersion
package util.settings

import com.histmersion.api.settings.ConfigFileInfo
import com.histmersion.config.UserConfigFile
import com.histmersion.lint.{tolerated, LintRule}

/** What the wire says of the user config file: its line in `/api/about`. */
object ConfigViews:
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def configFile(file: UserConfigFile): ConfigFileInfo =
    ConfigFileInfo(
      file.path.map(p => ConfigFileInfo.Path(p.toString)),
      ConfigFileInfo.Loaded(file.loaded.value),
      file.unknownKeys.map(k => ConfigFileInfo.UnknownKeys(k.value))
    )

extension (file: UserConfigFile) def info: ConfigFileInfo = ConfigViews.configFile(file)
