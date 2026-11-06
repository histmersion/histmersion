package com.histmersion.api.library

import com.histmersion.api.catalogue.LinkScheme
import com.histmersion.api.given
final case class ExternalId(scheme: LinkScheme, id: ExternalId.Id):
  def key: com.histmersion.api.library.ExternalId.KeyResult = _root_.com.histmersion.api.library.ExternalId.KeyResult(s"${scheme.wire}:$id")

  def placeId: com.histmersion.api.library.ExternalId.PlaceIdResult = s"${scheme.wire}_${id.toLowerCase(java.util.Locale.ROOT)}"

object ExternalId:
  type ParseMakeId = ParseMakeId.T
  object ParseMakeId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type KeyResult = KeyResult.T
  object KeyResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlaceIdResult = PlaceIdResult.T
  object PlaceIdResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseRaw = ParseRaw.T
  object ParseRaw extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type Id = Id.T
  object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private val Prefixed = """^(wd|wikidata|gn|geonames|pl|pleiades|tgn|whg)\s*:\s*(\S+)$""".r
  private val WikidataUri = """^https?://(?:www\.)?wikidata\.org/(?:entity|wiki)/(Q\d+)/?$""".r
  private val GeonamesUri = """^https?://(?:sws\.|www\.)?geonames\.org/(\d+)(?:/.*)?$""".r
  private val PleiadesUri = """^https?://pleiades\.stoa\.org/places/(\d+)(?:/.*)?$""".r
  private val TgnUri = """^https?://vocab\.getty\.edu/(?:page/)?tgn/(\d+)(?:[-/].*)?$""".r
  private val WhgUri = """^https?://(?:www\.)?whgazetteer\.org/(?:api/)?(?:places?|db)/(?:\?id=)?(\d+)/?.*$""".r

  def parse(raw: com.histmersion.api.library.ExternalId.ParseRaw): Option[ExternalId] =
    val s = raw.trim
    def make(scheme: LinkScheme, id: com.histmersion.api.library.ExternalId.ParseMakeId): Option[ExternalId] =
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
