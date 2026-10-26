package com.histmersion.domain

import io.circe.{ACursor, Json}

import java.nio.charset.StandardCharsets.UTF_8
import java.util.Locale

/** Free-text object dates as museums write them (`1550-1196 BCE`, `100 BCE-100 CE`, `ca. 3300-ca. 2250 BCE`,
  * `12th-13th century`, `5th century BCE`, `2nd millennium BCE`, `1883 or 1884`) read as a signed span,
  * BCE negative. Conservative: an uncertain date (`?`), a range whose ends run backwards, or anything
  * else it does not read is `None` — and a record without dates is never matched. Pure. */
object FreeTextDates:

  private val Part =
    """^(?:(?:late|early|mid|middle|first half of|second half of|end of|beginning of)[\s-]+)?(?:the\s+)?([0-9]{1,5})(?:st|nd|rd|th)?(?:\s*(century|centuries|millennium|millennia))?(?:\s*(bce|bc|ce|ad))?$""".r

  private enum Unit:
    case Year, Century, Millennium

  private final case class Bound(n: Long, unit: Option[Unit], ordinal: Boolean, bce: Option[Boolean])

  private def bound(raw: String): Option[Bound] =
    raw.trim match
      case Part(n, unit, era) =>
        val u = Option(unit).map(x => if x.startsWith("cent") then Unit.Century else Unit.Millennium)
        val e = Option(era).map(x => x.startsWith("b"))
        Some(Bound(n.toLong, u, raw.trim.matches("""^.*?[0-9]+(st|nd|rd|th).*$"""), e))
      case _ => None

  /** Pure: the signed span of one bound, given the unit and era it carries or inherits. */
  private def span(b: Bound): Option[(Long, Long)] =
    val bce = b.bce.contains(true)
    b.unit.getOrElse(Unit.Year) match
      case Unit.Year if b.ordinal => None
      case Unit.Year       => Option.when(b.n > 0)(if bce then (-b.n, -b.n) else (b.n, b.n))
      case Unit.Century    => Option.when(b.n > 0)(if bce then (-b.n * 100, -(b.n * 100 - 99)) else ((b.n - 1) * 100 + 1, b.n * 100))
      case Unit.Millennium => Option.when(b.n > 0)(if bce then (-b.n * 1000, -((b.n - 1) * 1000 + 1)) else ((b.n - 1) * 1000 + 1, b.n * 1000))

  /** Pure: the span a date text states, or `None`. */
  def parse(text: String): Option[(Long, Long)] =
    val s0 = Option(text).getOrElse("").toLowerCase(Locale.ROOT).trim
    if s0.isEmpty || s0.contains("?") then None
    else
      // eras first (so `b.c.` is not taken for `c.` = circa), then the hedges, then the separators
      val s = s0.replace("b.c.e.", "bce").replace("b.c.", "bc").replace("a.d.", "ad").replace("c.e.", "ce")
        .replaceAll("""\b(ca\.|circa|c\.|about|approx\.|approximately)\s*""", "").replace('–', '-').replace('—', '-')
        .replaceAll("""\b(late|early|mid)-""", "$1 ")
        .replaceAll("""\s+or\s+""", "-").replaceAll("""\s+to\s+""", "-").replaceAll("""\s+""", " ").trim
      val parts = s.split("""\s*-\s*""").toList.map(_.trim).filter(_.nonEmpty)
      parts.map(bound) match
        case List(Some(a)) => span(a)
        case List(Some(a0), Some(b)) =>
          // the first bound inherits the unit and era it does not state (`12th-13th century`, `305-30 BCE`)
          val a = a0.copy(unit = a0.unit.orElse(Option.when(a0.ordinal)(b.unit).flatten), bce = a0.bce.orElse(b.bce))
          for
            (s1, _) <- span(a)
            (_, e2) <- span(b)
            if s1 <= e2
          yield (s1, e2)
        case _ => None

/** The Smithsonian Open Access API (https://edan.si.edu/openaccess/apidocs/): `GET {api}/search?q=&rows=
  * &type=edanmdm&row_group=objects` with an api.data.gov key (sent as the `X-Api-Key` header, which
  * api.data.gov accepts in place of `api_key`, so the key never sits in a URL that could be logged). The
  * answer is `{status, responseCode, response: {rows: [...], rowCount, message}}`; each row is an EDAN
  * `edanmdm` record — the same schema as the Smithsonian's open-data dump (`content.descriptiveNonRepeating`,
  * `content.indexedStructured`, `content.freetext`). A record is used only through an image media item
  * whose OWN `usage.access` is `CC0`; any other is refused. Place fields: `freetext.place` (each value's
  * comma parts, most specific first), then `indexedStructured.geoLocation` from its finest level down.
  * Dates: `freetext.date` entries labelled as the object's date (never an accession or collection
  * date), read by [[FreeTextDates]]. Pure. */
