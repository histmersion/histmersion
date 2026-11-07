package com.histmersion.plugins.media

import com.histmersion.plugin.*

/** `europeana`: Europeana's Search API (a user's personal key, sent as a header): one search by the place's name,
  * openly reusable images only; a record to store is read again through the Record API, whose licence must be the
  * search's ([[Europeana.item]]). Tied by the provider's own `dcterms:spatial` values first, then by a gazetteer id
  * they carry, else by the museum rule's names. The aggregators, institutions and `dc:type`s the collection excludes
  * (natural-history specimens) are refused, and so is an object made after `max-year`.
  */
final class EuropeanaCollection(conf: CollectionSettings, key: Option[EuropeanaCollection.Key], licences: LicenceReader)
    extends KeyedCollection(conf, com.histmersion.plugins.media.KeyedCollection.Plugin("media-europeana"), key, com.histmersion.plugins.media.KeyedCollection.Needs("a Europeana personal API key"), com.histmersion.plugins.media.KeyedCollection.Settings("media.europeana")):
  val title = com.histmersion.plugin.MediaCollection.TitleResult("Europeana images")
  override val description = described(
    "Europeana's openly licensed images (reusability=open: CC0, PDM, CC BY, CC BY-SA), each record's own " +
      "edm:rights read from the search and again from the Record API; tied to a place on the map by its " +
      "dcterms:spatial name or a gazetteer id, and its dates; natural-history specimens and objects made after " +
      "media.europeana.max-year are refused; needs a personal API key " +
      "(histmersion.plugins.media-europeana.api-key, a secret reference)"
  )
  override def matchRule = MediaMatchRule.PlaceIds(Set(Europeana.Spatial))
  private val exclusions = Europeana.Exclusions(
    CollectionSettings.items(conf.excludeProviders),
    CollectionSettings.items(conf.excludeDataProviders),
    CollectionSettings.items(conf.excludeTypes)
  )
  private def headers = Map(Europeana.KeyHeader -> apiKey)

  def search(place: MediaPlace, http: Plugin.Http): Either[com.histmersion.plugins.media.EuropeanaCollection.Problem, List[Either[com.histmersion.plugin.MediaItem.Key, MediaItem]]] =
    http
      .askJson(Europeana.searchUrl(conf.apiUrl, place.label, conf.candidates, exclusions), headers)
      .flatMap(Europeana.records(_, conf.apiUrl, licences, exclusions))
      .left
      .map(explain)

  override def complete(item: MediaItem, http: Plugin.Http): Either[String, MediaItem] =
    http
      .askJson(Europeana.recordUrl(conf.apiUrl, "/" + item.key), headers)
      .flatMap(Europeana.item(_, item, licences, exclusions))
      .left
      .map(explain)

object EuropeanaCollection:
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Key = Key.T
  object Key extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

