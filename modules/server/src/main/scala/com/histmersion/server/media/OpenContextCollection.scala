package com.histmersion.server.media

import com.histmersion.pluginhost.{allowed, PluginMedia, Polite}

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.db.MediaRepo.PlaceFacts
import com.histmersion.values.MediaRole

/** Open Context: one search by the place's name answers the records with their licences, creators, subjects and spatial
  * coverage; a record to store is completed from its media item. A record is tied by a shared gazetteer id first
  * ([[MediaMatch.withIds]]), else by its context path's names.
  */
final class OpenContextCollection(val conf: MediaCollectionConfig) extends Collection:
  val provider = MediaProvider.OpenContext
  val role = MediaRole.Object
  def search(fetch: Polite, place: PlaceFacts): Either[String, List[Either[String, MediaRecord]]] =
    fetch.json(OpenContext.searchUrl(conf.apiUrl, place.label, conf.candidates)).flatMap(OpenContext.records)
  override def usesIds = true
  override def matchesWithIds(
      rec: MediaRecord,
      place: MediaMatch.PlaceInfo,
      resolve: MediaMatch.Resolve,
      resolveId: MediaMatch.ResolveId
  ) = MediaMatch.withIds(rec, place, resolveId, resolve, Set(OpenContext.Context))
  override def complete(fetch: Polite, rec: MediaRecord): Either[String, MediaRecord] =
    fetch.json(OpenContext.itemUrl(conf.apiUrl, rec.key)).flatMap(OpenContext.item(_, rec))