object Smithsonian:

  /** The header the key travels in (api.data.gov's documented alternative to the `api_key` parameter). */
  val KeyHeader = "X-Api-Key"

  /** Pure: the query — the place's name as a phrase, objects with images only, none of the excluded
    * units (`unit_code`, a fielded term the API's `terms` endpoint lists). */
  def query(name: String, excludedUnits: List[String] = Nil): String =
    val units = excludedUnits.map(_.replaceAll("[^A-Za-z0-9_]", "")).filter(_.nonEmpty)
    s"\"${name.replace("\"", " ").trim}\" AND online_media_type:Images" +
      (if units.isEmpty then "" else units.mkString(" AND NOT unit_code:(", " OR ", ")"))

  def searchUrl(api: String, name: String, rows: Int, excludedUnits: List[String] = Nil): String =
    s"${api.stripSuffix("/")}/search?q=${Read.enc(query(name, excludedUnits))}&rows=${rows.max(1).min(1000)}" +
      "&type=edanmdm&row_group=objects"

  /** Pure: the unit a row comes from — `descriptiveNonRepeating.unit_code`, else the row's `unitCode`. */
  def unitOf(json: Json): Option[String] =
    val c = json.hcursor
    Read.str(c.downField("content").downField("descriptiveNonRepeating"), "unit_code").orElse(Read.str(c, "unitCode"))

  /** The labels of `freetext.date` that name when the object was made. */
  val DateLabels: Set[String] = Set("date", "date made", "date created", "creation date", "production date", "object date")

  /** The labels of `freetext.name` that name who made it. */
  val MakerLabels: Set[String] = Set("artist", "maker", "creator", "author", "manufacturer", "photographer", "designer",
    "sculptor", "potter", "illustrator", "engraver")

  /** Pure: the rows of a search answer; an error answer (api.data.gov's `{error: {code, message}}`, or
    * EDAN's `{response: {error}}`) is a `Left`. */
  def records(json: Json, excludedUnits: List[String] = Nil): Either[String, List[Either[String, MediaRecord]]] =
    val c = json.hcursor
    val resp = c.downField("response")
    resp.downField("rows").focus.flatMap(_.asArray) match
      case Some(rows) => Right(rows.toList.map(record(_, excludedUnits)))
      case None if Read.long(resp, "rowCount").isDefined => Right(Nil)
      case None =>
        val gov = c.downField("error")
        Left(List(Read.str(gov, "code"), Read.str(gov, "message")).flatten.mkString(": ") match
          case "" => Read.str(resp, "error").orElse(Read.str(c, "message")).getOrElse("no rows in the answer")
          case e  => e)

  private def labelled(c: ACursor, field: String): List[(String, String)] =
    c.downField(field).focus.flatMap(_.asArray).toList.flatten.flatMap { e =>
      val h = e.hcursor
      Read.str(h, "content").map(v => Read.str(h, "label").getOrElse("").toLowerCase(Locale.ROOT) -> v)
    }

  /** Pure: one row of a search answer; a row of an excluded unit (a natural-history department's
    * specimen) is withheld with that reason, whatever its licence. */
  def record(json: Json, excludedUnits: List[String] = Nil): Either[String, MediaRecord] =
    val c    = json.hcursor
    val cont = c.downField("content")
    val dnr  = cont.downField("descriptiveNonRepeating")
    val free = cont.downField("freetext")
    val idx  = cont.downField("indexedStructured")
    Read.str(dnr, "record_ID").orElse(Read.str(c, "url").map(_.split(":", 2).last)).orElse(Read.str(c, "id"))
      .toRight("no record id").map { key =>
        val title  = Read.str(c, "title").orElse(Read.str(dnr.downField("title"), "content")).getOrElse(key)
        val page   = Read.str(dnr, "record_link").orElse(Read.str(dnr, "guid"))
          .getOrElse(s"https://collections.si.edu/search/detail/${Read.str(c, "url").getOrElse(s"edanmdm:$key")}")
        val unit   = Read.str(dnr, "data_source")
        val maker  = labelled(free, "name").find((l, _) => MakerLabels(l)).map(_._2)
        val creditLine = labelled(free, "creditLine").headOption.map(_._2)
        val credit = (List(Some(title), maker, unit).flatten ++ List("Smithsonian Institution") ++ creditLine.toList :+ "CC0")
          .mkString(", ")
        val media = dnr.downField("online_media").downField("media").focus.flatMap(_.asArray).toList.flatten
          .map(_.hcursor).filter(m => Read.str(m, "type").forall(_.equalsIgnoreCase("Images")))
        val cc0 = media.find(m => Read.str(m.downField("usage"), "access").exists(_.equalsIgnoreCase("CC0")))
        val rights: Either[String, MediaRights] = cc0 match
          case Some(_) => Right(MediaRights.cc0(maker, credit))
          case None if media.isEmpty => Left("no image on the record")
          case None =>
            val stated = media.flatMap(m => Read.str(m.downField("usage"), "access")).distinct
            Left(s"no CC0 image (usage.access ${if stated.isEmpty then "missing" else stated.mkString(", ")})")
        def resource(m: ACursor, label: String) =
          m.downField("resources").focus.flatMap(_.asArray).toList.flatten.map(_.hcursor)
            .find(r => Read.str(r, "label").exists(_.equalsIgnoreCase(label))).flatMap(Read.str(_, "url"))
        val image    = cc0.flatMap(m => resource(m, "Screen Image").orElse(Read.str(m, "content")))
        val thumb    = cc0.flatMap(m => resource(m, "Thumbnail Image").orElse(Read.str(m, "thumbnail")))
        val original = cc0.flatMap(m => resource(m, "High-resolution JPEG").orElse(Read.str(m, "content")))
        // the free-text places first (each value's comma parts, most specific first), then the
        // structured location from its finest level down
        val places = labelled(free, "place").flatMap((l, v) =>
          v.split(",").toList.map(_.trim).filter(_.nonEmpty).map((if l.isEmpty then "place" else l) -> _))
        val levels = idx.downField("geoLocation").focus.flatMap(_.asArray).toList.flatten.flatMap { g =>
          val h = g.hcursor
          h.keys.toList.flatten.filter(_.matches("L[0-9]+")).sortBy(k => -k.drop(1).toInt).flatMap { k =>
            val l = h.downField(k)
            Read.str(l, "content").map(v => Read.str(l, "type").getOrElse(k).toLowerCase(Locale.ROOT) -> v)
          }
        }
        val geo = (places ++ levels).distinctBy((_, v) => v.toLowerCase(Locale.ROOT))
        val period = labelled(free, "date").filter((l, _) => DateLabels(l)).iterator
          .flatMap((_, v) => FreeTextDates.parse(v)).nextOption().flatMap((a, b) => DeepTime.span(a, b))
        val excluded = unitOf(json).filter(u => excludedUnits.exists(_.equalsIgnoreCase(u))).map(u => s"an excluded unit ($u)")
        MediaRecord(key, title, page, image, thumb, original, rights, geo, period, withheld = excluded)
      }

