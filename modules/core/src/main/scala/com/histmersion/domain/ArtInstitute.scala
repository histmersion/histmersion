package com.histmersion.domain

import io.circe.Json

/** The Art Institute of Chicago API (https://api.artic.edu/docs/), no key. A search answers the fields asked for;
  * `is_public_domain` marks the works whose images are released under CC0 — any other is refused. Images come from its
  * IIIF server, `{iiif}/{image_id}/full/{w},/0/default.jpg` (843 px is the size it caches and recommends). The place
  * field is `place_of_origin`; years are signed already. Pure.
  */
object ArtInstitute:

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
    s"${api.stripSuffix("/")}/artworks/search?q=${Read.enc(name)}&limit=${limit.max(1)}&fields=$Fields"

  def imageUrl(iiif: String, imageId: String, width: Int): String =
    s"${iiif.stripSuffix("/")}/${Read.enc(imageId)}/full/${width.max(1)},/0/default.jpg"

  def records(
      json: Json,
      iiif: String,
      displayPx: Int,
      thumbPx: Int
  ): Either[String, List[Either[String, MediaRecord]]] =
    val c = json.hcursor
    c.downField("data").focus.flatMap(_.asArray) match
      case None       => Left(Read.str(c, "detail").orElse(Read.str(c, "error")).getOrElse("no data in the answer"))
      case Some(rows) => Right(rows.toList.map(r => record(r, iiif, displayPx, thumbPx)))

  def record(json: Json, iiif: String, displayPx: Int, thumbPx: Int): Either[String, MediaRecord] =
    val c = json.hcursor
    Read.long(c, "id").toRight("no id").map { id =>
      val title = Read.str(c, "title").getOrElse(s"Artwork $id")
      val artist = Read.str(c, "artist_title")
      val credit = (List(Some(title), artist).flatten :+ "The Art Institute of Chicago" :+ "CC0").mkString(", ")
      val image = Read.str(c, "image_id")
      val rights =
        if !Read.bool(c, "is_public_domain").contains(true) then Left("not public domain (is_public_domain is false)")
        else Right(MediaRights.cc0(artist, credit))
      MediaRecord(
        id.toString,
        title,
        s"https://www.artic.edu/artworks/$id",
        image.map(imageUrl(iiif, _, displayPx)),
        image.map(imageUrl(iiif, _, thumbPx)),
        image.map(i => s"${iiif.stripSuffix("/")}/${Read.enc(i)}/full/full/0/default.jpg"),
        rights,
        Read.str(c, "place_of_origin").map("place_of_origin" -> _).toList,
        Read.range(Read.long(c, "date_start"), Read.long(c, "date_end"))
      )
    }
