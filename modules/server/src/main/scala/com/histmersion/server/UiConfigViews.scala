package com.histmersion
package server

import com.histmersion.api.plugins.PluginsReport
import com.histmersion.api.instance.{BuildId, UiConfig}
import com.histmersion.config.UiSettings
import io.scalaland.chimney.dsl.*
import com.histmersion.api.catalogue.Year
import com.histmersion.api.instance.MapStartMode
import com.histmersion.api.basemap.BaseMapSource
import com.histmersion.lint.{movesTo, FeaturePlace}
import com.histmersion.lint.{tolerated, LintRule}

/** What the wire says of the `ui` block: the page's settings as `/api/config` serves them, and the tile servers the
  * page fetches from as the Content-Security-Policy names them.
  */
@movesTo(FeaturePlace.ServerInstance)
object UiConfigViews:

  /** The settings [[tileTemplates]] reads: a reload that changes one of them rebuilds what is made from it. */
  val TileKeys: List[String] = List("histmersion.ui.tiles", "histmersion.ui.base-map-source")

  /** The tile templates the page's online base maps may fetch from: `ui.tiles` (Standard and Dark) and the Muted map's
    * ([[BaseMapSource.MutedTiles]]). None when `ui.base-map-source` is `local`: the page then offers no online map and
    * asks no other host for one.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def tileTemplates(ui: UiSettings): List[String] =
    if ui.baseMapSource.value.trim.equalsIgnoreCase(BaseMapSource.Local) then Nil
    else List(ui.tiles.value, BaseMapSource.MutedTiles.value)

  /** The page's settings: the `ui` block, plus what the server knows and the block does not hold. */

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def page(
      ui: UiSettings,
      keyRequired: UiConfig.KeyRequired,
      agentsEnabled: UiConfig.AgentsEnabled,
      plugins: PluginsReport,
      baseMapSource: UiConfig.BaseMapSource,
      mapLanguage: UiConfig.MapLanguage,
      setupDue: UiConfig.SetupDue = UiConfig.SetupDue.SetUp,
      buildId: BuildId = BuildId("")
  ): UiConfig =
    ui.into[UiConfig]
      .withFieldConst(_.setupDue, setupDue)
      .withFieldConst(_.buildId, buildId)
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
