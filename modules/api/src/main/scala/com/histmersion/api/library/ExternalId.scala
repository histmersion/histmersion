package com.histmersion.api.library

import com.histmersion.api.catalogue.LinkScheme
import com.histmersion.api.given
final case class ExternalId(scheme: LinkScheme, id: String):
  def key: String = s"${scheme.wire}:$id"

  def placeId: String = s"${scheme.wire}_${id.toLowerCase(java.util.Locale.ROOT)}"

object ExternalId:
  private val Prefixed = """^(wd|wikidata|gn|geonames|pl|pleiades|tgn|whg)\s*:\s*(\S+)$""".r
  private val WikidataUri = """^https?://(?:www\.)?wikidata\.org/(?:entity|wiki)/(Q\d+)/?$""".r
  private val GeonamesUri = """^https?://(?:sws\.|www\.)?geonames\.org/(\d+)(?:/.*)?$""".r
  private val PleiadesUri = """^https?://pleiades\.stoa\.org/places/(\d+)(?:/.*)?$""".r
  private val TgnUri = """^https?://vocab\.getty\.edu/(?:page/)?tgn/(\d+)(?:[-/].*)?$""".r
  private val WhgUri = """^https?://(?:www\.)?whgazetteer\.org/(?:api/)?(?:places?|db)/(?:\?id=)?(\d+)/?.*$""".r

  def parse(raw: String): Option[ExternalId] =
    val s = raw.trim
    def make(scheme: LinkScheme, id: String): Option[ExternalId] =
      val v = id.trim.stripSuffix("/")
      scheme match
        case LinkScheme.Wikidata =>
          Option.when(v.matches("(?i)q\\d+"))(ExternalId(scheme, v.toUpperCase(java.util.Locale.ROOT)))
        case _ => Option.when(v.matches("\\d+"))(ExternalId(scheme, v))
    s match
      case Prefixed(p, id) =>
        val scheme = p.toLowerCase(java.util.Locale.ROOT) match
          case "wd" | "wikidata" => LinkScheme.Wikidata
          case "gn" | "geonames" => LinkScheme.Geonames
          case "pl" | "pleiades" => LinkScheme.Pleiades
          case "tgn"             => LinkScheme.Tgn
          case _                 => LinkScheme.Whg
        make(scheme, id)
      case WikidataUri(id) => make(LinkScheme.Wikidata, id)
      case GeonamesUri(id) => make(LinkScheme.Geonames, id)
      case PleiadesUri(id) => make(LinkScheme.Pleiades, id)
      case TgnUri(id)      => make(LinkScheme.Tgn, id)
      case WhgUri(id)      => make(LinkScheme.Whg, id)
      case _               => None
