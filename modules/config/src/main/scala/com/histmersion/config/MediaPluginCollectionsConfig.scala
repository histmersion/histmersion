package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader

/** The media collections PLUGINS register (`media.collections`, docs/research-plugins.md §6): the licence families
  * their pictures may be stored under. The host judges each record's stated licence against this — never against
  * anything the plugin says — so a plugin cannot widen it; the user can, per collection (`per-collection.<id>`, which
  * replaces `licences` for that one). The built-in collections keep their own `media.<provider>.licences`.
  */
final case class MediaPluginCollectionsConfig(licences: MediaPluginCollectionsConfig.Licences, perCollection: Map[MediaPluginCollectionsConfig.PerCollectionKey, MediaPluginCollectionsConfig.PerCollectionValue])
    derives ConfigReader:
  /** The allowlist as written for `collection` (what a report quotes). */
  def licencesFor(collection: com.histmersion.config.MediaPluginCollectionsConfig.PerCollectionKey): com.histmersion.config.MediaPluginCollectionsConfig.Licences = perCollection.getOrElse(collection, licences)

object MediaPluginCollectionsConfig:

  type Licences = Licences.T
  object Licences extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PerCollectionKey = PerCollectionKey.T
  object PerCollectionKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PerCollectionValue = PerCollectionValue.T
  object PerCollectionValue extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
