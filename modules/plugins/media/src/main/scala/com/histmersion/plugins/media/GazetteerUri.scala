package com.histmersion
package plugins.media

import com.histmersion.plugin.MediaItem
import com.histmersion.plugin.GazetteerPlace

/** Gazetteer ids in the URI forms providers write them: `(scheme, id)` of a Pleiades, GeoNames or Wikidata URI, as a
  * [[com.histmersion.plugin.MediaItem]]'s `ids` carry them. Pure.
  */
object GazetteerUri:

  private val PleiadesUri = """^https?://pleiades\.stoa\.org/places/([0-9]+)(?:[/#?].*)?$""".r
  private val GeoNamesUri = """^https?://(?:www\.|sws\.)?geonames\.org/([0-9]+)(?:[/#?].*)?$""".r
  private val WikidataUri = """^https?://(?:www\.)?wikidata\.org/(?:entity|wiki)/(Q[1-9][0-9]*)(?:[/#?].*)?$""".r

  def of(uri: GazetteerPlace.Id): Option[(MediaItem.IdsPart1, MediaItem.IdsPart2)] =
    Option(uri).map(_.value.trim).getOrElse("") match
      case PleiadesUri(id) => Some(MediaItem.IdsPart1("pleiades") -> MediaItem.IdsPart2(id))
      case GeoNamesUri(id) => Some(MediaItem.IdsPart1("geonames") -> MediaItem.IdsPart2(id))
      case WikidataUri(id) => Some(MediaItem.IdsPart1("wikidata") -> MediaItem.IdsPart2(id))
      case _               => None
