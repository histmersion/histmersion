package com.histmersion.plugins.media

/** `media-pleiades`: `histmersion media import pleiades` — the Commons files Pleiades place records link. */
import com.histmersion.plugin.Plugin
final class PleiadesPlugin extends MediaPlugin("media-pleiades"):
  type Config = MediaPlugin.Config
  def register(c: MediaPlugin.Config, ctx: Plugin.Context): Either[com.histmersion.plugins.media.PleiadesPlugin.RegisterResultPart1, Unit] =
    ctx.mediaCollections.add("pleiades", PleiadesPictures(c.media, ctx.licences))

object PleiadesPlugin:
  type RegisterResultPart1 = RegisterResultPart1.T
  object RegisterResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
