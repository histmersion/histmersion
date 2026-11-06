package com.histmersion.api.library

import com.histmersion.api.catalogue.LinkScheme
import com.histmersion.api.given
final case class ExternalId(scheme: LinkScheme, id: ExternalId.Id):
  def key: com.histmersion.api.library.ExternalId.Key = _root_.com.histmersion.api.library.ExternalId.Key(s"${scheme.wire}:$id")

  def placeId: com.histmersion.api.library.ExternalId.PlaceId = _root_.com.histmersion.api.library.ExternalId.PlaceId(s"${scheme.wire}_${(id).value.toLowerCase(java.util.Locale.ROOT)}")

object ExternalId:
  type Key = Key.T
  object Key extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlaceId = PlaceId.T
  object PlaceId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Reference = Reference.T
  object Reference extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  type Id = Id.T
  object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private val Prefixed = """^(wd|wikidata|gn|geonames|pl|pleiades|tgn|whg)\s*:\s*(\S+)$""".r
  private val WikidataUri = """^https?://(?:www\.)?wikidata\.org/(?:entity|wiki)/(Q\d+)/?$""".r
  private val GeonamesUri = """^https?://(?:sws\.|www\.)?geonames\.org/(\d+)(?:/.*)?$""".r
  private val PleiadesUri = """^https?://pleiades\.stoa\.org/places/(\d+)(?:/.*)?$""".r
  private val TgnUri = """^https?://vocab\.getty\.edu/(?:page/)?tgn/(\d+)(?:[-/].*)?$""".r
  private val WhgUri = """^https?://(?:www\.)?whgazetteer\.org/(?:api/)?(?:places?|db)/(?:\?id=)?(\d+)/?.*$""".r

  def parse(raw: com.histmersion.api.library.ExternalId.Reference): Option[ExternalId] =
    val s = (raw).value.trim
    def make(scheme: LinkScheme, id: com.histmersion.api.library.ExternalId.Id): Option[ExternalId] =
      val v = (id).value.trim.stripSuffix("/")
      scheme match
        case LinkScheme.Wikidata =>
          Option.when(v.matches("(?i)q\\d+"))(ExternalId(scheme, _root_.com.histmersion.api.library.ExternalId.Id(v.toUpperCase(java.util.Locale.ROOT))))
        case _ => Option.when(v.matches("\\d+"))(ExternalId(scheme, _root_.com.histmersion.api.library.ExternalId.Id(v)))
    s match
      case Prefixed(p, id) =>
        val scheme = p.toLowerCase(java.util.Locale.ROOT) match
          case "wd" | "wikidata" => LinkScheme.Wikidata
          case "gn" | "geonames" => LinkScheme.Geonames
          case "pl" | "pleiades" => LinkScheme.Pleiades
          case "tgn"             => LinkScheme.Tgn
          case _                 => LinkScheme.Whg
        make(scheme, _root_.com.histmersion.api.library.ExternalId.Id(id))
      case WikidataUri(id) => make(LinkScheme.Wikidata, _root_.com.histmersion.api.library.ExternalId.Id(id))
      case GeonamesUri(id) => make(LinkScheme.Geonames, _root_.com.histmersion.api.library.ExternalId.Id(id))
      case PleiadesUri(id) => make(LinkScheme.Pleiades, _root_.com.histmersion.api.library.ExternalId.Id(id))
      case TgnUri(id)      => make(LinkScheme.Tgn, _root_.com.histmersion.api.library.ExternalId.Id(id))
      case WhgUri(id)      => make(LinkScheme.Whg, _root_.com.histmersion.api.library.ExternalId.Id(id))
      case _               => None
