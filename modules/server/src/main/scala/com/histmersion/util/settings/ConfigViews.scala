package com.histmersion
package util.settings

import com.histmersion.api.settings.ConfigFileInfo
import com.histmersion.config.UserConfigFile

/** What the wire says of the user config file: its line in `/api/about`. */
object ConfigViews:
  def configFile(file: UserConfigFile): ConfigFileInfo =
    ConfigFileInfo(
      file.path.map(p => ConfigFileInfo.Path(p.toString)),
      ConfigFileInfo.Loaded(file.loaded.value),
      file.unknownKeys.map(k => ConfigFileInfo.UnknownKeys(k.value))
    )

extension (file: UserConfigFile) def info: ConfigFileInfo = ConfigViews.configFile(file)
