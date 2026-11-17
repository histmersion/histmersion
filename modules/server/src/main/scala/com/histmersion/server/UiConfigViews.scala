package com.histmersion
package server

import com.histmersion.api.plugins.PluginsReport
import com.histmersion.api.instance.UiConfig
import com.histmersion.config.UiSettings
import io.scalaland.chimney.dsl.*
import com.histmersion.api.catalogue.Year
import com.histmersion.api.instance.MapStartMode

/** What the wire says of the `ui` block: the page's settings as `/api/config` serves them. */
object UiConfigViews:

  /** The page's settings: the `ui` block, plus what the server knows and the block does not hold. */

  def page(
      ui: UiSettings,
      keyRequired: UiConfig.KeyRequired,
      agentsEnabled: UiConfig.AgentsEnabled,
      plugins: PluginsReport,
      baseMapSource: UiConfig.BaseMapSource,
      mapLanguage: UiConfig.MapLanguage,
      setupDue: UiConfig.SetupDue = UiConfig.SetupDue(false)
  ): UiConfig =
    ui.into[UiConfig]
      .withFieldConst(_.setupDue, setupDue)
      .withFieldComputed(_.time.min, u => Year(u.time.min.value))
      .withFieldComputed(_.time.max, u => Year(u.time.max.value))
      .withFieldComputed(_.time.defaultFrom, u => Year(u.time.defaultFrom.value))
      .withFieldComputed(_.time.defaultTo, u => Year(u.time.defaultTo.value))
      .withFieldComputed(_.mapStart.mode, u => MapStartMode.Wire(u.mapStart.mode.value))
      .withFieldConst(_.keyRequired, keyRequired)
      .withFieldConst(_.agentsEnabled, agentsEnabled)
      .withFieldConst(_.plugins, plugins)
      .withFieldConst(_.baseMapSource, baseMapSource)
      .withFieldConst(_.mapLanguage, mapLanguage)
      .transform
