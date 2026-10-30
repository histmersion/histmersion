package com.histmersion.server.media

import com.histmersion.pluginhost.{europeanaExclusions, excludedProviders, latestYear, Polite}

import com.histmersion.api.MediaProvider
import com.histmersion.config.MediaCollectionConfig
import com.histmersion.domain.{MediaMatch, MediaRecord, Smithsonian}
import com.histmersion.db.MediaRepo.PlaceFacts
import com.histmersion.values.MediaRole

/** The Smithsonian's Open Access API (an api.data.gov key, sent as a header): one search by the place's name answers
  * the records; only a CC0 image media item is used ([[Smithsonian]]). The units in `exclude-providers` (the
  * natural-history departments' specimens) are left out of the search and refused if one still answers; an object made
  * after `max-year` is refused.
  */
final class SmithsonianCollection(val conf: MediaCollectionConfig, key: String) extends Collection:
  val provider = MediaProvider.Smithsonian
  val role = MediaRole.Object
  private val headers = Map(Smithsonian.KeyHeader -> key)
  private val units = conf.excludedProviders
  def search(fetch: Polite, place: PlaceFacts): Either[String, List[Either[String, MediaRecord]]] =
    fetch
      .json(Smithsonian.searchUrl(conf.apiUrl, place.label, conf.candidates, units), headers)
      .flatMap(Smithsonian.records(_, units))
      .left
      .map(KeyRefusal.explain(_, BuiltinMedia.SmithsonianPlugin))
  override def matches(rec: MediaRecord, place: MediaMatch.PlaceInfo, resolve: MediaMatch.Resolve) =
    MediaMatch.museum(rec, place, resolve, conf.latestYear)
