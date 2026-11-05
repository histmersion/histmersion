package com.histmersion.plugins.media

/** `media-smithsonian`: `histmersion media import smithsonian` — Smithsonian Open Access objects, with an api.data.gov
  * key.
  */
import com.histmersion.plugin.Plugin
final class SmithsonianPlugin extends KeyedPlugin("media-smithsonian", "smithsonian"):
  protected def collection(c: KeyedPlugin.Config, key: Option[com.histmersion.plugins.media.SmithsonianPlugin.CollectionKey], ctx: Plugin.Context) =
    SmithsonianCollection(c.media.smithsonian, key)

object SmithsonianPlugin:
  type CollectionKey = CollectionKey.T
  object CollectionKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
