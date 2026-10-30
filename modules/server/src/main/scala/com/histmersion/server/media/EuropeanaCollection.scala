package com.histmersion.server.media

import com.histmersion.pluginhost.{europeanaExclusions, excludedProviders, latestYear, Polite}

import com.histmersion.api.MediaProvider
import com.histmersion.config.MediaCollectionConfig
import com.histmersion.domain.{Europeana, MediaMatch, MediaRecord}
import com.histmersion.db.MediaRepo.PlaceFacts
import com.histmersion.values.MediaRole

/** Europeana's Search API (a user's personal key, sent as a header): one search by the place's name, openly reusable
  * images only; a record to store is read again through the Record API, whose licence must be the search's
  * ([[Europeana.item]]). Tied by the provider's own `dcterms:spatial` values first, then by a gazetteer id they carry,
  * else by the museum rule's names. The aggregators, institutions and `dc:type`s the collection excludes
  * ([[Europeana.Exclusions]]: natural-history specimens) are refused, and so is an object made after `max-year`.
  */
final class EuropeanaCollection(val conf: MediaCollectionConfig, key: String) extends Collection:
  val provider = MediaProvider.Europeana
  val role = MediaRole.Object
  private val headers = Map(Europeana.KeyHeader -> key)
  private val exclusions = conf.europeanaExclusions
  def search(fetch: Polite, place: PlaceFacts): Either[String, List[Either[String, MediaRecord]]] =
    fetch
      .json(Europeana.searchUrl(conf.apiUrl, place.label, conf.candidates, exclusions), headers)
      .flatMap(Europeana.records(_, conf.apiUrl, exclusions))
      .left
      .map(KeyRefusal.explain(_, BuiltinMedia.EuropeanaPlugin))
  override def usesIds = true
  override def matchesWithIds(
      rec: MediaRecord,
      place: MediaMatch.PlaceInfo,
      resolve: MediaMatch.Resolve,
      resolveId: MediaMatch.ResolveId
  ) =
    MediaMatch.withIds(rec, place, resolveId, resolve, Set(Europeana.Spatial), conf.latestYear)
  override def complete(fetch: Polite, rec: MediaRecord): Either[String, MediaRecord] =
    fetch
      .json(Europeana.recordUrl(conf.apiUrl, "/" + rec.key), headers)
      .flatMap(Europeana.item(_, rec, exclusions))
      .left
      .map(KeyRefusal.explain(_, BuiltinMedia.EuropeanaPlugin))
