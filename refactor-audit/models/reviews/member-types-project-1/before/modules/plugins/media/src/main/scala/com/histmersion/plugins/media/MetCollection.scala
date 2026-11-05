package com.histmersion.plugins.media

import com.histmersion.plugin.*
import io.circe.Json

/** `met`: The Metropolitan Museum of Art Collection API (https://metmuseum.github.io/), no key. Search is
  * `/v1.1/search` (the Solr-backed `/v1/search` was retired on 2026-10-01: it answers 410), paged by `limit`, by the
  * place's name (as `q` and `geoLocation`); then each object, `/v1/objects/{id}`. Open Access objects carry
  * `isPublicDomain: true` and are released under CC0 — any other object is refused. The Met's years are astronomical
  * (`-1399` is 1400 BCE, as its "ca. 14th–13th century BCE" shows), so a year ≤ 0 is moved one back.
  */
final class MetCollection(conf: CollectionSettings)
    extends MuseumCollection(conf, "The Metropolitan Museum of Art", "media.met"):
  val title = "Met objects"
  override val description = "The Metropolitan Museum of Art (CC0, isPublicDomain)" + MetCollection.Museum
  val role = MediaCollection.Object

  def search(place: MediaPlace, http: Plugin.Http): Either[String, List[Either[String, MediaItem]]] =
    http
      .askJson(MetCollection.searchUrl(conf.apiUrl, place.label, conf.candidates))
      .flatMap(MetCollection.ids)
      .map(
        _.take(conf.candidates.max(0)).map(id =>
          http
            .askJson(MetCollection.objectUrl(conf.apiUrl, id))
            .flatMap(MetCollection.record)
            .left
            .map(e => s"object $id: $e")
        )
      )

object MetCollection:
  /** What every museum's description adds: what it takes and how a dry run shows it. */
  val Museum = " — Open Access objects only (as the record itself says), tied to a place on the map " +
    "when the object's most specific place field names it (and no other) and its dates overlap the place's; " +
    "--dry-run lists the matches with their reasons"

  /** Pure: the search for objects with images from a place: `q` and `geoLocation` both the name. */
  def searchUrl(api: String, name: String, limit: Int): String =
    s"${api.stripSuffix("/")}/v1.1/search?hasImages=true&geoLocation=${name.enc}&q=${name.enc}&limit=${limit.max(1)}"

  def objectUrl(api: String, id: Long): String = s"${api.stripSuffix("/")}/v1/objects/$id"

  /** Pure: the object ids of a search answer (`objectIDs` may be `null` when nothing matched). */
  def ids(json: Json): Either[String, List[Long]] =
    val c = json.hcursor
    c.str("message")
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
  def record(json: Json): Either[String, MediaItem] =
    val c = json.hcursor
    c.long("objectID").toRight(c.str("message").getOrElse("no objectID")).map { id =>
      val title = c.str("title").orElse(c.str("objectName")).getOrElse(s"Object $id")
      val artist = c.str("artistDisplayName")
      val page = c.str("objectURL").getOrElse(s"https://www.metmuseum.org/art/collection/search/$id")
      val credit = (List(Some(title), artist).flatten :+ "The Metropolitan Museum of Art" :+ "CC0").mkString(", ")
      val rights =
        if c.bool("isPublicDomain").contains(true) then Right(MediaRights.cc0(artist, credit))
        else Left("not Open Access (isPublicDomain is false)")
      val period = Period
        .range(c.long("objectBeginDate").map(year), c.long("objectEndDate").map(year))
        .filterNot(_ == Period.point(Year(-1)))
      MediaItem(
        id.toString,
        title,
        page,
        c.str("primaryImageSmall").orElse(c.str("primaryImage")),
        rights,
        originalUrl = c.str("primaryImage"),
        geography = GeographyFields.flatMap(f => c.str(f).map(f -> _)),
        period = period
      )
    }
