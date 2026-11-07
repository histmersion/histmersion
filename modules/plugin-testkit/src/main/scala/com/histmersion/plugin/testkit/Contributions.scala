package com.histmersion.plugin.testkit

import com.histmersion.plugin.*
import com.histmersion.pluginhost.Registries

/** What the plugin registered, by extension point, each under the id the server lists it by. */
final case class Contributions(
    sourceFetchers: List[Registries.SourceFetchersKey],
    mediaCollections: List[Registries.MediaCollectionsKey],
    eventCatalogues: List[Registries.EventCataloguesKey],
    aiProviders: List[Registries.AiProvidersKey],
    secretVaults: List[Registries.SecretVaultsKey]
):
  /** As `histmersion plugin add` and `histmersion about` show them: `source-fetcher:oapen`, … */
  def provides: List[Registries.RegistryKey] =
    sourceFetchers.map(k => Registries.RegistryKey("source-fetcher:" + k.value)) ++
      mediaCollections.map(k => Registries.RegistryKey("media-collection:" + k.value)) ++
      eventCatalogues.map(k => Registries.RegistryKey("event-catalogue:" + k.value)) ++
      aiProviders.map(k => Registries.RegistryKey("ai-provider:" + k.value)) ++
      secretVaults.map(k => Registries.RegistryKey("secret-vault:" + k.value))
