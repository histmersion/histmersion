package com.histmersion
package config

import com.histmersion.config.NewtypeReaders.given
import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.constraint.any.Pure

/** The media collections PLUGINS register (`media.collections`, docs/research-plugins.md §6): the licence families
  * their pictures may be stored under. The host judges each record's stated licence against this — never against
  * anything the plugin says — so a plugin cannot widen it; the user can, per collection (`per-collection.<id>`, which
  * replaces `licences` for that one). The built-in collections keep their own `media.<provider>.licences`.
  */
final case class MediaPluginCollectionsConfig(
    licences: MediaPluginCollectionsConfig.Licences,
    perCollection: Map[MediaPluginCollectionsConfig.PerCollectionKey, MediaPluginCollectionsConfig.Licences]
) derives ConfigReader:
  /** The allowlist as written for `collection` (what a report quotes). */
  def licencesFor(collection: MediaPluginCollectionsConfig.PerCollectionKey): MediaPluginCollectionsConfig.Licences =
    perCollection.getOrElse(collection, licences)

object MediaPluginCollectionsConfig:

  type Licences = Licences.T
  object Licences extends Newtype[String, Pure]
  type PerCollectionKey = PerCollectionKey.T
  object PerCollectionKey extends Newtype[String, Pure]:
    given ConfigReader[Map[PerCollectionKey, Licences]] =
      ConfigReader.derived[Map[String, Licences]].emap { byName =>
        byName.foldLeft[Either[String, Map[PerCollectionKey, Licences]]](Right(Map.empty)) { case (acc, (name, v)) =>
          for
            done <- acc
            k <- PerCollectionKey.either(name)
          yield done.updated(k, v)
        }
      }
