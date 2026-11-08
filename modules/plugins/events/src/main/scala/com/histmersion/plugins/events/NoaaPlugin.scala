package com.histmersion
package plugins.events

/** `events-noaa`: `histmersion events import noaa` — NOAA NCEI's significant earthquakes, eruptions and tsunamis. */
import com.histmersion.plugin.Plugin
import com.histmersion.plugin.Registry
final class NoaaPlugin extends EventsPlugin("events-noaa"):
  type Config = NoaaConfig
  def register(c: NoaaConfig, ctx: Plugin.Context): Either[Plugin.RegisterResultPart1, Unit] =
    ctx.eventCatalogues
      .add(Registry.AddId("noaa"), NoaaCatalogue(c.events.noaa))
      .left
      .map(e => Plugin.RegisterResultPart1(e.value))
