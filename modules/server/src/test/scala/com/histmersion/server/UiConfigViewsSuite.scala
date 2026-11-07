package com.histmersion.server

import com.histmersion.api.instance.{DebounceConfig, EventsUiConfig, TimeConfig, UiConfig}
import com.histmersion.api.catalogue.MapTilesUiConfig
import com.histmersion.api.plugins.PluginsReport
import com.histmersion.config.AppConfig

class UiConfigViewsSuite extends munit.FunSuite:

  test("the page's settings: every `ui` setting as read, and what the server adds"):
    val ui = AppConfig.load().ui
    val page = UiConfigViews.page(
      ui,
      writeKeyRequired = com.histmersion.api.instance.UiConfig.WriteKeyRequired(true),
      agentsEnabled = com.histmersion.api.instance.UiConfig.AgentsEnabled(true),
      plugins = PluginsReport(),
      baseMapSource = com.histmersion.api.instance.UiConfig.BaseMapSource("local"),
      mapLanguage = com.histmersion.api.instance.UiConfig.MapLanguage("de"),
      setupDue = com.histmersion.api.instance.UiConfig.SetupDue(true)
    )
    assertEquals(
      page,
      UiConfig(
        ui.tiles,
        ui.tilesMaxZoom,
        ui.markerRadius,
        ui.viewportDebounceMs,
        ui.pageSize,
        ui.facetSuggestions,
        ui.treeDepth,
        ui.treeSamples,
        ui.maxBuckets,
        DebounceConfig(ui.debounce.suggestMs, ui.debounce.searchMs, ui.debounce.reloadMs, ui.debounce.previewMs),
        TimeConfig(ui.time.min, ui.time.max, ui.time.defaultFrom, ui.time.defaultTo),
        ui.narrowMaxWidthPx,
        ui.narrowMaxHeightPx,
        ui.tabletMaxWidthPx,
        EventsUiConfig(
          ui.events.localFadeSeconds,
          ui.events.regionalFadeSeconds,
          ui.events.mapLimit,
          ui.events.outlineToleranceDeg
        ),
        MapTilesUiConfig(ui.mapTiles.maxZoom, ui.mapTiles.playbackBucketSeconds),
        ui.countersPollSeconds,
        ui.jobPollMs,
        writeKeyRequired = com.histmersion.api.instance.UiConfig.WriteKeyRequired(true),
        agentsEnabled = com.histmersion.api.instance.UiConfig.AgentsEnabled(true),
        ui.tilesTone,
        PluginsReport(),
        ui.docsUrl,
        baseMapSource = com.histmersion.api.instance.UiConfig.BaseMapSource("local"),
        mapLanguage = com.histmersion.api.instance.UiConfig.MapLanguage("de"),
        com.histmersion.api.instance
          .MapStartConfig(ui.mapStart.mode, ui.mapStart.lat, ui.mapStart.lon, ui.mapStart.zoom, ui.mapStart.label),
        setupDue = com.histmersion.api.instance.UiConfig.SetupDue(true)
      )
    )
