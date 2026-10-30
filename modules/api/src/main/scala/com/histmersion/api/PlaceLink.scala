package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

final case class PlaceLink(placeId: String, scheme: String, externalId: String, relation: String = "close_match")
    derives KindlingsCodecAsObject,
      KindlingsSchema
object PlaceLink:
  given Schema[PlaceLink] = summon[KindlingsSchema[PlaceLink]].schema
  val Schemes: List[String] = List("pleiades", "wikidata", "tgn", "geonames", "whg")
  val Relations: List[String] = List("close_match", "exact_match", "broad_match", "see_also")

  def url(scheme: String, externalId: String): Option[String] =
    val id = externalId.trim
    if id.isEmpty then None
    else
      scheme.trim.toLowerCase(java.util.Locale.ROOT) match
        case "wikidata" => Some(s"https://www.wikidata.org/wiki/$id")
        case "geonames" => Some(s"https://www.geonames.org/$id")
        case "pleiades" => Some(s"https://pleiades.stoa.org/places/$id")
        case "tgn"      => Some(s"http://vocab.getty.edu/page/tgn/$id")
        case "whg"      => Some(s"https://whgazetteer.org/places/$id/portal")
        case "commons"  => Some(s"https://commons.wikimedia.org/wiki/Category:${id.replace(' ', '_')}")
        case _          => None

  def schemeTitle(scheme: String): String = scheme.trim.toLowerCase(java.util.Locale.ROOT) match
    case "wikidata" => "Wikidata"
    case "geonames" => "GeoNames"
    case "pleiades" => "Pleiades"
    case "tgn"      => "Getty TGN"
    case "whg"      => "World Historical Gazetteer"
    case "commons"  => "Commons category"
    case other      => other
