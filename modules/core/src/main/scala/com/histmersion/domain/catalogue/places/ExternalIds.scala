package com.histmersion.domain.catalogue.places

/** Gazetteer ids in the URI forms providers write them. Pure. */
object ExternalIds:
  type OfPlaceIdNumPrefix = OfPlaceIdNumPrefix.T
  object OfPlaceIdNumPrefix extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type OfResultPart1 = OfResultPart1.T
  object OfResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OfResultPart2 = OfResultPart2.T
  object OfResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OfUri = OfUri.T
  object OfUri extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OfPlaceIdResultPart1 = OfPlaceIdResultPart1.T
  object OfPlaceIdResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OfPlaceIdResultPart2 = OfPlaceIdResultPart2.T
  object OfPlaceIdResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ShowResult = ShowResult.T
  object ShowResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ShowIdPart1 = ShowIdPart1.T
  object ShowIdPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ShowIdPart2 = ShowIdPart2.T
  object ShowIdPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val Pleiades = "pleiades"
  val GeoNames = "geonames"
  val Wikidata = "wikidata"

  private val PleiadesUri = """^https?://pleiades\.stoa\.org/places/([0-9]+)(?:[/#?].*)?$""".r
  private val GeoNamesUri = """^https?://(?:www\.|sws\.)?geonames\.org/([0-9]+)(?:[/#?].*)?$""".r
  private val WikidataUri = """^https?://(?:www\.)?wikidata\.org/(?:entity|wiki)/(Q[1-9][0-9]*)(?:[/#?].*)?$""".r

  /** Pure: `(scheme, id)` of a Pleiades, GeoNames or Wikidata URI; None for any other. */
  def of(uri: com.histmersion.domain.catalogue.places.ExternalIds.OfUri): Option[(com.histmersion.domain.catalogue.places.ExternalIds.OfResultPart1, com.histmersion.domain.catalogue.places.ExternalIds.OfResultPart2)] = Option(uri).map(_.trim).getOrElse("") match
    case PleiadesUri(id) => Some(Pleiades -> id)
    case GeoNamesUri(id) => Some(GeoNames -> id)
    case WikidataUri(id) => Some(Wikidata -> id)
    case _               => None

  /** Pure: the id a gazetteer place id carries itself (`pleiades_570491`, `geonames_390903`, `wikidata_q132564`).
    */
  def ofPlaceId(placeId: com.histmersion.domain.catalogue.Place.Id): Option[(com.histmersion.domain.catalogue.places.ExternalIds.OfPlaceIdResultPart1, com.histmersion.domain.catalogue.places.ExternalIds.OfPlaceIdResultPart2)] =
    val s = placeId.trim
    def num(prefix: com.histmersion.domain.catalogue.places.ExternalIds.OfPlaceIdNumPrefix) = Option.when(s.startsWith(prefix))(s.drop(prefix.length)).filter(_.matches("[0-9]+"))
    num(com.histmersion.domain.catalogue.places.ExternalIds.OfPlaceIdNumPrefix("pleiades_"))
      .map(Pleiades -> _)
      .orElse(num("geonames_").map(GeoNames -> _))
      .orElse(com.histmersion.domain.catalogue.places.Wikidata.qidOfPlaceId(s).map(Wikidata -> _))

  def show(id: (com.histmersion.domain.catalogue.places.ExternalIds.ShowIdPart1, com.histmersion.domain.catalogue.places.ExternalIds.ShowIdPart2)): com.histmersion.domain.catalogue.places.ExternalIds.ShowResult = s"${id._1}:${id._2}"
