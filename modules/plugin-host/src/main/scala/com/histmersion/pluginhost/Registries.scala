package com.histmersion
package pluginhost

import com.histmersion.plugin.*

import scala.collection.immutable.ListMap
import scala.jdk.CollectionConverters.*
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

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
  object RegistryKey extends RefinedType[String, Pure]

  type SecretVaultsKey = SecretVaultsKey.T
  object SecretVaultsKey extends RefinedType[String, Pure]
  type SourceFetchersKey = SourceFetchersKey.T
  object SourceFetchersKey extends RefinedType[String, Pure]
  type MediaCollectionsKey = MediaCollectionsKey.T
  object MediaCollectionsKey extends RefinedType[String, Pure]
  type EventCataloguesKey = EventCataloguesKey.T
  object EventCataloguesKey extends RefinedType[String, Pure]
  type AiProvidersKey = AiProvidersKey.T
  object AiProvidersKey extends RefinedType[String, Pure]
  type GazetteerSourcesKey = GazetteerSourcesKey.T
  object GazetteerSourcesKey extends RefinedType[String, Pure]
  type SourceCollectionsKey = SourceCollectionsKey.T
  object SourceCollectionsKey extends RefinedType[String, Pure]

  val SecretVault = "secret-vault"
  val SourceFetcher = "source-fetcher"
  val MediaCollection = "media-collection"
  val EventCatalogue = "event-catalogue"
  val AiProvider = "ai-provider"
  val GazetteerSource = "gazetteer-source"
  val SourceCollection = "source-collection"
  val empty: Registries = Registries()
