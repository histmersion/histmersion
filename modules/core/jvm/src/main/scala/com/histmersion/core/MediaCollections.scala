package com.histmersion.core

import io.circe.{ACursor, Json}

import java.net.URLEncoder
import java.nio.charset.StandardCharsets.UTF_8
import java.util.Locale

/** One object or map of an open-access collection (#1446), as its API describes it — the pure,
  * provider-neutral shape every collection parser produces and the import judges.
  *
  * @param key        the provider's own id for it (`252448`, `1916.131`, `2007627513`)
  * @param pageUrl    the record's page at the provider (the credit line's link)
  * @param imageUrl   the rendition to store (the provider's own web-size copy, not the original)
  * @param thumbUrl   the provider's small rendition for the card, when it has one (else one is made here)
  * @param originalUrl the full-size original, recorded and never downloaded
  * @param rights     what the record says about reuse — read from it, never assumed — or why it cannot be used
  * @param geography  the record's find-spot / place fields, MOST SPECIFIC FIRST, as (field, value)
  * @param period     the object's date range, signed years (BCE negative), when the record gives one */
final case class MediaRecord(
    key: String,
    title: String,
    pageUrl: String,
    imageUrl: Option[String],
    thumbUrl: Option[String],
    originalUrl: Option[String],
    rights: Either[String, MediaRights],
    geography: List[(String, String)],
    period: Option[(Long, Long)]
)

/** The licence of a collection record and the credit line in the provider's own form. */
final case class MediaRights(licence: MediaLicence.Licence, licenceUrl: Option[String], author: Option[String],
    credit: String)

object MediaRights:
  val Cc0Url = "https://creativecommons.org/publicdomain/zero/1.0/"
  val PdmUrl = "https://creativecommons.org/publicdomain/mark/1.0/"
  def cc0(author: Option[String], credit: String): MediaRights =
    MediaRights(MediaLicence.Licence("CC0-1.0", MediaLicence.Cc0), Some(Cc0Url), author, credit)
  def publicDomain(author: Option[String], credit: String, url: Option[String] = Some(PdmUrl)): MediaRights =
    MediaRights(MediaLicence.Licence("public-domain", MediaLicence.PublicDomain), url, author, credit)

/** How a collection record is tied to a place on the map — conservatively, and with the reason
  * recorded (#1446). Pure.
  *
  *  - **Geography**, never coordinates: a record's place field (a find-spot, a city, a country, a
  *    map's location) is compared with the place's name and alternative names by the gazetteer's
  *    name key ([[Geocode.key]] = `histmersion_place_key`), whole value against whole name. A value
  *    marked uncertain (`?`, "probably", "said to be") is never used.
  *  - **Most specific first** (a museum object): the first field whose value names ANY place on the
  *    map decides. It must name the target and no other place (equivalents of one site excepted — the
  *    caller's `resolve`), so an object from Mycenae is not also filed under Greece.
  *  - **Any field** (a map): a map lists every place it shows, so it is linked to the target when one
  *    of its locations names the target alone.
  *  - **Dates** (an object): its range must overlap the place's period — the place's own, else the
  *    span of the findings it holds. A record or place without dates is not matched. */
