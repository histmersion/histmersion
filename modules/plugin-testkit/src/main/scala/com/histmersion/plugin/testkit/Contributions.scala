package com.histmersion.plugin.testkit

import com.histmersion.plugin.*

/** What the plugin registered, by extension point, each under the id the server lists it by. */
final case class Contributions(
    sourceFetchers: List[Contributions.SourceFetchers],
    mediaCollections: List[Contributions.MediaCollections],
    eventCatalogues: List[Contributions.EventCatalogues],
    aiProviders: List[Contributions.AiProviders],
    secretVaults: List[Contributions.SecretVaults]
):
  /** As `histmersion plugin add` and `histmersion about` show them: `source-fetcher:oapen`, … */
  def provides: List[String] =
    sourceFetchers.map("source-fetcher:" + _) ++ mediaCollections.map("media-collection:" + _) ++
      eventCatalogues.map("event-catalogue:" + _) ++ aiProviders.map("ai-provider:" + _) ++
      secretVaults.map("secret-vault:" + _)

object Contributions:
  type SourceFetchers = SourceFetchers.T
  object SourceFetchers extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MediaCollections = MediaCollections.T
  object MediaCollections extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EventCatalogues = EventCatalogues.T
  object EventCatalogues extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AiProviders = AiProviders.T
  object AiProviders extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SecretVaults = SecretVaults.T
  object SecretVaults extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
