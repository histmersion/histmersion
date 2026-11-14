package com.histmersion
package plugins.media

import com.histmersion.util.problem.Problem
import com.histmersion.plugin.*

/** `smithsonian`: the Smithsonian's Open Access API (an api.data.gov key, sent as a header): one search by the place's
  * name answers the records; only a CC0 image media item is used ([[Smithsonian]]). The units in `exclude-providers`
  * (the natural-history departments' specimens) are left out of the search and refused if one still answers; an object
  * made after `max-year` is refused.
  */
final class SmithsonianCollection(conf: CollectionSettings, key: Option[KeyedCollection.Key])
    extends KeyedCollection(
      conf,
      KeyedCollection.Plugin("media-smithsonian"),
      key,
      KeyedCollection.Needs("an api.data.gov key"),
      KeyedCollection.Settings("media.smithsonian")
    ):
  val title = MediaCollection.Title("Smithsonian Open Access objects")
  override val description = described(
    "Smithsonian Open Access objects whose image is itself CC0 (usage.access), tied to a place on the map " +
      "when the object's most specific place field names it (and no other) and its dates overlap the place's; " +
      "natural-history units' specimens and objects made after media.smithsonian.max-year are refused; " +
      "needs an api.data.gov key (histmersion.plugins.media-smithsonian.api-key, a secret reference)"
  )
  private val units = CollectionSettings.items(conf.excludeProviders).map(i => Smithsonian.Unit(i.value))

  def search(
      place: MediaPlace,
      http: Plugin.Http
  ): Either[Problem, List[Either[Problem, MediaItem]]] =
    http
      .askJson(
        Smithsonian.searchUrl(conf.apiUrl, place.label, conf.candidates, units),
        Map(Header.Name(Smithsonian.KeyHeader) -> Header.Value(apiKey.value))
      )
      .flatMap(j => Smithsonian.records(j, units).unreadableEach)
      .left
      .map(explain)
