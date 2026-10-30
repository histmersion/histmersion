package com.histmersion.api

/** One id in one authority: Wikidata `Q42`, GeoNames `2643743`, Pleiades `579885`, TGN `7011944`. */
final case class ExternalId(scheme: LinkScheme, id: String):
  /** `wikidata:Q42` — how a source names a record it imported. */
  def key: String = s"${scheme.wire}:$id"

  /** The id a place gets when this record is the first to bring it in: `geonames_2643743`, `wikidata_q42` — the
    * convention of the seed's own `pleiades_579885`.
    */
  def placeId: String = s"${scheme.wire}_${id.toLowerCase(java.util.Locale.ROOT)}"

object ExternalId:
  private val Prefixed = """^(wd|wikidata|gn|geonames|pl|pleiades|tgn|whg)\s*:\s*(\S+)$""".r
  private val WikidataUri = """^https?://(?:www\.)?wikidata\.org/(?:entity|wiki)/(Q\d+)/?$""".r
  private val GeonamesUri = """^https?://(?:sws\.|www\.)?geonames\.org/(\d+)(?:/.*)?$""".r
  private val PleiadesUri = """^https?://pleiades\.stoa\.org/places/(\d+)(?:/.*)?$""".r
  private val TgnUri = """^https?://vocab\.getty\.edu/(?:page/)?tgn/(\d+)(?:[-/].*)?$""".r
  private val WhgUri = """^https?://(?:www\.)?whgazetteer\.org/(?:api/)?(?:places?|db)/(?:\?id=)?(\d+)/?.*$""".r

  /** `wd:Q42`, `gn:123`, `pl:579885`, `tgn:7011944`, `whg:123` or the authority's URI; `None` for anything else (a VIAF
    * id, a dataset-local id). Ids are normalised: `Q` upper case, digits as is.
    */
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
