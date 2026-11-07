package com.histmersion.plugins.media

/** Gazetteer ids in the URI forms providers write them: `(scheme, id)` of a Pleiades, GeoNames or Wikidata URI, as a
  * [[com.histmersion.plugin.MediaItem]]'s `ids` carry them. Pure.
  */
object GazetteerUri:

  private val PleiadesUri = """^https?://pleiades\.stoa\.org/places/([0-9]+)(?:[/#?].*)?$""".r
  private val GeoNamesUri = """^https?://(?:www\.|sws\.)?geonames\.org/([0-9]+)(?:[/#?].*)?$""".r
  private val WikidataUri = """^https?://(?:www\.)?wikidata\.org/(?:entity|wiki)/(Q[1-9][0-9]*)(?:[/#?].*)?$""".r

  def of(uri: com.histmersion.plugin.GazetteerPlace.Id): Option[(com.histmersion.plugin.GazetteerPlace.Id, com.histmersion.plugin.GazetteerPlace.Source)] = Option(uri).map(_.trim).getOrElse("") match
    case PleiadesUri(id) => Some("pleiades" -> id)
    case GeoNamesUri(id) => Some("geonames" -> id)
    case WikidataUri(id) => Some("wikidata" -> id)
    case _               => None
