package com.histmersion.server.media

import com.histmersion.pluginhost.{allowed, PluginMedia, Polite}

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.db.MediaRepo.PlaceFacts
import com.histmersion.values.MediaRole

/** The Met: a `/v1.1/search` by the place's name (as `q` and `geoLocation`), then each object. */
final class MetCollection(val conf: MediaCollectionConfig) extends Collection:
  val provider = MediaProvider.Met
  val role = MediaRole.Object
  def search(fetch: Polite, place: PlaceFacts): Either[String, List[Either[String, MediaRecord]]] =
    fetch
      .json(MetMuseum.searchUrl(conf.apiUrl, place.label, conf.candidates))
      .flatMap(MetMuseum.ids)
      .map(
        _.take(conf.candidates.max(0)).map(id =>
          fetch.json(MetMuseum.objectUrl(conf.apiUrl, id)).flatMap(MetMuseum.record).left.map(e => s"object $id: $e")
        )
      )
