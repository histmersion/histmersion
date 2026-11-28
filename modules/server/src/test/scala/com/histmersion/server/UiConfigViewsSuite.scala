package com.histmersion
package server

import com.histmersion.api.instance.{DebounceConfig, EventsUiConfig, TimeConfig, UiConfig}
import com.histmersion.api.catalogue.MapTilesUiConfig
import com.histmersion.api.plugins.PluginsReport
import com.histmersion.config.AppConfig
import com.histmersion.api.catalogue.Year
import com.histmersion.api.instance.MapStartConfig
import com.histmersion.api.instance.MapStartMode
import com.histmersion.api.catalogue.Latitude
import com.histmersion.api.catalogue.Longitude
import com.histmersion.config.UiSettings
import hearth.kindlings.optics.syntax.*
import com.histmersion.lint.{tolerated, LintRule}

@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class UiConfigViewsSuite extends munit.FunSuite:

  private val muted = "https://{s}.tile.openstreetmap.fr/hot/{z}/{x}/{y}.png"

  test("the tile templates the page may fetch from: ui.tiles and the Muted map's, none when the base map is local"):
    val ui = AppConfig.load(org.ekrich.config.ConfigFactory.empty).ui
    def at(tiles: String, source: String): List[String] =
      UiConfigViews.tileTemplates(
        ui.modify(_.tiles)
          .setTo(UiSettings.Tiles(tiles))
          .modify(_.baseMapSource)
          .setTo(UiSettings.BaseMapSource(source))
      )
    assertEquals(at("https://t.example.test/{z}", "auto"), List("https://t.example.test/{z}", muted))
    assertEquals(at("https://t.example.test/{z}", "online"), List("https://t.example.test/{z}", muted))
    assertEquals(at("https://t.example.test/{z}", "local"), Nil)
    assertEquals(at("https://t.example.test/{z}", " Local "), Nil)
    assertEquals(UiConfigViews.tileTemplates(ui), List(ui.tiles.value, muted))
    assertEquals(UiConfigViews.TileKeys, List("histmersion.ui.tiles", "histmersion.ui.base-map-source"))

  test("the page's settings: every `ui` setting as read, and what the server adds"):
    val ui = AppConfig.load().ui
    val page = UiConfigViews.page(
      ui,
      keyRequired = UiConfig.KeyRequired.KeyRequired,
      agentsEnabled = UiConfig.AgentSwitch.Enabled,
      plugins = PluginsReport(),
      baseMapSource = UiConfig.BaseMapSource("local"),
      mapLanguage = UiConfig.MapLanguage("de"),
      setupDue = UiConfig.SetupDue.SetupDue
    )
    assertEquals(
      page,
      UiConfig(
        UiConfig.Tiles(ui.tiles.value),
        UiConfig.TilesMaxZoom(ui.tilesMaxZoom.value),
        UiConfig.MarkerRadius(ui.markerRadius.value),
        UiConfig.ViewportDebounceMs(ui.viewportDebounceMs.value),
        UiConfig.PageSize(ui.pageSize.value),
        UiConfig.FacetSuggestions(ui.facetSuggestions.value),
        UiConfig.TreeDepth(ui.treeDepth.value),
        UiConfig.TreeSamples(ui.treeSamples.value),
        UiConfig.MaxBuckets(ui.maxBuckets.value),
        DebounceConfig(
          DebounceConfig.SuggestMs(ui.debounce.suggestMs.value),
          DebounceConfig.SearchMs(ui.debounce.searchMs.value),
          DebounceConfig.ReloadMs(ui.debounce.reloadMs.value),
          DebounceConfig.PreviewMs(ui.debounce.previewMs.value)
        ),
        TimeConfig(
          Year(ui.time.min.value),
          Year(ui.time.max.value),
          Year(ui.time.defaultFrom.value),
          Year(ui.time.defaultTo.value)
        ),
        UiConfig.NarrowMaxWidthPx(ui.narrowMaxWidthPx.value),
        UiConfig.NarrowMaxHeightPx(ui.narrowMaxHeightPx.value),
        UiConfig.TabletMaxWidthPx(ui.tabletMaxWidthPx.value),
        EventsUiConfig(
          EventsUiConfig.LocalFadeSeconds(ui.events.localFadeSeconds.value),
          EventsUiConfig.RegionalFadeSeconds(ui.events.regionalFadeSeconds.value),
          EventsUiConfig.MapLimit(ui.events.mapLimit.value),
          EventsUiConfig.OutlineToleranceDeg(ui.events.outlineToleranceDeg.value)
        ),
        MapTilesUiConfig(
          MapTilesUiConfig.MaxZoom(ui.mapTiles.maxZoom.value),
          MapTilesUiConfig.PlaybackBucketSeconds(ui.mapTiles.playbackBucketSeconds.value)
        ),
        UiConfig.CountersPollSeconds(ui.countersPollSeconds.value),
        UiConfig.JobPollMs(ui.jobPollMs.value),
        keyRequired = UiConfig.KeyRequired.KeyRequired,
        agentsEnabled = UiConfig.AgentSwitch.Enabled,
        UiConfig.TilesTone(ui.tilesTone.value),
        PluginsReport(),
        UiConfig.DocsUrl(ui.docsUrl.value),
        baseMapSource = UiConfig.BaseMapSource("local"),
        mapLanguage = UiConfig.MapLanguage("de"),
        MapStartConfig(
          MapStartMode.Wire(ui.mapStart.mode.value),
          Latitude(ui.mapStart.lat.value),
          Longitude(ui.mapStart.lon.value),
          MapStartConfig.Zoom(ui.mapStart.zoom.value),
          MapStartConfig.Label(ui.mapStart.label.value)
        ),
        setupDue = UiConfig.SetupDue.SetupDue
      )
    )