object MediaMatch:

  /** A place the import asks about: its id and label, the keys of every name it goes by, its period. */
  final case class PlaceInfo(id: String, label: String, keys: Set[String], period: Option[(Long, Long)])

  private val Uncertain = List("?", "probably", "possibly", "perhaps", "said to be", "attributed", "likely", "uncertain")

  /** Pure: is a place value marked as uncertain? */
  def uncertain(v: String): Boolean =
    val s = v.toLowerCase(Locale.ROOT)
    Uncertain.exists(s.contains)

  /** Pure: the name key of a value (empty when it is uncertain or blank). */
  def keyOf(v: String): String = if uncertain(v) then "" else Geocode.key(v)

  /** Pure: do two inclusive year ranges overlap? */
  def overlaps(a: (Long, Long), b: (Long, Long)): Boolean = a._1 <= b._2 && b._1 <= a._2

  /** Pure: a year range as a person reads it (`1400–1200 BCE`, `200 BCE–100 CE`). */
  def years(p: (Long, Long)): String =
    def y(v: Long) = if v < 0 then s"${-v} BCE" else s"$v CE"
    if p._1 < 0 && p._2 < 0 then s"${-p._1}–${-p._2} BCE"
    else if p._1 >= 0 && p._2 >= 0 then s"${p._1}–${p._2} CE"
    else s"${y(p._1)}–${y(p._2)}"

  /** What a value names on the map: the ids of the map places with that name key, equivalents of
    * one site counted once — the caller's lookup. */
  type Resolve = String => Set[String]

  /** Pure: which field of the record ties it to the target — `Right("excavation = Mycenae")` — or why
    * none does. `specific` = museum rule (the most specific naming field decides); otherwise the map
    * rule (any field naming the target alone). */
  def geography(rec: MediaRecord, target: PlaceInfo, resolve: Resolve, specific: Boolean): Either[String, String] =
    val named = rec.geography.flatMap((f, v) => Option(keyOf(v)).filter(_.nonEmpty).map(k => (f, v.trim, k)))
    if named.isEmpty then Left("no place on the record")
    else if specific then
      named.iterator.map((f, v, k) => (f, v, resolve(k))).find(_._3.nonEmpty) match
        case None => Left("its place is not on the map")
        case Some((f, v, ids)) if ids == Set(target.id) => Right(s"$f = $v")
        case Some((_, _, ids)) if ids.contains(target.id) => Left("its place name is ambiguous on the map")
        case Some(_) => Left("names another place on the map")
    else
      named.find((_, _, k) => target.keys(k)) match
        case None => Left("its place is not this one")
        case Some((f, v, k)) =>
          if resolve(k) == Set(target.id) then Right(s"$f = $v") else Left("its place name is ambiguous on the map")

  /** Pure: the museum rule — geography, then the dates; the basis names both. */
  def museum(rec: MediaRecord, target: PlaceInfo, resolve: Resolve): Either[String, String] =
    geography(rec, target, resolve, specific = true).flatMap { geo =>
      (rec.period, target.period) match
        case (None, _) => Left("no dates on the record")
        case (_, None) => Left("the place has no period to compare")
        case (Some(r), Some(p)) =>
          if overlaps(r, p) then Right(s"$geo; ${years(r)} within ${years(p)}")
          else Left("its dates do not overlap the place's")
    }

  /** Pure: the map rule — a location naming the target, a map made by `maxYear` (public domain). */
  def map(rec: MediaRecord, target: PlaceInfo, resolve: Resolve, maxYear: Int): Either[String, String] =
    rec.period match
      case None => Left("no date on the map")
      case Some((_, end)) if end > maxYear => Left(s"made after $maxYear")
      case Some(p) => geography(rec, target, resolve, specific = false).map(geo => s"$geo; map of ${years(p)}")

/** Shared reading helpers for the collection APIs. Pure. */
private[core] object Read:
  def str(c: ACursor, k: String): Option[String] =
    c.downField(k).focus.flatMap(j => j.asString.orElse(j.asNumber.map(_.toString))).map(_.trim).filter(_.nonEmpty)
  def strs(c: ACursor, k: String): List[String] =
    c.downField(k).focus.toList.flatMap(j =>
      j.asArray.map(_.toList.flatMap(_.asString)).getOrElse(j.asString.toList)).map(_.trim).filter(_.nonEmpty)
  def long(c: ACursor, k: String): Option[Long] =
    c.downField(k).focus.flatMap(j => j.asNumber.flatMap(_.toLong).orElse(j.asString.flatMap(_.trim.toLongOption)))
  def bool(c: ACursor, k: String): Option[Boolean] =
    c.downField(k).focus.flatMap(j => j.asBoolean.orElse(j.asString.map(_.trim.equalsIgnoreCase("true"))))
  def enc(s: String): String = URLEncoder.encode(s, UTF_8)
  def range(a: Option[Long], b: Option[Long]): Option[(Long, Long)] =
    (a, b) match
      case (Some(x), Some(y)) => Some((x.min(y), x.max(y)))
      case (Some(x), None)    => Some((x, x))
      case (None, Some(y))    => Some((y, y))
      case _                  => None

