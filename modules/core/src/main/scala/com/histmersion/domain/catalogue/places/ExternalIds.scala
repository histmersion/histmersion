package com.histmersion
package domain.catalogue.places

import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.domain.catalogue.Place

/** Gazetteer ids in the URI forms providers write them. Pure. */
object ExternalIds:
  type Scheme = Scheme.T
  object Scheme extends Newtype[String, Pure]
  type ExternalId = ExternalId.T
  object ExternalId extends Newtype[String, Pure]
  type Uri = Uri.T
  object Uri extends Newtype[String, Pure]
  type Shown = Shown.T
  object Shown extends Newtype[String, Pure]

  val Pleiades: Scheme = Scheme("pleiades")
  val GeoNames: Scheme = Scheme("geonames")
  val Wikidata: Scheme = Scheme("wikidata")

  private val PleiadesUri = """^https?://pleiades\.stoa\.org/places/([0-9]+)(?:[/#?].*)?$""".r
  private val GeoNamesUri = """^https?://(?:www\.|sws\.)?geonames\.org/([0-9]+)(?:[/#?].*)?$""".r
  private val WikidataUri = """^https?://(?:www\.)?wikidata\.org/(?:entity|wiki)/(Q[1-9][0-9]*)(?:[/#?].*)?$""".r

  /** Pure: `(scheme, id)` of a Pleiades, GeoNames or Wikidata URI; None for any other. */
  def of(uri: Uri): Option[(Scheme, ExternalId)] = Option(uri.value).map(_.trim).getOrElse("") match
    case PleiadesUri(id) => Some(Pleiades -> ExternalId(id))
    case GeoNamesUri(id) => Some(GeoNames -> ExternalId(id))
    case WikidataUri(id) => Some(Wikidata -> ExternalId(id))
    case _               => None

  /** Pure: the id a gazetteer place id carries itself (`pleiades_570491`, `geonames_390903`, `wikidata_q132564`).
    */
  def ofPlaceId(placeId: Place.Id): Option[(Scheme, ExternalId)] =
    val s = placeId.value.trim
    def num(prefix: String) = Option.when(s.startsWith(prefix))(s.drop(prefix.length)).filter(_.matches("[0-9]+"))
    num("pleiades_")
      .map(n => Pleiades -> ExternalId(n))
      .orElse(num("geonames_").map(n => GeoNames -> ExternalId(n)))
      .orElse(
        com.histmersion.domain.catalogue.places.Wikidata.qidOfPlaceId(placeId).map(q => Wikidata -> ExternalId(q.value))
      )

  def show(id: (Scheme, ExternalId)): Shown = Shown(s"${id._1}:${id._2}")
