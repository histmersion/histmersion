package com.histmersion.server

import com.histmersion.api.{ConfigFileInfo, DebounceConfig, EventsUiConfig, PluginsReport, TimeConfig, UiConfig}
import com.histmersion.config.{AppConfig, UserConfigFile}

import java.nio.file.Path

class ConfigViewsSuite extends munit.FunSuite:

  test("the page's settings: every `ui` setting as read, and what the server adds"):
    val ui   = AppConfig.load().ui
    val page = ConfigViews.page(ui, writeKeyRequired = true, agentsEnabled = true, plugins = PluginsReport(),
      baseMapSource = "local", mapLanguage = "de")
    assertEquals(page, UiConfig(ui.tiles, ui.tilesMaxZoom, ui.markerRadius, ui.viewportDebounceMs, ui.pageSize,
      ui.facetSuggestions, ui.treeDepth, ui.treeSamples, ui.maxBuckets,
      DebounceConfig(ui.debounce.suggestMs, ui.debounce.searchMs, ui.debounce.reloadMs, ui.debounce.previewMs),
      TimeConfig(ui.time.min, ui.time.max, ui.time.defaultFrom, ui.time.defaultTo),
      ui.narrowMaxWidthPx, ui.narrowMaxHeightPx, ui.tabletMaxWidthPx,
      EventsUiConfig(ui.events.localFadeSeconds, ui.events.regionalFadeSeconds, ui.events.mapLimit,
        ui.events.outlineToleranceDeg),
      ui.countersPollSeconds, ui.jobPollMs, writeKeyRequired = true, agentsEnabled = true, ui.tilesTone,
      PluginsReport(), ui.docsUrl, baseMapSource = "local", mapLanguage = "de"))

  test("the config file's line: its path as text, whether it was read, the keys it ignored"):
    val f = UserConfigFile(Some(Path.of("/c/histmersion.conf")), loaded = true, List("histmersion.x"))
    assertEquals(f.info, ConfigFileInfo(Some("/c/histmersion.conf"), loaded = true, List("histmersion.x")))
    assertEquals(UserConfigFile(None, loaded = false).info, ConfigFileInfo(None, loaded = false, Nil))
