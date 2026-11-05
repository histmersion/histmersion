package com.histmersion.plugins.media

/** `media-depicts`: `histmersion media import depicts` — Commons files that depict events. */
import com.histmersion.plugin.Plugin
final class DepictsPlugin extends MediaPlugin("media-depicts"):
  type Config = MediaPlugin.Config
  def register(c: MediaPlugin.Config, ctx: Plugin.Context): Either[com.histmersion.plugins.media.DepictsPlugin.RegisterResultPart1, Unit] =
    ctx.mediaCollections.add("depicts", Commons.Depictions(c.media, ctx.licences))

object DepictsPlugin:
  type RegisterResultPart1 = RegisterResultPart1.T
  object RegisterResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
