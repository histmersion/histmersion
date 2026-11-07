package com.histmersion.plugins.events

/** `events-noaa`: `histmersion events import noaa` — NOAA NCEI's significant earthquakes, eruptions and tsunamis. */
import com.histmersion.plugin.Plugin
final class NoaaPlugin extends EventsPlugin("events-noaa"):
  type Config = NoaaConfig
  def register(c: NoaaConfig, ctx: Plugin.Context): Either[com.histmersion.plugins.events.NoaaPlugin.Problem, Unit] =
    ctx.eventCatalogues.add(com.histmersion.plugin.Registry.AddId("noaa"), NoaaCatalogue(c.events.noaa)).left.map(e => NoaaPlugin.Problem(e.value))

object NoaaPlugin:
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
