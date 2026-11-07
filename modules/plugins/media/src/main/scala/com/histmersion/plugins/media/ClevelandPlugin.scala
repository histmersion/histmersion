package com.histmersion.plugins.media

/** `media-cleveland`: `histmersion media import cleveland` — The Cleveland Museum of Art's Open Access objects. */
import com.histmersion.plugin.Plugin
final class ClevelandPlugin extends MediaPlugin("media-cleveland"):
  type Config = MediaPlugin.Config
  def register(c: MediaPlugin.Config, ctx: Plugin.Context): Either[com.histmersion.plugins.media.ClevelandPlugin.Problem, Unit] =
    ctx.mediaCollections.add(com.histmersion.plugin.Registry.AddId("cleveland"), ClevelandCollection(c.media.cleveland))

object ClevelandPlugin:
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
