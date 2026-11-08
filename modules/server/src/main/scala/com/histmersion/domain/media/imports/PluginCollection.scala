package com.histmersion
package domain.media.imports

import com.histmersion.pluginhost.{PluginMedia, Polite, SpiValues}

import com.histmersion.config.MediaCollectionConfig
import com.histmersion.domain.media.imports.{MediaMatch, MediaRecord}
import com.histmersion.plugin.*
import com.histmersion.domain.media.MediaRole
import com.histmersion.domain.catalogue.places.Wikidata

/** A collection searched by a place's name, run by the host's own collection import ([[CollectionImport]]): the plugin
  * searches and describes; the host reads each stated licence ([[PluginMedia.rights]]) and judges it against `allowed`
  * — the collection's allowlist, never anything the plugin says —, ties records to places by [[MediaMatch]] under the
  * rule the collection chose, downloads, thumbnails and stores. One per import (it keeps the run's items for
  * [[complete]]).
  *
  * @param provider
  *   the name its checks are recorded and its pictures stored under
  */
final class PluginCollection(
    val provider: ImportService.Provider,
    col: MediaCollection,
    allowed: MediaCollectionConfig.Licences
) extends SearchedCollection:
  val role: MediaRole.Wire = MediaRole.Wire(col.role.value)
  private val latest = col.latestYear.map(y => com.histmersion.Year(y.value))
  val conf: MediaCollectionConfig =
    MediaCollectionConfig(
      enabled = MediaCollectionConfig.Enabled(true),
      apiUrl = MediaCollectionConfig.ApiUrl(""),
      imageUrl = MediaCollectionConfig.ImageUrl(""),
      candidates = MediaCollectionConfig.Candidates(0),
      perPlace = MediaCollectionConfig.PerPlace(col.perPlace.value),
      requestIntervalMs = MediaCollectionConfig.RequestIntervalMs(col.requestIntervalMs.value),
      displayPx = MediaCollectionConfig.DisplayPx(0),
      maxYear = MediaCollectionConfig.MaxYear(PluginCollection.maxYear(col.matchRule).value),
      licences = allowed
    )

  // the items as the plugin returned them, by key, for `complete` — one import run's own state
  private val items = scala.collection.concurrent.TrieMap.empty[MediaItem.Key, MediaItem]

  def search(
      fetch: Polite,
      place: PlaceFacts
  ): Either[SearchedCollection.Problem, List[Either[SearchedCollection.Problem, MediaRecord]]] =
    PluginMedia
      .guard(col.search(PluginCollection.place(place), PluginMedia.http(fetch)))
      .left
      .map(why => SearchedCollection.Problem(why.value))
      .map(_.map(_.left.map(why => SearchedCollection.Problem(why.value)).map { it =>
        items.update(it.key, it)
        PluginMedia.record(it)
      }))

  override def matches(rec: MediaRecord, place: MediaMatch.PlaceInfo, resolve: MediaMatch.Resolve) =
    col.matchRule match
      case MediaMatchRule.Map(maxYear) => MediaMatch.map(rec, place, resolve, com.histmersion.Year(maxYear.value))
      case _                           => MediaMatch.museum(rec, place, resolve, latest)

  override def usesIds: Boolean = col.matchRule.isInstanceOf[MediaMatchRule.PlaceIds]

  override def matchesWithIds(
      rec: MediaRecord,
      place: MediaMatch.PlaceInfo,
      resolve: MediaMatch.Resolve,
      resolveId: MediaMatch.ResolveId
  ) =
    col.matchRule match
      case MediaMatchRule.PlaceIds(deeper) =>
        MediaMatch.withIds(
          rec,
          place,
          resolveId,
          resolve,
          deeper.map(d => MediaRecord.Geography.Field(d.value)),
          latest
        )
      case _ => matches(rec, place, resolve)

  override def complete(fetch: Polite, rec: MediaRecord): Either[SearchedCollection.Problem, MediaRecord] =
    items.get(MediaItem.Key(rec.key.value)) match
      case None     => Right(rec)
      case Some(it) =>
        PluginMedia
          .guard(col.complete(it, PluginMedia.http(fetch)))
          .left
          .map(why => SearchedCollection.Problem(why.value))
          .map(PluginMedia.record)

object PluginCollection:
  def maxYear(rule: MediaMatchRule): com.histmersion.Year = rule match
    case MediaMatchRule.Map(y) => com.histmersion.Year(y.value)
    case _                     => com.histmersion.Year(0)

  /** The place as a plugin sees it: what the store holds, each value as its type (one that is not left out). Pure. */
  def place(p: PlaceFacts): MediaPlace =
    MediaPlace(
      MediaPlace.Id(p.id.value),
      MediaPlace.Label(p.label.value),
      p.qid.flatMap(q => Wikidata.Id.option(q.value).flatMap(SpiValues.wikidata)),
      p.lat.flatMap(l => SpiValues.latitude(Latitude(l.value))),
      p.lon.flatMap(l => SpiValues.longitude(Longitude(l.value))),
      p.period.flatMap((a, b) => SpiValues.period(a, b))
    )
