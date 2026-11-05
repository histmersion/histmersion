package com.histmersion.plugins.events

/** `events-gvp`: `histmersion events import gvp` — the Smithsonian Global Volcanism Program's Holocene eruptions. */
import com.histmersion.plugin.Plugin
final class GvpPlugin extends EventsPlugin("events-gvp"):
  type Config = GvpConfig
  def register(c: GvpConfig, ctx: Plugin.Context): Either[com.histmersion.plugins.events.GvpPlugin.RegisterResultPart1, Unit] =
    ctx.eventCatalogues.add("gvp", GvpCatalogue(c.events))

object GvpPlugin:
  type RegisterResultPart1 = RegisterResultPart1.T
  object RegisterResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
