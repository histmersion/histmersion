package com.histmersion
package plugins.media

import com.histmersion.plugin.Problem

/** `media-depicts`: `histmersion media import depicts` — Commons files that depict events. */
import com.histmersion.plugin.Plugin
import com.histmersion.plugin.Registry
final class DepictsPlugin extends MediaPlugin("media-depicts"):
  type Config = MediaPlugin.Config
  def register(c: MediaPlugin.Config, ctx: Plugin.Context): Either[Problem, Unit] =
    ctx.mediaCollections
      .add(Registry.AddId("depicts"), Commons.Depictions(c.media, ctx.licences))
      .left
      .map(e => Problem(e.value))
