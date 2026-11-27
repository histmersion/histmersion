package com.histmersion
package api.library

import com.histmersion.api.catalogue.LinkScheme
import com.histmersion.api.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}
final case class ExternalId(scheme: LinkScheme, id: ExternalId.Id):
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def key: ExternalId.Key = ExternalId.Key(s"${scheme.wire}:$id")

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def placeId: ExternalId.PlaceId =
    ExternalId.PlaceId(s"${scheme.wire}_${(id).value.toLowerCase(java.util.Locale.ROOT)}")

object ExternalId:
  type Key = Key.T
  object Key extends Newtype[String, Pure]
  type PlaceId = PlaceId.T
  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  object PlaceId extends Newtype[String, Pure]
  type Reference = Reference.T
  object Reference extends Newtype[String, Pure]

  type Id = Id.T
  object Id extends Newtype[String, Pure]

  private val Prefixed = """^(wd|wikidata|gn|geonames|pl|pleiades|tgn|whg)\s*:\s*(\S+)$""".r
  private val WikidataUri = """^https?://(?:www\.)?wikidata\.org/(?:entity|wiki)/(Q\d+)/?$""".r
  private val GeonamesUri = """^https?://(?:sws\.|www\.)?geonames\.org/(\d+)(?:/.*)?$""".r
  private val PleiadesUri = """^https?://pleiades\.stoa\.org/places/(\d+)(?:/.*)?$""".r
  private val TgnUri = """^https?://vocab\.getty\.edu/(?:page/)?tgn/(\d+)(?:[-/].*)?$""".r
  private val WhgUri = """^https?://(?:www\.)?whgazetteer\.org/(?:api/)?(?:places?|db)/(?:\?id=)?(\d+)/?.*$""".r

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def parse(raw: Reference): Option[ExternalId] =
    val s = (raw).value.trim
    def make(scheme: LinkScheme, id: Id): Option[ExternalId] =
      val v = (id).value.trim.stripSuffix("/")
      scheme match
        case LinkScheme.Wikidata =>
          Option.when(v.matches("(?i)q\\d+"))(ExternalId(scheme, Id(v.toUpperCase(java.util.Locale.ROOT))))
        case _ => Option.when(v.matches("\\d+"))(ExternalId(scheme, Id(v)))
    s match
      case Prefixed(p, id) =>
        val scheme = p.toLowerCase(java.util.Locale.ROOT) match
          case "wd" | "wikidata" => LinkScheme.Wikidata
          case "gn" | "geonames" => LinkScheme.Geonames
          case "pl" | "pleiades" => LinkScheme.Pleiades
          case "tgn"             => LinkScheme.Tgn
          case _                 => LinkScheme.Whg
        make(scheme, Id(id))
      case WikidataUri(id) => make(LinkScheme.Wikidata, Id(id))
      case GeonamesUri(id) => make(LinkScheme.Geonames, Id(id))
      case PleiadesUri(id) => make(LinkScheme.Pleiades, Id(id))
      case TgnUri(id)      => make(LinkScheme.Tgn, Id(id))
      case WhgUri(id)      => make(LinkScheme.Whg, Id(id))
      case _               => None
