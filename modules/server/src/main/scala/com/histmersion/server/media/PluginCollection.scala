package com.histmersion.server.media

import com.histmersion.pluginhost.{PluginMedia, Polite, SpiValues}

import com.histmersion.config.MediaCollectionConfig
import com.histmersion.domain.{MediaMatch, MediaRecord}
import com.histmersion.db.MediaRepo.PlaceFacts
import com.histmersion.plugin.*

/** A collection searched by a place's name, run by the host's own collection import ([[CollectionImport]]): the plugin
  * searches and describes; the host reads each stated licence ([[PluginMedia.rights]]) and judges it against `allowed`
  * — the collection's allowlist, never anything the plugin says —, ties records to places by [[MediaMatch]] under the
  * rule the collection chose, downloads, thumbnails and stores. One per import (it keeps the run's items for
  * [[complete]]).
  *
  * @param provider
  *   the name its checks are recorded and its pictures stored under
  */
final class PluginCollection(val provider: String, col: MediaCollection, allowed: String) extends Collection:
  val role: String = col.role
  private val latest = col.latestYear.map(_.value)
  val conf: MediaCollectionConfig =
    MediaCollectionConfig(
      enabled = true,
      apiUrl = "",
      imageUrl = "",
      candidates = 0,
      perPlace = col.perPlace,
      requestIntervalMs = col.requestIntervalMs,
      displayPx = 0,
      maxYear = PluginCollection.maxYear(col.matchRule),
      licences = allowed
    )

  // the items as the plugin returned them, by key, for `complete` — one import run's own state
  private val items = scala.collection.concurrent.TrieMap.empty[String, MediaItem]

  def search(fetch: Polite, place: PlaceFacts): Either[String, List[Either[String, MediaRecord]]] =
    PluginMedia
      .guard(col.search(PluginCollection.place(place), PluginMedia.http(fetch)))
      .map(_.map(_.map { it =>
        items.update(it.key, it)
        PluginMedia.record(it)
      }))

  override def matches(rec: MediaRecord, place: MediaMatch.PlaceInfo, resolve: MediaMatch.Resolve) =
    col.matchRule match
      case MediaMatchRule.Map(maxYear) => MediaMatch.map(rec, place, resolve, maxYear.value)
      case _                           => MediaMatch.museum(rec, place, resolve, latest)

  override def usesIds: Boolean = col.matchRule.isInstanceOf[MediaMatchRule.PlaceIds]

  override def matchesWithIds(
      rec: MediaRecord,
      place: MediaMatch.PlaceInfo,
      resolve: MediaMatch.Resolve,
      resolveId: MediaMatch.ResolveId
  ) =
    col.matchRule match
      case MediaMatchRule.PlaceIds(deeper) => MediaMatch.withIds(rec, place, resolveId, resolve, deeper, latest)
      case _                               => matches(rec, place, resolve)

  override def complete(fetch: Polite, rec: MediaRecord): Either[String, MediaRecord] =
    items.get(rec.key) match
      case None     => Right(rec)
      case Some(it) => PluginMedia.guard(col.complete(it, PluginMedia.http(fetch))).map(PluginMedia.record)

object PluginCollection:

  def maxYear(rule: MediaMatchRule): Int = rule match
    case MediaMatchRule.Map(y) => y.value
    case _                     => 0

  /** The place as a plugin sees it: what the store holds, each value as its type (one that is not left out). Pure. */
  def place(p: PlaceFacts): MediaPlace =
    MediaPlace(
      p.id,
      p.label,
      p.qid.flatMap(SpiValues.wikidata),
      p.lat.flatMap(SpiValues.latitude),
      p.lon.flatMap(SpiValues.longitude),
      p.period.flatMap((a, b) => SpiValues.period(a, b))
    )
