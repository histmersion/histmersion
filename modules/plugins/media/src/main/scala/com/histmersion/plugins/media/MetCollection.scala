package com.histmersion
package plugins.media
import com.histmersion.util.json.RawJson
import com.histmersion.util.json.RawJson.*
import com.histmersion.util.json.JsonCodec.given
import hearth.kindlings.jsoniterjson.Json
import com.histmersion.util.problem.{Legacy, Problem}

import com.histmersion.plugin.*
import io.github.iltotore.iron.constraint.any.Pure

/** `met`: The Metropolitan Museum of Art Collection API (https://metmuseum.github.io/), no key. Search is
  * `/v1.1/search` (the Solr-backed `/v1/search` was retired on 2026-10-01: it answers 410), paged by `limit`, by the
  * place's name (as `q` and `geoLocation`); then each object, `/v1/objects/{id}`. Open Access objects carry
  * `isPublicDomain: true` and are released under CC0 — any other object is refused. The Met's years are astronomical
  * (`-1399` is 1400 BCE, as its "ca. 14th–13th century BCE" shows), so a year ≤ 0 is moved one back.
  */
final class MetCollection(conf: CollectionSettings)
    extends MuseumCollection(
      conf,
      MuseumCollection.Provider("The Metropolitan Museum of Art"),
      MuseumCollection.Key("media.met")
    ):
  val title = MediaCollection.Title("Met objects")
  override val description =
    MediaCollection.Description("The Metropolitan Museum of Art (CC0, isPublicDomain)" + MetCollection.Museum)
  val role = MediaCollection.Object

  def search(
      place: MediaPlace,
      http: Plugin.Http
  ): Either[Problem, List[Either[Problem, MediaItem]]] =
    http
      .askJson(MetCollection.searchUrl(conf.apiUrl, place.label, conf.candidates))
      .flatMap(j => MetCollection.ids(j).unreadable)
      .map(
        _.take(conf.candidates.value.max(0)).map(id =>
          http
            .askJson(MetCollection.objectUrl(conf.apiUrl, id))
            .flatMap(j => MetCollection.record(j).unreadable)
            .left
            .map(_.within(Problem.Field(s"object $id")))
        )
      )

object MetCollection:
  type ObjectId = ObjectId.T
  object ObjectId extends Newtype[Long, Pure]
  type Year = Year.T
  object Year extends Newtype[Long, Pure]

  /** What every museum's description adds: what it takes and how a dry run shows it. */
  val Museum = " — Open Access objects only (as the record itself says), tied to a place on the map " +
    "when the object's most specific place field names it (and no other) and its dates overlap the place's; " +
    "--dry-run lists the matches with their reasons"

  /** Pure: the search for objects with images from a place: `q` and `geoLocation` both the name. */
  def searchUrl(
      api: CollectionSettings.ApiUrl,
      name: MediaPlace.Label,
      limit: CollectionSettings.Candidates
  ): Plugin.Http.Url =
    Plugin.Http.Url(
      s"${api.value.stripSuffix("/")}/v1.1/search?hasImages=true&geoLocation=${name.value.enc}&q=${name.value.enc}&limit=${limit.value.max(1)}"
    )

  def objectUrl(api: CollectionSettings.ApiUrl, id: ObjectId): Plugin.Http.Url =
    Plugin.Http.Url(s"${api.value.stripSuffix("/")}/v1/objects/$id")

  /** Pure: the object ids of a search answer (`objectIDs` may be `null` when nothing matched). */
  def ids(json: Json): Either[Problem, List[ObjectId]] =
    val c = json.cursor
    c.str("message")
      .filter(_ => c.downField("objectIDs").focus.isEmpty)
      .map(m => Left(Problem.Unclassified(Legacy.Text(m))))
      .getOrElse(
        Right(
          c.downField("objectIDs")
            .focus
            .flatMap(_.asArray)
            .toList
            .flatten
            .flatMap(_.asNumber.flatMap(_.asLong))
            .map(ObjectId(_))
        )
      )

  /** Pure: astronomical year → the corpus' signed year (0 → 1 BCE = -1, -1399 → -1400). */
  def year(astronomical: Year): Year = if astronomical.value <= 0 then Year(astronomical.value - 1) else astronomical

  /** The geography fields, most specific first. */
  val GeographyFields: List[MediaItem.Geography.Field] =
    List("excavation", "locus", "locale", "city", "county", "state", "subregion", "region", "river", "country").map(
      MediaItem.Geography.Field(_)
    )

  /** Pure: one object. Not public domain → its rights are a `Left`. */
  def record(json: Json): Either[Problem, MediaItem] =
    val c = json.cursor
    c.long("objectID").toRight(Problem.Unclassified(Legacy.Text(c.str("message").getOrElse("no objectID")))).map { id =>
      val title = c.str("title").orElse(c.str("objectName")).getOrElse(s"Object $id")
      val artist = c.str("artistDisplayName")
      val page = c.str("objectURL").getOrElse(s"https://www.metmuseum.org/art/collection/search/$id")
      val credit = (List(Some(title), artist).flatten :+ "The Metropolitan Museum of Art" :+ "CC0").mkString(", ")
      val rights: Either[Problem, MediaRights] =
        if c.bool("isPublicDomain").contains(true) then
          Right(MediaRights.cc0(artist.map(MediaRights.Author(_)), MediaRights.Credit(credit)))
        else Left(Problem.Unclassified(Legacy.Text("not Open Access (isPublicDomain is false)")))
      val period = Period
        .range(
          c.long("objectBeginDate").map(d => year(Year(d)).value),
          c.long("objectEndDate").map(d => year(Year(d)).value)
        )
        .filterNot(_ == Period.point(com.histmersion.plugin.Year(-1)))
      MediaItem(
        MediaItem.Key(id.toString),
        MediaItem.Title(title),
        MediaItem.PageUrl(page),
        c.str("primaryImageSmall").orElse(c.str("primaryImage")).map(MediaItem.ImageUrl(_)),
        rights,
        originalUrl = c.str("primaryImage").map(MediaItem.OriginalUrl(_)),
        geography = GeographyFields.flatMap(f => c.str(f.value).map(v => f -> MediaItem.Geography.Value(v))),
        period = period
      )
    }
