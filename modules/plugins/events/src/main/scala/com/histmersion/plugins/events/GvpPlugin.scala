package com.histmersion
package plugins.events
import com.histmersion.util.problem.Problem

/** `events-gvp`: `histmersion events import gvp` — the Smithsonian Global Volcanism Program's Holocene eruptions. */
import com.histmersion.plugin.Plugin
import com.histmersion.plugin.Registry
final class GvpPlugin extends EventsPlugin("events-gvp"):
  type Config = GvpConfig
  def register(c: GvpConfig, ctx: Plugin.Context): Either[Problem, Unit] =
    ctx.eventCatalogues
      .add(Registry.AddId("gvp"), GvpCatalogue(c.events))
