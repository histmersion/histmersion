package com.histmersion.plugins.media

/** `media-loc`: `histmersion media import loc` — the Library of Congress's public-domain maps. */
import com.histmersion.plugin.Plugin
final class LocPlugin extends MediaPlugin("media-loc"):
  type Config = MediaPlugin.Config
  def register(c: MediaPlugin.Config, ctx: Plugin.Context): Either[com.histmersion.plugins.media.LocPlugin.RegisterResultPart1, Unit] =
    ctx.mediaCollections.add("loc", LocCollection(c.media.loc, c.media.thumbPx))

object LocPlugin:
  type RegisterResultPart1 = RegisterResultPart1.T
  object RegisterResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
