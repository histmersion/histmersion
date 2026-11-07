package com.histmersion.plugins.media

/** `media-met`: `histmersion media import met` — The Metropolitan Museum of Art's Open Access objects. */
import com.histmersion.plugin.Plugin
final class MetPlugin extends MediaPlugin("media-met"):
  type Config = MediaPlugin.Config
  def register(c: MediaPlugin.Config, ctx: Plugin.Context): Either[com.histmersion.plugins.media.MetPlugin.Problem, Unit] =
    ctx.mediaCollections.add("met", MetCollection(c.media.met))

object MetPlugin:
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
