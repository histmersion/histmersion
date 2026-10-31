package com.histmersion.plugins.events

import com.histmersion.plugin.PluginContext

/** `events-gvp`: `histmersion events import gvp` — the Smithsonian Global Volcanism Program's Holocene eruptions. */
final class GvpPlugin extends EventsPlugin("events-gvp"):
  type Config = GvpConfig
  def register(c: GvpConfig, ctx: PluginContext): Either[String, Unit] =
    ctx.eventCatalogues.add("gvp", GvpCatalogue(c.events))
