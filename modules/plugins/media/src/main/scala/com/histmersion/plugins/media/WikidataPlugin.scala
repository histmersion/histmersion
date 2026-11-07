package com.histmersion.plugins.media

/** `media-wikidata`: `histmersion media import wikidata` — pictures of places and events from their Wikidata items. */
import com.histmersion.plugin.Plugin
final class WikidataPlugin extends MediaPlugin("media-wikidata"):
  type Config = MediaPlugin.Config
  def register(c: MediaPlugin.Config, ctx: Plugin.Context): Either[com.histmersion.plugins.media.WikidataPlugin.Problem, Unit] =
    ctx.mediaCollections.add(com.histmersion.plugin.Registry.AddId("wikidata"), WikidataPictures(c.media, ctx.licences))

object WikidataPlugin:
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
