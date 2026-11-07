package com.histmersion.server

import com.histmersion.api.plugins.PluginsReport
import com.histmersion.api.instance.UiConfig
import com.histmersion.config.UiSettings
import io.scalaland.chimney.dsl.*

/** What the wire says of the `ui` block: the page's settings as `/api/config` serves them. */
object UiConfigViews:

  /** The page's settings: the `ui` block, plus what the server knows and the block does not hold. */

  def page(
      ui: UiSettings,
      writeKeyRequired: UiConfig.WriteKeyRequired,
      agentsEnabled: UiConfig.AgentsEnabled,
      plugins: PluginsReport,
      baseMapSource: UiConfig.BaseMapSource,
      mapLanguage: UiConfig.MapLanguage,
      setupDue: UiConfig.SetupDue = UiConfig.SetupDue(false)
  ): UiConfig =
    ui.into[UiConfig]
      .withFieldConst(_.setupDue, setupDue)
      .withFieldConst(_.writeKeyRequired, writeKeyRequired)
      .withFieldConst(_.agentsEnabled, agentsEnabled)
      .withFieldConst(_.plugins, plugins)
      .withFieldConst(_.baseMapSource, baseMapSource)
      .withFieldConst(_.mapLanguage, mapLanguage)
      .transform
