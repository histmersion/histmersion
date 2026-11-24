package com.histmersion
package plugins.media

/** `media-europeana`: `histmersion media import europeana` — Europeana's openly licensed images, with a personal API
  * key.
  */
import com.histmersion.plugin.Plugin
import com.histmersion.lint.{LintRule, tolerated}
@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
final class EuropeanaPlugin extends KeyedPlugin(KeyedPlugin.Id("media-europeana"), KeyedPlugin.Name("europeana")):
  protected def collection(c: KeyedPlugin.Config, key: Option[KeyedCollection.Key], ctx: Plugin.Context) =
    EuropeanaCollection(c.media.europeana, key, ctx.licences)
