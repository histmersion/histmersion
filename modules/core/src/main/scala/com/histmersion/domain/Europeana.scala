package com.histmersion.domain

import io.circe.{ACursor, Json}

import java.nio.charset.StandardCharsets.UTF_8
import java.util.Locale

/** The Europeana APIs (https://europeana.atlassian.net/wiki/spaces/EF/pages/2385739812, Record API page 2385674279,
  * Thumbnail API page 2480209953), with a user's personal key sent as the `X-Api-Key` header (Europeana's preferred
  * form; the `wskey` parameter is deprecated). Search:
  * `/record/v2/search.json?query=where:("<name>")&reusability=open&media=true&thumbnail=true&qf=TYPE:IMAGE&profile=rich`
  * — `reusability=open` is Europeana's own filter for PDM, CC0, CC BY and CC BY-SA. Each item's licence is still read
  * from its own `rights` (the edm:rights URL) and judged by the allowlist; a rightsstatements.org statement or any
  * other non-CC URL is unreadable as a licence and refused. A record to store is read again through the Record API
  * (`/record/v2/{id}.json`): the licence of the web resource that is stored (its `webResourceEdmRights`, else the
  * aggregation's `edmRights`) must be the search's. The pictures are Europeana's own cached renditions through the
  * keyless Thumbnail API v3 (400 px stored, 200 px the thumbnail; the media file's URL hashed with MD5, as the API
  * documents); the provider's file (`edmIsShownBy`) is recorded as the original. Pure.
  */
