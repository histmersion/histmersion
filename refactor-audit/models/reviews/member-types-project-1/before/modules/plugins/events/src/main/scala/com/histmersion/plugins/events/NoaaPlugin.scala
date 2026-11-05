package com.histmersion.plugins.events

/** `events-noaa`: `histmersion events import noaa` — NOAA NCEI's significant earthquakes, eruptions and tsunamis. */
import com.histmersion.plugin.Plugin
final class NoaaPlugin extends EventsPlugin("events-noaa"):
  type Config = NoaaConfig
  def register(c: NoaaConfig, ctx: Plugin.Context): Either[String, Unit] =
    ctx.eventCatalogues.add("noaa", NoaaCatalogue(c.events.noaa))
