package com.histmersion.plugins.events

import com.histmersion.plugin.PluginContext

/** `events-noaa`: `histmersion events import noaa` — NOAA NCEI's significant earthquakes, eruptions and tsunamis. */
final class NoaaPlugin extends EventsPlugin("events-noaa"):
  type Config = NoaaConfig
  def register(c: NoaaConfig, ctx: PluginContext): Either[String, Unit] =
    ctx.eventCatalogues.add("noaa", NoaaCatalogue(c.events.noaa))