/** The Metropolitan Museum of Art Collection API (https://metmuseum.github.io/), no key. Search is
  * `/v1.1/search` (the Solr-backed `/v1/search` was retired on 2026-10-01: it answers 410), paged by
  * `limit`; an object is `/v1/objects/{id}`. Open Access objects carry `isPublicDomain: true` and are
  * released under CC0 — any other object is refused. The Met's years are astronomical (`-1399` is
  * 1400 BCE, as its "ca. 14th–13th century BCE" shows), so a year ≤ 0 is moved one back. Pure. */
object MetMuseum:

  /** Pure: the search for objects with images from a place: `q` and `geoLocation` both the name. */
  def searchUrl(api: String, name: String, limit: Int): String =
    s"${api.stripSuffix("/")}/v1.1/search?hasImages=true&geoLocation=${Read.enc(name)}&q=${Read.enc(name)}&limit=${limit.max(1)}"

  def objectUrl(api: String, id: Long): String = s"${api.stripSuffix("/")}/v1/objects/$id"

  /** Pure: the object ids of a search answer (`objectIDs` may be `null` when nothing matched). */
  def ids(json: Json): Either[String, List[Long]] =
    val c = json.hcursor
    Read.str(c, "message").filter(_ => c.downField("objectIDs").focus.isEmpty).map(Left(_)).getOrElse(
      Right(c.downField("objectIDs").focus.flatMap(_.asArray).toList.flatten.flatMap(_.asNumber.flatMap(_.toLong))))

  /** Pure: astronomical year → the corpus' signed year (0 → 1 BCE = -1, -1399 → -1400). */
  def year(astronomical: Long): Long = if astronomical <= 0 then astronomical - 1 else astronomical

  /** The geography fields, most specific first. */
  val GeographyFields: List[String] =
    List("excavation", "locus", "locale", "city", "county", "state", "subregion", "region", "river", "country")

  /** Pure: one object. Not public domain → its rights are a `Left`. */
  def record(json: Json): Either[String, MediaRecord] =
    val c = json.hcursor
    Read.long(c, "objectID").toRight(Read.str(c, "message").getOrElse("no objectID")).map { id =>
      val title  = Read.str(c, "title").orElse(Read.str(c, "objectName")).getOrElse(s"Object $id")
      val artist = Read.str(c, "artistDisplayName")
      val page   = Read.str(c, "objectURL").getOrElse(s"https://www.metmuseum.org/art/collection/search/$id")
      val credit = (List(Some(title), artist).flatten :+ "The Metropolitan Museum of Art" :+ "CC0").mkString(", ")
      val rights =
        if Read.bool(c, "isPublicDomain").contains(true) then Right(MediaRights.cc0(artist, credit))
        else Left("not Open Access (isPublicDomain is false)")
      val period = Read.range(Read.long(c, "objectBeginDate").map(year), Read.long(c, "objectEndDate").map(year))
        .filterNot(_ == (-1L, -1L))
      MediaRecord(id.toString, title, page, Read.str(c, "primaryImageSmall").orElse(Read.str(c, "primaryImage")), None,
        Read.str(c, "primaryImage"), rights, GeographyFields.flatMap(f => Read.str(c, f).map(f -> _)), period)
    }

/** The Cleveland Museum of Art Open Access API (https://openaccess-api.clevelandart.org/), no key. A
  * search answers the records themselves (`/api/artworks/?q=&cc0=1&has_image=1`). A record's
  * `share_license_status` is `CC0` for Open Access works; anything else is refused. Its place fields
  * are `find_spot` (an excavation site) and `culture` (a list whose plain entries are places of
  * origin — "Cyprus", "Egypt, Middle Kingdom"). Years are taken as given. Pure. */
