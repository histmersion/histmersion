package com.histmersion.plugins.media

import com.histmersion.plugin.*

/** `smithsonian`: the Smithsonian's Open Access API (an api.data.gov key, sent as a header): one search by the place's
  * name answers the records; only a CC0 image media item is used ([[Smithsonian]]). The units in `exclude-providers`
  * (the natural-history departments' specimens) are left out of the search and refused if one still answers; an object
  * made after `max-year` is refused.
  */
final class SmithsonianCollection(conf: CollectionSettings, key: Option[SmithsonianCollection.Key])
    extends KeyedCollection(conf, "media-smithsonian", key, "an api.data.gov key", "media.smithsonian"):
  val title = "Smithsonian Open Access objects"
  override val description = described(
    "Smithsonian Open Access objects whose image is itself CC0 (usage.access), tied to a place on the map " +
      "when the object's most specific place field names it (and no other) and its dates overlap the place's; " +
      "natural-history units' specimens and objects made after media.smithsonian.max-year are refused; " +
      "needs an api.data.gov key (histmersion.plugins.media-smithsonian.api-key, a secret reference)"
  )
  private val units = CollectionSettings.items(conf.excludeProviders)

  def search(place: MediaPlace, http: Plugin.Http): Either[com.histmersion.plugins.media.SmithsonianCollection.Problem, List[Either[com.histmersion.plugin.MediaItem.Key, MediaItem]]] =
    http
      .askJson(
        Smithsonian.searchUrl(conf.apiUrl, place.label, conf.candidates, units),
        Map(Smithsonian.KeyHeader -> apiKey)
      )
      .flatMap(Smithsonian.records(_, units))
      .left
      .map(explain)

object SmithsonianCollection:
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Key = Key.T
  object Key extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

