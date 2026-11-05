package com.histmersion.plugins.media

/** `media-met`: `histmersion media import met` — The Metropolitan Museum of Art's Open Access objects. */
import com.histmersion.plugin.Plugin
final class MetPlugin extends MediaPlugin("media-met"):
  type Config = MediaPlugin.Config
  def register(c: MediaPlugin.Config, ctx: Plugin.Context): Either[com.histmersion.plugins.media.MetPlugin.RegisterResultPart1, Unit] =
    ctx.mediaCollections.add("met", MetCollection(c.media.met))

object MetPlugin:
  type RegisterResultPart1 = RegisterResultPart1.T
  object RegisterResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