object ClevelandArt:

  val Fields: String = List("id", "accession_number", "title", "share_license_status", "find_spot", "culture",
    "creation_date_earliest", "creation_date_latest", "url", "images", "creditline", "creators", "tombstone").mkString(",")

  def searchUrl(api: String, name: String, limit: Int): String =
    s"${api.stripSuffix("/")}/artworks/?q=${Read.enc(name)}&cc0=1&has_image=1&limit=${limit.max(1)}&fields=$Fields"

  /** Pure: the records of a search answer. */
  def records(json: Json): Either[String, List[Either[String, MediaRecord]]] =
    val c = json.hcursor
    c.downField("data").focus.flatMap(_.asArray) match
      case None => Left(Read.str(c, "detail").orElse(Read.str(c, "message")).getOrElse("no data in the answer"))
      case Some(rows) => Right(rows.toList.map(r => record(r)))

  def record(json: Json): Either[String, MediaRecord] =
    val c = json.hcursor
    Read.str(c, "accession_number").orElse(Read.str(c, "id")).toRight("no accession number").map { acc =>
      val title   = Read.str(c, "title").getOrElse(acc)
      val creator = c.downField("creators").downArray.downField("description").focus.flatMap(_.asString)
        .map(_.trim).filter(_.nonEmpty)
      val page    = Read.str(c, "url").getOrElse(s"https://clevelandart.org/art/$acc")
      val credit  = (List(Some(title), creator).flatten ++ List("The Cleveland Museum of Art") ++
        Read.str(c, "creditline").toList :+ "CC0").mkString(", ")
      val rights =
        if Read.str(c, "share_license_status").exists(_.equalsIgnoreCase("CC0")) then Right(MediaRights.cc0(creator, credit))
        else Left(s"not Open Access (share_license_status ${Read.str(c, "share_license_status").getOrElse("missing")})")
      val img = c.downField("images")
      def url(size: String) = img.downField(size).downField("url").focus.flatMap(_.asString)
      val geo = Read.str(c, "find_spot").map("find_spot" -> _).toList ++ Read.strs(c, "culture").map("culture" -> _)
      MediaRecord(acc, title, page, url("web"), None, url("print").orElse(url("full")), rights, geo,
        Read.range(Read.long(c, "creation_date_earliest"), Read.long(c, "creation_date_latest")))
    }

/** The Art Institute of Chicago API (https://api.artic.edu/docs/), no key. A search answers the
  * fields asked for; `is_public_domain` marks the works whose images are released under CC0 — any other
  * is refused. Images come from its IIIF server, `{iiif}/{image_id}/full/{w},/0/default.jpg` (843 px is
  * the size it caches and recommends). The place field is `place_of_origin`; years are signed already.
  * Pure. */
object ArtInstitute:

  val Fields: String = List("id", "title", "is_public_domain", "image_id", "place_of_origin", "date_start",
    "date_end", "artist_title", "credit_line").mkString(",")

  def searchUrl(api: String, name: String, limit: Int): String =
    s"${api.stripSuffix("/")}/artworks/search?q=${Read.enc(name)}&limit=${limit.max(1)}&fields=$Fields"

  def imageUrl(iiif: String, imageId: String, width: Int): String =
    s"${iiif.stripSuffix("/")}/${Read.enc(imageId)}/full/${width.max(1)},/0/default.jpg"

  def records(json: Json, iiif: String, displayPx: Int, thumbPx: Int): Either[String, List[Either[String, MediaRecord]]] =
    val c = json.hcursor
    c.downField("data").focus.flatMap(_.asArray) match
      case None => Left(Read.str(c, "detail").orElse(Read.str(c, "error")).getOrElse("no data in the answer"))
      case Some(rows) => Right(rows.toList.map(r => record(r, iiif, displayPx, thumbPx)))

  def record(json: Json, iiif: String, displayPx: Int, thumbPx: Int): Either[String, MediaRecord] =
    val c = json.hcursor
    Read.long(c, "id").toRight("no id").map { id =>
      val title  = Read.str(c, "title").getOrElse(s"Artwork $id")
      val artist = Read.str(c, "artist_title")
      val credit = (List(Some(title), artist).flatten :+ "The Art Institute of Chicago" :+ "CC0").mkString(", ")
      val image  = Read.str(c, "image_id")
      val rights =
        if !Read.bool(c, "is_public_domain").contains(true) then Left("not public domain (is_public_domain is false)")
        else Right(MediaRights.cc0(artist, credit))
      MediaRecord(id.toString, title, s"https://www.artic.edu/artworks/$id",
        image.map(imageUrl(iiif, _, displayPx)), image.map(imageUrl(iiif, _, thumbPx)),
        image.map(i => s"${iiif.stripSuffix("/")}/${Read.enc(i)}/full/full/0/default.jpg"), rights,
        Read.str(c, "place_of_origin").map("place_of_origin" -> _).toList,
        Read.range(Read.long(c, "date_start"), Read.long(c, "date_end")))
    }

