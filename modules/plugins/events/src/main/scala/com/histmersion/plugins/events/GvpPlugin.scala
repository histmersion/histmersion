package com.histmersion.plugins.events

/** `events-gvp`: `histmersion events import gvp` — the Smithsonian Global Volcanism Program's Holocene eruptions. */
import com.histmersion.plugin.Plugin
final class GvpPlugin extends EventsPlugin("events-gvp"):
  type Config = GvpConfig
  def register(c: GvpConfig, ctx: Plugin.Context): Either[String, Unit] =
    ctx.eventCatalogues.add("gvp", GvpCatalogue(c.events))
