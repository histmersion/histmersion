package com.histmersion.plugin.testkit

import com.histmersion.plugin.*

/** What the plugin registered, by extension point, each under the id the server lists it by. */
final case class Contributions(
    sourceFetchers: List[String],
    mediaCollections: List[String],
    eventCatalogues: List[String],
    aiProviders: List[String],
    secretVaults: List[String]
):
  /** As `histmersion plugin add` and `histmersion about` show them: `source-fetcher:oapen`, … */
  def provides: List[String] =
    sourceFetchers.map("source-fetcher:" + _) ++ mediaCollections.map("media-collection:" + _) ++
      eventCatalogues.map("event-catalogue:" + _) ++ aiProviders.map("ai-provider:" + _) ++
      secretVaults.map("secret-vault:" + _)
