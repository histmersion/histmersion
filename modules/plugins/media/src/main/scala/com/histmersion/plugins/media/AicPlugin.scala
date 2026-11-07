package com.histmersion.plugins.media

/** `media-aic`: `histmersion media import aic` — The Art Institute of Chicago's public-domain works. */
import com.histmersion.plugin.Plugin
final class AicPlugin extends MediaPlugin("media-aic"):
  type Config = MediaPlugin.Config
  def register(c: MediaPlugin.Config, ctx: Plugin.Context): Either[com.histmersion.plugins.media.AicPlugin.Problem, Unit] =
    ctx.mediaCollections.add("aic", ArtInstituteCollection(c.media.aic, c.media.thumbPx))

object AicPlugin:
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
