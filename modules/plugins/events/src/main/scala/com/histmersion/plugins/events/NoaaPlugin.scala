package com.histmersion.plugins.events

/** `events-noaa`: `histmersion events import noaa` — NOAA NCEI's significant earthquakes, eruptions and tsunamis. */
import com.histmersion.plugin.Plugin
final class NoaaPlugin extends EventsPlugin("events-noaa"):
  type Config = NoaaConfig
  def register(c: NoaaConfig, ctx: Plugin.Context): Either[com.histmersion.plugins.events.NoaaPlugin.RegisterResultPart1, Unit] =
    ctx.eventCatalogues.add("noaa", NoaaCatalogue(c.events.noaa))

object NoaaPlugin:
  type RegisterResultPart1 = RegisterResultPart1.T
  object RegisterResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
