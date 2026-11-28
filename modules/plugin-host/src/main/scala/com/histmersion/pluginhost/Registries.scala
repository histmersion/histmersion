package com.histmersion
package pluginhost

import com.histmersion.plugin.*

import scala.collection.immutable.ListMap
import scala.jdk.CollectionConverters.*
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}
import com.histmersion.util.plugins.Plugin as PluginRecord

/** What the registered plugins contribute, one map per extension point, in registration order. Read by the rest of the
  * app; an immutable snapshot of one [[Generation]].
  */
final case class Registries(
    secretVaults: ListMap[Registries.SecretVaultsKey, Secret.Vault] = ListMap.empty,
    sourceFetchers: ListMap[Registries.SourceFetchersKey, SourceFetcher] = ListMap.empty,
    mediaCollections: ListMap[Registries.MediaCollectionsKey, MediaCollection] = ListMap.empty,
    eventCatalogues: ListMap[Registries.EventCataloguesKey, EventCatalogue] = ListMap.empty,
    aiProviders: ListMap[Registries.AiProvidersKey, AiProvider] = ListMap.empty,
    gazetteerSources: ListMap[Registries.GazetteerSourcesKey, GazetteerSource] = ListMap.empty,
    sourceCollections: ListMap[Registries.SourceCollectionsKey, SourceCollection] = ListMap.empty
):
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def provides: List[Registries.RegistryKey] =
    secretVaults.keys.toList.map(k => Registries.RegistryKey(s"${Registries.SecretVault}:$k")) ++
      sourceFetchers.keys.toList.map(k => Registries.RegistryKey(s"${Registries.SourceFetcher}:$k")) ++
      mediaCollections.keys.toList.map(k => Registries.RegistryKey(s"${Registries.MediaCollection}:$k")) ++
      eventCatalogues.keys.toList.map(k => Registries.RegistryKey(s"${Registries.EventCatalogue}:$k")) ++
      aiProviders.keys.toList.map(k => Registries.RegistryKey(s"${Registries.AiProvider}:$k")) ++
      gazetteerSources.keys.toList.map(k => Registries.RegistryKey(s"${Registries.GazetteerSource}:$k")) ++
      sourceCollections.keys.toList.map(k => Registries.RegistryKey(s"${Registries.SourceCollection}:$k"))

  /** Every registry, the other's entries after this one's. */
  def ++(other: Registries): Registries =
    Registries(
      secretVaults ++ other.secretVaults,
      sourceFetchers ++ other.sourceFetchers,
      mediaCollections ++ other.mediaCollections,
      eventCatalogues ++ other.eventCatalogues,
      aiProviders ++ other.aiProviders,
      gazetteerSources ++ other.gazetteerSources,
      sourceCollections ++ other.sourceCollections
    )

object Registries:
  type RegistryKey = RegistryKey.T
  object RegistryKey extends Newtype[String, Pure]

  type SecretVaultsKey = SecretVaultsKey.T
  object SecretVaultsKey extends Newtype[String, Pure]
  type SourceFetchersKey = SourceFetchersKey.T
  object SourceFetchersKey extends Newtype[String, Pure]
  type MediaCollectionsKey = MediaCollectionsKey.T
  object MediaCollectionsKey extends Newtype[String, Pure]
  type EventCataloguesKey = EventCataloguesKey.T
  object EventCataloguesKey extends Newtype[String, Pure]
  type AiProvidersKey = AiProvidersKey.T
  object AiProvidersKey extends Newtype[String, Pure]:
    extension (key: AiProvidersKey) def pluginId: PluginRecord.Id = PluginRecord.Id.assume(key.value)
  type GazetteerSourcesKey = GazetteerSourcesKey.T
  object GazetteerSourcesKey extends Newtype[String, Pure]
  type SourceCollectionsKey = SourceCollectionsKey.T
  object SourceCollectionsKey extends Newtype[String, Pure]

  val SecretVault = "secret-vault"
  val SourceFetcher = "source-fetcher"
  val MediaCollection = "media-collection"
  val EventCatalogue = "event-catalogue"
  val AiProvider = "ai-provider"
  val GazetteerSource = "gazetteer-source"
  val SourceCollection = "source-collection"
  val empty: Registries = Registries()
