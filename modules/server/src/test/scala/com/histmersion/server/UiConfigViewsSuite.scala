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

class UiConfigViewsSuite extends munit.FunSuite:

  test("the page's settings: every `ui` setting as read, and what the server adds"):
    val ui = AppConfig.load().ui
    val page = UiConfigViews.page(
      ui,
      writeKeyRequired = UiConfig.WriteKeyRequired(true),
      agentsEnabled = UiConfig.AgentsEnabled(true),
      plugins = PluginsReport(),
      baseMapSource = UiConfig.BaseMapSource("local"),
      mapLanguage = UiConfig.MapLanguage("de"),
      setupDue = UiConfig.SetupDue(true)
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
        writeKeyRequired = UiConfig.WriteKeyRequired(true),
        agentsEnabled = UiConfig.AgentsEnabled(true),
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
        setupDue = UiConfig.SetupDue(true)
      )
    )
