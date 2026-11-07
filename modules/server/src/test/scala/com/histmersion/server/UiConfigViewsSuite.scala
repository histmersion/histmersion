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
          com.histmersion.api.catalogue.Year(ui.time.min.value),
          com.histmersion.api.catalogue.Year(ui.time.max.value),
          com.histmersion.api.catalogue.Year(ui.time.defaultFrom.value),
          com.histmersion.api.catalogue.Year(ui.time.defaultTo.value)
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
        writeKeyRequired = com.histmersion.api.instance.UiConfig.WriteKeyRequired(true),
        agentsEnabled = com.histmersion.api.instance.UiConfig.AgentsEnabled(true),
        UiConfig.TilesTone(ui.tilesTone.value),
        PluginsReport(),
        UiConfig.DocsUrl(ui.docsUrl.value),
        baseMapSource = com.histmersion.api.instance.UiConfig.BaseMapSource("local"),
        mapLanguage = com.histmersion.api.instance.UiConfig.MapLanguage("de"),
        com.histmersion.api.instance
          .MapStartConfig(
            com.histmersion.api.instance.MapStartMode.Wire(ui.mapStart.mode.value),
            com.histmersion.api.catalogue.Latitude(ui.mapStart.lat.value),
            com.histmersion.api.catalogue.Longitude(ui.mapStart.lon.value),
            com.histmersion.api.instance.MapStartConfig.Zoom(ui.mapStart.zoom.value),
            com.histmersion.api.instance.MapStartConfig.Label(ui.mapStart.label.value)
          ),
        setupDue = com.histmersion.api.instance.UiConfig.SetupDue(true)
      )
    )
