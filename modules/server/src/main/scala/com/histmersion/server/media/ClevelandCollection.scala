package com.histmersion.server.media

import com.histmersion.pluginhost.{allowed, PluginMedia, Polite}

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.db.MediaRepo.PlaceFacts
import com.histmersion.values.MediaRole

/** Cleveland: one search answers the records. */
final class ClevelandCollection(val conf: MediaCollectionConfig) extends Collection:
  val provider = MediaProvider.Cleveland
  val role = MediaRole.Object
  def search(fetch: Polite, place: PlaceFacts): Either[String, List[Either[String, MediaRecord]]] =
    fetch.json(ClevelandArt.searchUrl(conf.apiUrl, place.label, conf.candidates)).flatMap(ClevelandArt.records)
