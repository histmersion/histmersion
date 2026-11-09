package com.histmersion
package plugins.media

import com.histmersion.plugin.Problem
import com.histmersion.plugin.*

/** `europeana`: Europeana's Search API (a user's personal key, sent as a header): one search by the place's name,
  * openly reusable images only; a record to store is read again through the Record API, whose licence must be the
  * search's ([[Europeana.item]]). Tied by the provider's own `dcterms:spatial` values first, then by a gazetteer id
  * they carry, else by the museum rule's names. The aggregators, institutions and `dc:type`s the collection excludes
  * (natural-history specimens) are refused, and so is an object made after `max-year`.
  */
final class EuropeanaCollection(conf: CollectionSettings, key: Option[KeyedCollection.Key], licences: LicenceReader)
    extends KeyedCollection(
      conf,
      KeyedCollection.Plugin("media-europeana"),
      key,
      KeyedCollection.Needs("a Europeana personal API key"),
      KeyedCollection.Settings("media.europeana")
    ):
  val title = MediaCollection.Title("Europeana images")
  override val description = described(
    "Europeana's openly licensed images (reusability=open: CC0, PDM, CC BY, CC BY-SA), each record's own " +
      "edm:rights read from the search and again from the Record API; tied to a place on the map by its " +
      "dcterms:spatial name or a gazetteer id, and its dates; natural-history specimens and objects made after " +
      "media.europeana.max-year are refused; needs a personal API key " +
      "(histmersion.plugins.media-europeana.api-key, a secret reference)"
  )
  override def matchRule = MediaMatchRule.PlaceIds(Set(MediaMatchRule.PlaceIds.Deeper(Europeana.Spatial)))
  private val exclusions = Europeana.Exclusions(
    CollectionSettings.items(conf.excludeProviders).map(i => Europeana.Exclusions.Providers(i.value)),
    CollectionSettings.items(conf.excludeDataProviders).map(i => Europeana.Exclusions.DataProviders(i.value)),
    CollectionSettings.items(conf.excludeTypes).map(i => Europeana.Exclusions.Types(i.value))
  )
  private def headers = Map(
    Header.Name(Europeana.KeyHeader) -> Header.Value(apiKey.value)
  )

  def search(
      place: MediaPlace,
      http: Plugin.Http
  ): Either[Plugin.Http.Failure, List[Either[Plugin.Http.Failure, MediaItem]]] =
    http
      .askJson(Europeana.searchUrl(conf.apiUrl, place.label, conf.candidates, exclusions), headers)
      .flatMap(j => Europeana.records(j, conf.apiUrl, licences, exclusions).unreadableEach)
      .left
      .map(explain)

  override def complete(item: MediaItem, http: Plugin.Http): Either[Problem, MediaItem] =
    http
      .askJson(Europeana.recordUrl(conf.apiUrl, item.key), headers)
      .flatMap(j => Europeana.item(j, item, licences, exclusions).unreadable)
      .left
      .map(e => explain(e).problem)
