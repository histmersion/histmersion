package com.histmersion.plugins.media

/** `media-pleiades`: `histmersion media import pleiades` — the Commons files Pleiades place records link. */
import com.histmersion.plugin.Plugin
final class PleiadesPlugin extends MediaPlugin("media-pleiades"):
  type Config = MediaPlugin.Config
  def register(c: MediaPlugin.Config, ctx: Plugin.Context): Either[com.histmersion.plugins.media.PleiadesPlugin.Problem, Unit] =
    ctx.mediaCollections.add(com.histmersion.plugin.Registry.AddId("pleiades"), PleiadesPictures(c.media, ctx.licences))

object PleiadesPlugin:
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
