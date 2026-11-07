package com.histmersion.plugins.media

/** `media-opencontext`: `histmersion media import opencontext` — Open Context's excavation media. */
import com.histmersion.plugin.Plugin
final class OpenContextPlugin extends MediaPlugin("media-opencontext"):
  type Config = MediaPlugin.Config
  def register(c: MediaPlugin.Config, ctx: Plugin.Context): Either[com.histmersion.plugins.media.OpenContextPlugin.Problem, Unit] =
    ctx.mediaCollections.add("opencontext", OpenContextCollection(c.media.openContext, ctx.licences))

object OpenContextPlugin:
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
