package com.histmersion.util.settings

import com.histmersion.api.ConfigFileInfo
import com.histmersion.config.UserConfigFile

/** What the wire says of the user config file: its line in `/api/about`. */
object ConfigViews:
  def configFile(file: UserConfigFile): ConfigFileInfo =
    ConfigFileInfo(file.path.map(_.toString), file.loaded, file.unknownKeys)

extension (file: UserConfigFile) def info: ConfigFileInfo = ConfigViews.configFile(file)
