package com.histmersion.plugins.media

import com.histmersion.plugin.*
import io.circe.Json

/** `aic`: the Art Institute of Chicago API (https://api.artic.edu/docs/), no key. A search answers the fields asked
  * for; `is_public_domain` marks the works whose images are released under CC0 — any other is refused. Images come from
  * its IIIF server, `{iiif}/{image_id}/full/{w},/0/default.jpg` (843 px is the size it caches and recommends). The
  * place field is `place_of_origin`; years are signed already.
  */
final class ArtInstituteCollection(conf: CollectionSettings, thumbPx: Int)
    extends MuseumCollection(conf, "The Art Institute of Chicago", "media.aic"):
  val title = "Art Institute of Chicago objects"
  override val description = "The Art Institute of Chicago (CC0, is_public_domain; IIIF images)" + MetCollection.Museum
  val role = MediaCollection.Object

  def search(place: MediaPlace, http: PluginHttp): Either[String, List[Either[String, MediaItem]]] =
    http
      .askJson(ArtInstituteCollection.searchUrl(conf.apiUrl, place.label, conf.candidates))
      .flatMap(ArtInstituteCollection.records(_, conf.imageUrl, conf.displayPx, thumbPx))

object ArtInstituteCollection:

  val Fields: String = List(
    "id",
    "title",
    "is_public_domain",
    "image_id",
    "place_of_origin",
    "date_start",
    "date_end",
    "artist_title",
    "credit_line"
  ).mkString(",")

  def searchUrl(api: String, name: String, limit: Int): String =
    s"${api.stripSuffix("/")}/artworks/search?q=${name.enc}&limit=${limit.max(1)}&fields=$Fields"

  def imageUrl(iiif: String, imageId: String, width: Int): String =
    s"${iiif.stripSuffix("/")}/${imageId.enc}/full/${width.max(1)},/0/default.jpg"

  def records(json: Json, iiif: String, displayPx: Int, thumbPx: Int): Either[String, List[Either[String, MediaItem]]] =
    val c = json.hcursor
    c.downField("data").focus.flatMap(_.asArray) match
      case None       => Left(c.str("detail").orElse(c.str("error")).getOrElse("no data in the answer"))
      case Some(rows) => Right(rows.toList.map(r => record(r, iiif, displayPx, thumbPx)))

  def record(json: Json, iiif: String, displayPx: Int, thumbPx: Int): Either[String, MediaItem] =
    val c = json.hcursor
    c.long("id").toRight("no id").map { id =>
      val title = c.str("title").getOrElse(s"Artwork $id")
      val artist = c.str("artist_title")
      val credit = (List(Some(title), artist).flatten :+ "The Art Institute of Chicago" :+ "CC0").mkString(", ")
      val image = c.str("image_id")
      val rights =
        if !c.bool("is_public_domain").contains(true) then Left("not public domain (is_public_domain is false)")
        else Right(MediaRights.cc0(artist, credit))
      MediaItem(
        id.toString,
        title,
        s"https://www.artic.edu/artworks/$id",
        image.map(imageUrl(iiif, _, displayPx)),
        rights,
        thumbUrl = image.map(imageUrl(iiif, _, thumbPx)),
        originalUrl = image.map(i => s"${iiif.stripSuffix("/")}/${i.enc}/full/full/0/default.jpg"),
        geography = c.str("place_of_origin").map("place_of_origin" -> _).toList,
        period = Period.range(c.long("date_start"), c.long("date_end"))
      )
    }
