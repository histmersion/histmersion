package com.histmersion.server.media

import com.histmersion.pluginhost.Polite

import com.histmersion.api.MediaProvider
import com.histmersion.config.MediaCollectionConfig
import com.histmersion.domain.{Europeana, MediaMatch, MediaRecord, Smithsonian}
import com.histmersion.db.MediaRepo.PlaceFacts
import com.histmersion.values.MediaRole

/** What a key-gated API's refusal means, said once for both collections. */
private[media] object KeyRefusal:
  /** Pure: an HTTP 401/403 of a key-gated API named as a refused key, with the setting to check. */
  def explain(e: String, plugin: String): String =
    if e.contains("HTTP 401") || e.contains("HTTP 403") then
      s"$e — the API key was refused (check histmersion.plugins.$plugin.api-key)"
    else e

/** The Smithsonian's Open Access API (an api.data.gov key, sent as a header): one search by the place's
  * name answers the records; only a CC0 image media item is used ([[Smithsonian]]). */
final class SmithsonianCollection(val conf: MediaCollectionConfig, key: String) extends Collection:
  val provider = MediaProvider.Smithsonian
  val role     = MediaRole.Object
  private val headers = Map(Smithsonian.KeyHeader -> key)
  def search(fetch: Polite, place: PlaceFacts): Either[String, List[Either[String, MediaRecord]]] =
    fetch.json(Smithsonian.searchUrl(conf.apiUrl, place.label, conf.candidates), headers).flatMap(Smithsonian.records)
      .left.map(KeyRefusal.explain(_, BuiltinMedia.SmithsonianPlugin))

/** Europeana's Search API (a user's personal key, sent as a header): one search by the place's name,
  * openly reusable images only; a record to store is read again through the Record API, whose licence
  * must be the search's ([[Europeana.item]]). Tied by the provider's own `dcterms:spatial` values first,
  * then by a gazetteer id they carry, else by the museum rule's names. */
final class EuropeanaCollection(val conf: MediaCollectionConfig, key: String) extends Collection:
  val provider = MediaProvider.Europeana
  val role     = MediaRole.Object
  private val headers = Map(Europeana.KeyHeader -> key)
  def search(fetch: Polite, place: PlaceFacts): Either[String, List[Either[String, MediaRecord]]] =
    fetch.json(Europeana.searchUrl(conf.apiUrl, place.label, conf.candidates), headers)
      .flatMap(Europeana.records(_, conf.apiUrl)).left.map(KeyRefusal.explain(_, BuiltinMedia.EuropeanaPlugin))
  override def usesIds = true
  override def matchesWithIds(rec: MediaRecord, place: MediaMatch.PlaceInfo, resolve: MediaMatch.Resolve,
      resolveId: MediaMatch.ResolveId) = MediaMatch.withIds(rec, place, resolveId, resolve, Set(Europeana.Spatial))
  override def complete(fetch: Polite, rec: MediaRecord): Either[String, MediaRecord] =
    fetch.json(Europeana.recordUrl(conf.apiUrl, "/" + rec.key), headers).flatMap(Europeana.item(_, rec))
      .left.map(KeyRefusal.explain(_, BuiltinMedia.EuropeanaPlugin))
