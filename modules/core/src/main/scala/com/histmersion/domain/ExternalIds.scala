package com.histmersion.domain

/** Gazetteer ids in the URI forms providers write them. Pure. */
object ExternalIds:
  val Pleiades = "pleiades"
  val GeoNames = "geonames"
  val Wikidata = "wikidata"

  private val PleiadesUri = """^https?://pleiades\.stoa\.org/places/([0-9]+)(?:[/#?].*)?$""".r
  private val GeoNamesUri = """^https?://(?:www\.|sws\.)?geonames\.org/([0-9]+)(?:[/#?].*)?$""".r
  private val WikidataUri = """^https?://(?:www\.)?wikidata\.org/(?:entity|wiki)/(Q[1-9][0-9]*)(?:[/#?].*)?$""".r

  /** Pure: `(scheme, id)` of a Pleiades, GeoNames or Wikidata URI; None for any other. */
  def of(uri: String): Option[(String, String)] = Option(uri).map(_.trim).getOrElse("") match
    case PleiadesUri(id) => Some(Pleiades -> id)
    case GeoNamesUri(id) => Some(GeoNames -> id)
    case WikidataUri(id) => Some(Wikidata -> id)
    case _               => None

  /** Pure: the id a gazetteer place id carries itself (`pleiades_570491`, `geonames_390903`, `wikidata_q132564`).
    */
  def ofPlaceId(placeId: String): Option[(String, String)] =
    val s = placeId.trim
    def num(prefix: String) = Option.when(s.startsWith(prefix))(s.drop(prefix.length)).filter(_.matches("[0-9]+"))
    num("pleiades_")
      .map(Pleiades -> _)
      .orElse(num("geonames_").map(GeoNames -> _))
      .orElse(com.histmersion.domain.Wikidata.qidOfPlaceId(s).map(Wikidata -> _))

  def show(id: (String, String)): String = s"${id._1}:${id._2}"