object Europeana:

  val KeyHeader = "X-Api-Key"
  val Spatial = "dcterms:spatial"
  val Place = "edm:Place"

  /** The records an import refuses whatever place they name: those of an aggregator (`PROVIDER`, its name exactly, any
    * case), of an institution whose name contains one of `dataProviders` (any case), or whose `dc:type` is one of
    * `types` (letters compared, any case: `Preserved Specimen` = `PreservedSpecimen`). The aggregators and types are
    * excluded in the search as well; a type is read again from the Record API, since a search item does not carry it.
    */
  final case class Exclusions(providers: List[String], dataProviders: List[String], types: List[String])
  object Exclusions:
    val none: Exclusions = Exclusions(Nil, Nil, Nil)

  private def phrase(s: String) = "\"" + s.replace("\"", " ").trim + "\""
  private def anyOf(field: String, vs: List[String]) =
    val ps = vs.map(_.trim).filter(_.nonEmpty)
    if ps.isEmpty then "" else ps.map(phrase).mkString(s" NOT $field:(", " OR ", ")")

  /** Pure: the query — the place as Europeana's aggregated `where` field, without the excluded aggregators (`PROVIDER`)
    * and types (`proxy_dc_type`).
    */
  def query(name: String, ex: Exclusions = Exclusions.none): String =
    s"where:(${phrase(name)})" + anyOf("PROVIDER", ex.providers) + anyOf("proxy_dc_type", ex.types)

  def searchUrl(api: String, name: String, rows: Int, ex: Exclusions = Exclusions.none): String =
    s"${api.stripSuffix("/")}/record/v2/search.json?query=${Read.enc(query(name, ex))}&reusability=open&media=true" +
      s"&thumbnail=true&qf=${Read.enc("TYPE:IMAGE")}&profile=rich&rows=${rows.max(1).min(100)}"

  private def letters(s: String) = s.toLowerCase(Locale.ROOT).filter(_.isLetterOrDigit)

  /** Pure: why a record's aggregator or institution excludes it, or `None`. */
  def excludedBy(providers: List[String], dataProviders: List[String], ex: Exclusions): Option[String] =
    providers
      .find(p => ex.providers.exists(_.trim.equalsIgnoreCase(p)))
      .map(p => s"an excluded provider ($p)")
      .orElse(
        dataProviders
          .find { d =>
            val name = d.toLowerCase(Locale.ROOT)
            ex.dataProviders.map(_.trim.toLowerCase(Locale.ROOT)).exists(f => f.nonEmpty && name.contains(f))
          }
          .map(d => s"an excluded data provider ($d)")
      )

  /** Pure: the first of a record's `dc:type` values that is excluded, or `None`. */
  def excludedType(types: List[String], ex: Exclusions): Option[String] =
    val banned = ex.types.map(letters).filter(_.nonEmpty).toSet
    types.find(t => banned(letters(t)))

  /** Pure: the Record API URL of a Europeana id (`/2058611/_kimbl_3_1`), each segment encoded. */
  def recordUrl(api: String, id: String): String =
    val path = id.split("/").toList.filter(_.nonEmpty).map(s => Read.enc(s).replace("+", "%20")).mkString("/")
    s"${api.stripSuffix("/")}/record/v2/$path.json"

  /** Pure: the Thumbnail API v3 URL of a media file at `width` (200 or 400). */
  def thumbnailUrl(api: String, media: String, width: Int): String =
    val md5 =
      java.security.MessageDigest.getInstance("MD5").digest(media.getBytes(UTF_8)).map(b => f"${b & 0xff}%02x").mkString
    s"${api.stripSuffix("/")}/thumbnail/v3/$width/$md5.jpg"

  /** Pure: the media URL a search's `edmPreview` (a Thumbnail API v2 URL) was made from — its `uri` parameter — or
    * `None` when it is not one.
    */
  def previewSource(preview: String): Option[String] =
    scala.util
      .Try(java.net.URI.create(preview.trim))
      .toOption
      .flatMap(u => Option(u.getRawQuery))
      .toList
      .flatMap(_.split("&"))
      .map(_.split("=", 2))
      .collectFirst { case Array("uri", v) =>
        java.net.URLDecoder.decode(v, UTF_8)
      }
      .filter(_.nonEmpty)

  /** Pure: a licence's short name for the credit line (`CC-BY-SA-4.0` → `CC BY-SA 4.0`). */
  def shortName(l: MediaLicence.Licence): String = l.family match
    case MediaLicence.Cc0          => "CC0"
    case MediaLicence.PublicDomain => "Public Domain Mark"
    case _                         =>
      val parts = l.id.split("-").toList
      val (name, rest) = parts.span(p => !p.headOption.exists(_.isDigit))
      (name match
        case "CC" :: kinds => ("CC " + kinds.mkString("-")) :: rest
        case other         => other ++ rest
      ).mkString(" ")

  /** Pure: the values of a field that may be a string, an array of strings, a LangMap (`{"en": [..]}`) or an array of
    * LangMaps (`[{"def": "Athens"}]`) — English and language-less values first.
    */
  def values(c: ACursor, field: String): List[String] =
    def ofJson(j: Json): List[String] =
      j.asString.toList ++ j.asArray.toList.flatten.flatMap(ofJson) ++ j.asObject.toList.flatMap { o =>
        val langs = o.keys.toList.sortBy(k => if k == "en" then 0 else if k == "def" then 1 else 2)
        langs.flatMap(k => o(k).toList.flatMap(ofJson))
      }
    c.downField(field).focus.toList.flatMap(ofJson).map(_.trim).filter(_.nonEmpty).distinct

  private def isUri(s: String) = s.startsWith("http://") || s.startsWith("https://")

  /** Pure: the items of a search answer; `success: false` is a `Left` with Europeana's message. */
  def records(
      json: Json,
      api: String,
      ex: Exclusions = Exclusions.none
  ): Either[String, List[Either[String, MediaRecord]]] =
    val c = json.hcursor
    if Read.bool(c, "success").contains(false) then
      Left(List(Read.str(c, "error"), Read.str(c, "message")).flatten.distinct.mkString(": ") match
        case "" => "the search failed"
        case e  => e)
    else
      c.downField("items").focus.flatMap(_.asArray) match
        case Some(items)                                    => Right(items.toList.map(record(_, api, ex)))
        case None if Read.long(c, "totalResults").isDefined => Right(Nil)
        case None                                           => Left("no items in the answer")

  /** Pure: one search item; one of an excluded aggregator or institution ([[Exclusions]]) is withheld with that reason.
    */
  def record(json: Json, api: String, ex: Exclusions = Exclusions.none): Either[String, MediaRecord] =
    val c = json.hcursor
    Read.str(c, "id").filter(_.startsWith("/")).toRight("no Europeana id").map { id =>
      val title = values(c, "title").headOption.orElse(values(c, "dcTitleLangAware").headOption).getOrElse(id)
      val creator = values(c, "dcCreator").filterNot(isUri).headOption
      val provider = values(c, "dataProvider").headOption
      val shownBy = values(c, "edmIsShownBy").headOption
      val media = values(c, "edmPreview").headOption.flatMap(previewSource).orElse(shownBy)
      val rights: Either[String, MediaRights] = values(c, "rights").headOption match
        case None    => Left("no rights statement on the record")
        case Some(u) =>
          val lic = MediaLicence.ofUrl(u)
          if lic.family == MediaLicence.Unknown then Left(s"not an open licence ($u)")
          else
            Right(
              MediaRights(
                lic,
                Some(u),
                creator,
                (List(Some(title), creator, provider).flatten ++ List("via Europeana", shortName(lic))).mkString(", ")
              )
            )
      val spatial = values(c, "dctermsSpatial")
      val geo = spatial.filterNot(isUri).map(Spatial -> _) ++
        (values(c, "edmPlaceLabel") ++ values(c, "edmPlaceLabelLangAware")).distinct.map(Place -> _)
      val ids = (spatial.filter(isUri) ++ values(c, "edmPlace")).flatMap(ExternalIds.of).distinct
      val years = values(c, "year").flatMap(y => y.trim.toLongOption).filter(_ != 0)
      val period = Option.when(years.nonEmpty)(()).flatMap(_ => DeepTime.span(years.min, years.max))
      MediaRecord(
        id.stripPrefix("/"),
        title,
        s"https://www.europeana.eu/item$id",
        media.map(thumbnailUrl(api, _, 400)),
        media.map(thumbnailUrl(api, _, 200)),
        shownBy,
        rights,
        geo,
        period,
        ids,
        withheld = excludedBy(values(c, "provider"), values(c, "dataProvider"), ex)
      )
    }

  /** Pure: a record completed from the Record API — the licence read AGAIN, from the web resource that is shown
    * (`webResourceEdmRights`), else from the provider's aggregation (`edmRights`): a missing or different licence
    * refuses it, and so does an excluded `dc:type` of any of its proxies.
    */
  def item(json: Json, rec: MediaRecord, ex: Exclusions = Exclusions.none): Either[String, MediaRecord] =
    val c = json.hcursor
    if Read.bool(c, "success").contains(false) then
      Left(List(Read.str(c, "error"), Read.str(c, "message")).flatten.distinct.mkString(": "))
    else
      val obj = c.downField("object")
      val aggs = obj.downField("aggregations").focus.flatMap(_.asArray).toList.flatten.map(_.hcursor)
      val types =
        obj.downField("proxies").focus.flatMap(_.asArray).toList.flatten.map(_.hcursor).flatMap(values(_, "dcType"))
      val firstAggregation = excludedType(types, ex) match
        case Some(t) => Left(s"an excluded type ($t)")
        case None    => aggs.headOption.toRight("no aggregation on the record")
      firstAggregation.flatMap { agg =>
        val shownBy = Read.str(agg, "edmIsShownBy").orElse(values(agg, "edmIsShownBy").headOption)
        val resource = agg
          .downField("webResources")
          .focus
          .flatMap(_.asArray)
          .toList
          .flatten
          .map(_.hcursor)
          .find(r => shownBy.isDefined && Read.str(r, "about") == shownBy)
        val stated =
          resource.flatMap(values(_, "webResourceEdmRights").headOption).orElse(values(agg, "edmRights").headOption)
        rec.rights.flatMap { r =>
          stated.map(MediaLicence.ofUrl) match
            case None                            => Left("no licence on the full record")
            case Some(l) if l.id != r.licence.id =>
              Left(s"the full record's licence (${
                  if l.id.isEmpty then "unreadable" else l.id
                }) differs from the search's (${r.licence.id})")
            case Some(_) => Right(rec.copy(originalUrl = shownBy.orElse(rec.originalUrl)))
        }
      }
