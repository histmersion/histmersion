package com.histmersion.plugins.media

/** Gazetteer ids in the URI forms providers write them: `(scheme, id)` of a Pleiades, GeoNames or Wikidata URI, as a
  * [[com.histmersion.plugin.MediaItem]]'s `ids` carry them. Pure.
  */
object GazetteerUri:
  type OfResultPart1 = OfResultPart1.T
  object OfResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OfResultPart2 = OfResultPart2.T
  object OfResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OfUri = OfUri.T
  object OfUri extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private val PleiadesUri = """^https?://pleiades\.stoa\.org/places/([0-9]+)(?:[/#?].*)?$""".r
  private val GeoNamesUri = """^https?://(?:www\.|sws\.)?geonames\.org/([0-9]+)(?:[/#?].*)?$""".r
  private val WikidataUri = """^https?://(?:www\.)?wikidata\.org/(?:entity|wiki)/(Q[1-9][0-9]*)(?:[/#?].*)?$""".r

  def of(uri: com.histmersion.plugins.media.GazetteerUri.OfUri): Option[(com.histmersion.plugins.media.GazetteerUri.OfResultPart1, com.histmersion.plugins.media.GazetteerUri.OfResultPart2)] = Option(uri).map(_.trim).getOrElse("") match
    case PleiadesUri(id) => Some("pleiades" -> id)
    case GeoNamesUri(id) => Some("geonames" -> id)
    case WikidataUri(id) => Some("wikidata" -> id)
    case _               => None
