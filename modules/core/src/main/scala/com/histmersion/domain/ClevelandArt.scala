package com.histmersion.domain

import io.circe.Json

/** The Cleveland Museum of Art Open Access API (https://openaccess-api.clevelandart.org/), no key. A search answers the
  * records themselves (`/api/artworks/?q=&cc0=1&has_image=1`). A record's `share_license_status` is `CC0` for Open
  * Access works; anything else is refused. Its place fields are `find_spot` (an excavation site) and `culture` (a list
  * whose plain entries are places of origin — "Cyprus", "Egypt, Middle Kingdom"). Years are taken as given. Pure.
  */
object ClevelandArt:

  val Fields: String = List(
    "id",
    "accession_number",
    "title",
    "share_license_status",
    "find_spot",
    "culture",
    "creation_date_earliest",
    "creation_date_latest",
    "url",
    "images",
    "creditline",
    "creators",
    "tombstone"
  ).mkString(",")

  def searchUrl(api: String, name: String, limit: Int): String =
    s"${api.stripSuffix("/")}/artworks/?q=${Read.enc(name)}&cc0=1&has_image=1&limit=${limit.max(1)}&fields=$Fields"

  /** Pure: the records of a search answer. */
  def records(json: Json): Either[String, List[Either[String, MediaRecord]]] =
    val c = json.hcursor
    c.downField("data").focus.flatMap(_.asArray) match
      case None       => Left(Read.str(c, "detail").orElse(Read.str(c, "message")).getOrElse("no data in the answer"))
      case Some(rows) => Right(rows.toList.map(r => record(r)))

  def record(json: Json): Either[String, MediaRecord] =
    val c = json.hcursor
    Read.str(c, "accession_number").orElse(Read.str(c, "id")).toRight("no accession number").map { acc =>
      val title = Read.str(c, "title").getOrElse(acc)
      val creator = c
        .downField("creators")
        .downArray
        .downField("description")
        .focus
        .flatMap(_.asString)
        .map(_.trim)
        .filter(_.nonEmpty)
      val page = Read.str(c, "url").getOrElse(s"https://clevelandart.org/art/$acc")
      val credit = (List(Some(title), creator).flatten ++ List("The Cleveland Museum of Art") ++
        Read.str(c, "creditline").toList :+ "CC0").mkString(", ")
      val rights =
        if Read.str(c, "share_license_status").exists(_.equalsIgnoreCase("CC0")) then
          Right(MediaRights.cc0(creator, credit))
        else Left(s"not Open Access (share_license_status ${Read.str(c, "share_license_status").getOrElse("missing")})")
      val img = c.downField("images")
      def url(size: String) = img.downField(size).downField("url").focus.flatMap(_.asString)
      val geo = Read.str(c, "find_spot").map("find_spot" -> _).toList ++ Read.strs(c, "culture").map("culture" -> _)
      MediaRecord(
        acc,
        title,
        page,
        url("web"),
        None,
        url("print").orElse(url("full")),
        rights,
        geo,
        Read.range(Read.long(c, "creation_date_earliest"), Read.long(c, "creation_date_latest"))
      )
    }
