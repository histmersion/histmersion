package com.histmersion.pluginhost

import com.histmersion.files.*
import com.histmersion.plugin.*

import scala.collection.immutable.ListMap
import scala.jdk.CollectionConverters.*

/** What the registered plugins contribute, one map per extension point, in registration order. Read by the rest of the
  * app; an immutable snapshot of one [[Generation]].
  */
final case class Registries(
    secretVaults: ListMap[String, SecretVault] = ListMap.empty,
    sourceFetchers: ListMap[String, SourceFetcher] = ListMap.empty,
    mediaCollections: ListMap[String, MediaCollection] = ListMap.empty,
    eventCatalogues: ListMap[String, EventCatalogue] = ListMap.empty,
    aiProviders: ListMap[String, AiProvider] = ListMap.empty,
    gazetteerSources: ListMap[String, GazetteerSource] = ListMap.empty
):
  def provides: List[String] =
    secretVaults.keys.toList.map(k => s"${Registries.SecretVault}:$k") ++
      sourceFetchers.keys.toList.map(k => s"${Registries.SourceFetcher}:$k") ++
      mediaCollections.keys.toList.map(k => s"${Registries.MediaCollection}:$k") ++
      eventCatalogues.keys.toList.map(k => s"${Registries.EventCatalogue}:$k") ++
      aiProviders.keys.toList.map(k => s"${Registries.AiProvider}:$k") ++
      gazetteerSources.keys.toList.map(k => s"${Registries.GazetteerSource}:$k")

  /** Every registry, the other's entries after this one's. */
  def ++(other: Registries): Registries =
    Registries(
      secretVaults ++ other.secretVaults,
      sourceFetchers ++ other.sourceFetchers,
      mediaCollections ++ other.mediaCollections,
      eventCatalogues ++ other.eventCatalogues,
      aiProviders ++ other.aiProviders,
      gazetteerSources ++ other.gazetteerSources
    )

object Registries:
  val SecretVault = "secret-vault"
  val SourceFetcher = "source-fetcher"
  val MediaCollection = "media-collection"
  val EventCatalogue = "event-catalogue"
  val AiProvider = "ai-provider"
  val GazetteerSource = "gazetteer-source"
  val empty: Registries = Registries()
