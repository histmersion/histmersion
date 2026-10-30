package com.histmersion.domain

import com.histmersion.values.{Period, Year}

import io.circe.Json

/** The Metropolitan Museum of Art Collection API (https://metmuseum.github.io/), no key. Search is `/v1.1/search` (the
  * Solr-backed `/v1/search` was retired on 2026-10-01: it answers 410), paged by `limit`; an object is
  * `/v1/objects/{id}`. Open Access objects carry `isPublicDomain: true` and are released under CC0 — any other object
  * is refused. The Met's years are astronomical (`-1399` is 1400 BCE, as its "ca. 14th–13th century BCE" shows), so a
  * year ≤ 0 is moved one back. Pure.
  */
object MetMuseum:

  /** Pure: the search for objects with images from a place: `q` and `geoLocation` both the name. */
  def searchUrl(api: String, name: String, limit: Int): String =
    s"${api.stripSuffix("/")}/v1.1/search?hasImages=true&geoLocation=${Read.enc(name)}&q=${Read.enc(name)}&limit=${limit.max(1)}"

  def objectUrl(api: String, id: Long): String = s"${api.stripSuffix("/")}/v1/objects/$id"

  /** Pure: the object ids of a search answer (`objectIDs` may be `null` when nothing matched). */
  def ids(json: Json): Either[String, List[Long]] =
    val c = json.hcursor
    Read
      .str(c, "message")
      .filter(_ => c.downField("objectIDs").focus.isEmpty)
      .map(Left(_))
      .getOrElse(
        Right(c.downField("objectIDs").focus.flatMap(_.asArray).toList.flatten.flatMap(_.asNumber.flatMap(_.toLong)))
      )

  /** Pure: astronomical year → the corpus' signed year (0 → 1 BCE = -1, -1399 → -1400). */
  def year(astronomical: Long): Long = if astronomical <= 0 then astronomical - 1 else astronomical

  /** The geography fields, most specific first. */
  val GeographyFields: List[String] =
    List("excavation", "locus", "locale", "city", "county", "state", "subregion", "region", "river", "country")

  /** Pure: one object. Not public domain → its rights are a `Left`. */
  def record(json: Json): Either[String, MediaRecord] =
    val c = json.hcursor
    Read.long(c, "objectID").toRight(Read.str(c, "message").getOrElse("no objectID")).map { id =>
      val title = Read.str(c, "title").orElse(Read.str(c, "objectName")).getOrElse(s"Object $id")
      val artist = Read.str(c, "artistDisplayName")
      val page = Read.str(c, "objectURL").getOrElse(s"https://www.metmuseum.org/art/collection/search/$id")
      val credit = (List(Some(title), artist).flatten :+ "The Metropolitan Museum of Art" :+ "CC0").mkString(", ")
      val rights =
        if Read.bool(c, "isPublicDomain").contains(true) then Right(MediaRights.cc0(artist, credit))
        else Left("not Open Access (isPublicDomain is false)")
      val period = Read
        .range(Read.long(c, "objectBeginDate").map(year), Read.long(c, "objectEndDate").map(year))
        .filterNot(_ == Period.point(Year(-1)))
      MediaRecord(
        id.toString,
        title,
        page,
        Read.str(c, "primaryImageSmall").orElse(Read.str(c, "primaryImage")),
        None,
        Read.str(c, "primaryImage"),
        rights,
        GeographyFields.flatMap(f => Read.str(c, f).map(f -> _)),
        period
      )
    }
