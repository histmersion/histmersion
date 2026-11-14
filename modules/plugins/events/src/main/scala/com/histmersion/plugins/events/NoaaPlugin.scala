package com.histmersion
package plugins.events
import com.histmersion.util.problem.{Problem, Problems}

/** `events-noaa`: `histmersion events import noaa` — NOAA NCEI's significant earthquakes, eruptions and tsunamis. */
import com.histmersion.plugin.Plugin
import com.histmersion.plugin.Registry
final class NoaaPlugin extends EventsPlugin("events-noaa"):
  type Config = NoaaConfig
  def register(c: NoaaConfig, ctx: Plugin.Context): Either[Problems, Unit] =
    ctx.eventCatalogues
      .add(Registry.AddId("noaa"), NoaaCatalogue(c.events.noaa))