/** The Europeana APIs (https://europeana.atlassian.net/wiki/spaces/EF/pages/2385739812, Record API page
  * 2385674279, Thumbnail API page 2480209953), with a user's personal key sent as the `X-Api-Key` header
  * (Europeana's preferred form; the `wskey` parameter is deprecated). Search:
  * `/record/v2/search.json?query=where:("<name>")&reusability=open&media=true&thumbnail=true&qf=TYPE:IMAGE&profile=rich`
  * — `reusability=open` is Europeana's own filter for PDM, CC0, CC BY and CC BY-SA. Each item's licence is
  * still read from its own `rights` (the edm:rights URL) and judged by the allowlist; a rightsstatements.org
  * statement or any other non-CC URL is unreadable as a licence and refused. A record to store is read again
  * through the Record API (`/record/v2/{id}.json`): the licence of the web resource that is stored (its
  * `webResourceEdmRights`, else the aggregation's `edmRights`) must be the search's. The pictures are
  * Europeana's own cached renditions through the keyless Thumbnail API v3 (400 px stored, 200 px the
  * thumbnail; the media file's URL hashed with MD5, as the API documents); the provider's file
  * (`edmIsShownBy`) is recorded as the original. Pure. */
object Europeana:

  val KeyHeader = "X-Api-Key"
  val Spatial   = "dcterms:spatial"
  val Place     = "edm:Place"

  /** The records an import refuses whatever place they name: those of an aggregator (`PROVIDER`, its
    * name exactly, any case), of an institution whose name contains one of `dataProviders` (any case),
    * or whose `dc:type` is one of `types` (letters compared, any case: `Preserved Specimen` =
    * `PreservedSpecimen`). The aggregators and types are excluded in the search as well; a type is read
    * again from the Record API, since a search item does not carry it. */
  final case class Exclusions(providers: List[String], dataProviders: List[String], types: List[String])
  object Exclusions:
    val none: Exclusions = Exclusions(Nil, Nil, Nil)

  private def phrase(s: String) = "\"" + s.replace("\"", " ").trim + "\""
  private def anyOf(field: String, vs: List[String]) =
    val ps = vs.map(_.trim).filter(_.nonEmpty)
    if ps.isEmpty then "" else ps.map(phrase).mkString(s" NOT $field:(", " OR ", ")")

  /** Pure: the query — the place as Europeana's aggregated `where` field, without the excluded
    * aggregators (`PROVIDER`) and types (`proxy_dc_type`). */
  def query(name: String, ex: Exclusions = Exclusions.none): String =
    s"where:(${phrase(name)})" + anyOf("PROVIDER", ex.providers) + anyOf("proxy_dc_type", ex.types)

  def searchUrl(api: String, name: String, rows: Int, ex: Exclusions = Exclusions.none): String =
    s"${api.stripSuffix("/")}/record/v2/search.json?query=${Read.enc(query(name, ex))}&reusability=open&media=true" +
      s"&thumbnail=true&qf=${Read.enc("TYPE:IMAGE")}&profile=rich&rows=${rows.max(1).min(100)}"

  private def letters(s: String) = s.toLowerCase(Locale.ROOT).filter(_.isLetterOrDigit)

  /** Pure: why a record's aggregator or institution excludes it, or `None`. */
  def excludedBy(providers: List[String], dataProviders: List[String], ex: Exclusions): Option[String] =
    providers.find(p => ex.providers.exists(_.trim.equalsIgnoreCase(p))).map(p => s"an excluded provider ($p)")
      .orElse(dataProviders.find { d =>
        val name = d.toLowerCase(Locale.ROOT)
        ex.dataProviders.map(_.trim.toLowerCase(Locale.ROOT)).exists(f => f.nonEmpty && name.contains(f))
      }.map(d => s"an excluded data provider ($d)"))

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
    val md5 = java.security.MessageDigest.getInstance("MD5").digest(media.getBytes(UTF_8)).map(b => f"${b & 0xff}%02x").mkString
    s"${api.stripSuffix("/")}/thumbnail/v3/$width/$md5.jpg"

  /** Pure: the media URL a search's `edmPreview` (a Thumbnail API v2 URL) was made from — its `uri`
    * parameter — or `None` when it is not one. */
  def previewSource(preview: String): Option[String] =
    scala.util.Try(java.net.URI.create(preview.trim)).toOption.flatMap(u => Option(u.getRawQuery)).toList
      .flatMap(_.split("&")).map(_.split("=", 2)).collectFirst { case Array("uri", v) =>
        java.net.URLDecoder.decode(v, UTF_8) }.filter(_.nonEmpty)

  /** Pure: a licence's short name for the credit line (`CC-BY-SA-4.0` → `CC BY-SA 4.0`). */
  def shortName(l: MediaLicence.Licence): String = l.family match
    case MediaLicence.Cc0          => "CC0"
    case MediaLicence.PublicDomain => "Public Domain Mark"
    case _ =>
      val parts = l.id.split("-").toList
      val (name, rest) = parts.span(p => !p.headOption.exists(_.isDigit))
      (name match
        case "CC" :: kinds => ("CC " + kinds.mkString("-")) :: rest
        case other         => other ++ rest).mkString(" ")

  /** Pure: the values of a field that may be a string, an array of strings, a LangMap (`{"en": [..]}`) or an
    * array of LangMaps (`[{"def": "Athens"}]`) — English and language-less values first. */
  def values(c: ACursor, field: String): List[String] =
    def ofJson(j: Json): List[String] =
      j.asString.toList ++ j.asArray.toList.flatten.flatMap(ofJson) ++ j.asObject.toList.flatMap { o =>
        val langs = o.keys.toList.sortBy(k => if k == "en" then 0 else if k == "def" then 1 else 2)
        langs.flatMap(k => o(k).toList.flatMap(ofJson))
      }
    c.downField(field).focus.toList.flatMap(ofJson).map(_.trim).filter(_.nonEmpty).distinct

  private def isUri(s: String) = s.startsWith("http://") || s.startsWith("https://")

  /** Pure: the items of a search answer; `success: false` is a `Left` with Europeana's message. */
  def records(json: Json, api: String, ex: Exclusions = Exclusions.none): Either[String, List[Either[String, MediaRecord]]] =
    val c = json.hcursor
    if Read.bool(c, "success").contains(false) then
      Left(List(Read.str(c, "error"), Read.str(c, "message")).flatten.distinct.mkString(": ") match
        case "" => "the search failed"
        case e  => e)
    else c.downField("items").focus.flatMap(_.asArray) match
      case Some(items) => Right(items.toList.map(record(_, api, ex)))
      case None if Read.long(c, "totalResults").isDefined => Right(Nil)
      case None => Left("no items in the answer")

  /** Pure: one search item; one of an excluded aggregator or institution ([[Exclusions]]) is withheld
    * with that reason. */
  def record(json: Json, api: String, ex: Exclusions = Exclusions.none): Either[String, MediaRecord] =
    val c = json.hcursor
    Read.str(c, "id").filter(_.startsWith("/")).toRight("no Europeana id").map { id =>
      val title    = values(c, "title").headOption.orElse(values(c, "dcTitleLangAware").headOption).getOrElse(id)
      val creator  = values(c, "dcCreator").filterNot(isUri).headOption
      val provider = values(c, "dataProvider").headOption
      val shownBy  = values(c, "edmIsShownBy").headOption
      val media    = values(c, "edmPreview").headOption.flatMap(previewSource).orElse(shownBy)
      val rights: Either[String, MediaRights] = values(c, "rights").headOption match
        case None => Left("no rights statement on the record")
        case Some(u) =>
          val lic = MediaLicence.ofUrl(u)
          if lic.family == MediaLicence.Unknown then Left(s"not an open licence ($u)")
          else Right(MediaRights(lic, Some(u), creator,
            (List(Some(title), creator, provider).flatten ++ List("via Europeana", shortName(lic))).mkString(", ")))
      val spatial = values(c, "dctermsSpatial")
      val geo = spatial.filterNot(isUri).map(Spatial -> _) ++
        (values(c, "edmPlaceLabel") ++ values(c, "edmPlaceLabelLangAware")).distinct.map(Place -> _)
      val ids = (spatial.filter(isUri) ++ values(c, "edmPlace")).flatMap(ExternalIds.of).distinct
      val years = values(c, "year").flatMap(y => y.trim.toLongOption).filter(_ != 0)
      val period = Option.when(years.nonEmpty)(()).flatMap(_ => DeepTime.span(years.min, years.max))
      MediaRecord(id.stripPrefix("/"), title, s"https://www.europeana.eu/item$id",
        media.map(thumbnailUrl(api, _, 400)), media.map(thumbnailUrl(api, _, 200)), shownBy, rights, geo, period, ids,
        withheld = excludedBy(values(c, "provider"), values(c, "dataProvider"), ex))
    }

  /** Pure: a record completed from the Record API — the licence read AGAIN, from the web resource that is
    * shown (`webResourceEdmRights`), else from the provider's aggregation (`edmRights`): a missing or
    * different licence refuses it, and so does an excluded `dc:type` of any of its proxies. */
  def item(json: Json, rec: MediaRecord, ex: Exclusions = Exclusions.none): Either[String, MediaRecord] =
    val c = json.hcursor
    if Read.bool(c, "success").contains(false) then
      Left(List(Read.str(c, "error"), Read.str(c, "message")).flatten.distinct.mkString(": "))
    else
      val obj = c.downField("object")
      val aggs = obj.downField("aggregations").focus.flatMap(_.asArray).toList.flatten.map(_.hcursor)
      val types = obj.downField("proxies").focus.flatMap(_.asArray).toList.flatten.map(_.hcursor).flatMap(values(_, "dcType"))
      val firstAggregation = excludedType(types, ex) match
        case Some(t) => Left(s"an excluded type ($t)")
        case None    => aggs.headOption.toRight("no aggregation on the record")
      firstAggregation.flatMap { agg =>
        val shownBy = Read.str(agg, "edmIsShownBy").orElse(values(agg, "edmIsShownBy").headOption)
        val resource = agg.downField("webResources").focus.flatMap(_.asArray).toList.flatten.map(_.hcursor)
          .find(r => shownBy.isDefined && Read.str(r, "about") == shownBy)
        val stated = resource.flatMap(values(_, "webResourceEdmRights").headOption).orElse(values(agg, "edmRights").headOption)
        rec.rights.flatMap { r =>
          stated.map(MediaLicence.ofUrl) match
            case None => Left("no licence on the full record")
            case Some(l) if l.id != r.licence.id =>
              Left(s"the full record's licence (${if l.id.isEmpty then "unreadable" else l.id}) differs from the search's (${r.licence.id})")
            case Some(_) => Right(rec.copy(originalUrl = shownBy.orElse(rec.originalUrl)))
        }
      }