/** The Library of Congress JSON API (https://www.loc.gov/apis/json-and-yaml/), no key: the maps
  * format (`/maps/?q=&fo=json`) narrowed to the Geography and Map Division, then each candidate's item
  * (`/item/{id}/?fo=json`) for its rights and files. A map is taken as public domain only when BOTH
  * its rights advisory says "No known restrictions" and its date is no later than `max-year` (1928: US
  * works published before 1929). Its `location` list is where it is of. Pure. */
object LibraryOfCongress:

  def searchUrl(base: String, name: String, limit: Int): String =
    s"${base.stripSuffix("/")}/maps/?q=${Read.enc(name)}&fo=json&c=${limit.max(1)}&fa=${Read.enc("partof:geography and map division")}"

  def itemUrl(base: String, id: String): String = s"${base.stripSuffix("/")}/item/${Read.enc(id)}/?fo=json"

  private val ItemId = """.*/item/([^/?#]+)/?.*""".r

  /** Pure: the item id of a result's `id`/`url` (`http://www.loc.gov/item/2007627513/` → `2007627513`). */
  def itemId(url: String): Option[String] = url match
    case ItemId(id) => Some(id)
    case _          => None

  /** Pure: the item ids of a search answer, in its order. */
  def results(json: Json): Either[String, List[String]] =
    val c = json.hcursor
    c.downField("results").focus.flatMap(_.asArray) match
      case None => Left("no results in the answer")
      case Some(rs) =>
        Right(rs.toList.flatMap { r =>
          val h = r.hcursor
          (Read.str(h, "id").toList ++ Read.str(h, "url").toList).flatMap(itemId).headOption
        }.distinct)

  /** Pure: the first year in a date string (`1890`, `[ca. 1880]`, `1906-1910` → its range). */
  def yearsOf(date: String): Option[(Long, Long)] =
    val ys = """(?<![0-9])([0-9]{4})(?![0-9])""".r.findAllMatchIn(date).map(_.group(1).toLong).toList
    ys.headOption.map(a => (ys.min, ys.max))

  private val NoKnown = "no known restrictions"

  /** Pure: one item answer. `maxYear` is the public-domain boundary; `displayPx`/`thumbPx` choose
    * among the item's JPEG files (the largest no wider than display, the smallest no narrower than thumb). */
  def record(json: Json, base: String, maxYear: Int, displayPx: Int, thumbPx: Int): Either[String, MediaRecord] =
    val item = json.hcursor.downField("item")
    if item.focus.isEmpty then Left("no item in the answer")
    else
      val c = item
      val id = Read.str(c, "id").flatMap(itemId).orElse(Read.str(c, "url").flatMap(itemId))
        .orElse(Read.str(c, "library_of_congress_control_number"))
      id.toRight("no item id").map { key =>
        val title    = Read.str(c, "title").getOrElse(key)
        val advisory = (Read.strs(c, "rights_advisory") ++ Read.strs(c, "rights")).mkString(" ")
        val date     = Read.str(c, "date").orElse(Read.strs(c, "dates").headOption)
        val period   = date.flatMap(yearsOf)
        val creator  = Read.strs(c, "contributor_names").headOption.orElse(Read.strs(c, "creator").headOption)
        val page     = s"${base.stripSuffix("/")}/item/$key/"
        val credit   = (List(Some(title), creator).flatten :+ "Library of Congress, Geography and Map Division").mkString(", ")
        val files = json.hcursor.downField("resources").focus.flatMap(_.asArray).toList.flatten.headOption.toList
          .flatMap(_.hcursor.downField("files").focus.flatMap(_.asArray).toList.flatten)
          .flatMap(_.asArray.toList.flatten)
          .flatMap { f =>
            val h = f.hcursor
            for
              u <- Read.str(h, "url")
              if Read.str(h, "mimetype").exists(_.equalsIgnoreCase("image/jpeg")) || u.toLowerCase(Locale.ROOT).contains(".jpg")
            yield (u, Read.long(h, "width").getOrElse(0L))
          }
        val display = files.filter(_._2 <= displayPx).sortBy(-_._2).headOption.orElse(files.sortBy(_._2).headOption).map(_._1)
        val thumb   = files.filter(_._2 >= thumbPx).sortBy(_._2).headOption.map(_._1)
        val original = files.sortBy(-_._2).headOption.map(_._1)
        val rights =
          if !advisory.toLowerCase(Locale.ROOT).contains(NoKnown) then
            Left(if advisory.isEmpty then "no rights statement" else "rights not \"No known restrictions\"")
          else if period.isEmpty then Left("no date on the map")
          else if period.exists(_._2 > maxYear) then Left(s"made after $maxYear (may be in copyright)")
          else Right(MediaRights.publicDomain(creator, credit, Some("https://www.loc.gov/legal/understanding-copyright/")))
        val geo = Read.strs(c, "location").map("location" -> _)
        MediaRecord(key, title, page, display, thumb, original, rights, geo, period)
      }

