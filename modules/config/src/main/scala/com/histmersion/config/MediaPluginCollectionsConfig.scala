package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader

/** The media collections PLUGINS register (`media.collections`, docs/research-plugins.md §6): the licence families
  * their pictures may be stored under. The host judges each record's stated licence against this — never against
  * anything the plugin says — so a plugin cannot widen it; the user can, per collection (`per-collection.<id>`, which
  * replaces `licences` for that one). The built-in collections keep their own `media.<provider>.licences`.
  */
final case class MediaPluginCollectionsConfig(licences: String, perCollection: Map[String, String])
    derives ConfigReader:
  /** The allowlist as written for `collection` (what a report quotes). */
  def licencesFor(collection: String): String = perCollection.getOrElse(collection, licences)
