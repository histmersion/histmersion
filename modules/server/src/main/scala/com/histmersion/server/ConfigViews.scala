package com.histmersion.server

import com.histmersion.api.{ConfigFileInfo, PluginsReport, UiConfig}
import com.histmersion.config.{UiSettings, UserConfigFile}
import io.scalaland.chimney.Transformer
import io.scalaland.chimney.dsl.*

import java.nio.file.Path

/** What the wire says of the configuration: the `ui` block as `/api/config` serves it, and the user config file's line
  * in `/api/about`.
  */
object ConfigViews:
  private given Transformer[Path, String] = _.toString

  /** The page's settings: the `ui` block, plus what the server knows and the block does not hold. */
  def page(
      ui: UiSettings,
      writeKeyRequired: Boolean,
      agentsEnabled: Boolean,
      plugins: PluginsReport,
      baseMapSource: String,
      mapLanguage: String,
      setupDue: Boolean = false
  ): UiConfig =
    ui.into[UiConfig]
      .withFieldConst(_.setupDue, setupDue)
      .withFieldConst(_.writeKeyRequired, writeKeyRequired)
      .withFieldConst(_.agentsEnabled, agentsEnabled)
      .withFieldConst(_.plugins, plugins)
      .withFieldConst(_.baseMapSource, baseMapSource)
      .withFieldConst(_.mapLanguage, mapLanguage)
      .transform

  def configFile(file: UserConfigFile): ConfigFileInfo = file.transformInto[ConfigFileInfo]

extension (file: UserConfigFile) def info: ConfigFileInfo = ConfigViews.configFile(file)