/** Wikimedia Commons files that DEPICT an event (#1446): structured data's `P180 depicts` searched by
  * CirrusSearch's documented `haswbstatement` keyword through the action API (no login), and
  * Wikidata's `P1299 depicted by` on the event's own item (a work whose own image, P18, is the
  * picture). Pure URL building and parsing. */
object CommonsDepicts:

  val Depicts    = "P180"
  val DepictedBy = "P1299"

  def searchUrl(api: String, qid: String, limit: Int, maxlag: Int): String =
    val lag = if maxlag > 0 then s"&maxlag=$maxlag" else ""
    s"$api?action=query&format=json&formatversion=2&list=search&srnamespace=6&srlimit=${limit.max(1)}" +
      s"&srsearch=${Read.enc(s"haswbstatement:$Depicts=$qid")}$lag"

  /** Pure: the file titles of a search answer, in its order; an API error is a `Left`. */
  def titles(json: Json): Either[String, List[String]] =
    val c = json.hcursor
    c.downField("error").focus match
      case Some(err) => Left(s"${err.hcursor.get[String]("code").getOrElse("error")}: ${err.hcursor.get[String]("info").getOrElse("")}".trim)
      case None =>
        Right(c.downField("query").downField("search").focus.flatMap(_.asArray).toList.flatten
          .flatMap(_.hcursor.get[String]("title").toOption).filter(_.startsWith("File:")))

  /** Pure: the items an entity names under `P1299 depicted by`, per entity asked, best rank first. */
  def depictedBy(json: Json): Map[String, List[String]] =
    json.hcursor.downField("entities").focus.flatMap(_.asObject).toList.flatMap(_.toList).map { (key, ent) =>
      val sts = ent.hcursor.downField("claims").downField(DepictedBy).focus.flatMap(_.asArray).toList.flatten
      val ids = sts.flatMap { st =>
        val s = st.hcursor
        val rank = s.get[String]("rank").getOrElse("normal")
        Option.when(rank != "deprecated")(s.downField("mainsnak").downField("datavalue").downField("value")
          .get[String]("id").toOption).flatten.filter(Wikidata.isQid).map(id => (if rank == "preferred" then 0 else 1, id))
      }
      key -> ids.sortBy(_._1).map(_._2).distinct
    }.toMap
