package com.histmersion.plugins.media

import com.histmersion.plugin.*
import io.circe.Json

/** `cleveland`: The Cleveland Museum of Art Open Access API (https://openaccess-api.clevelandart.org/), no key. A
  * search answers the records themselves (`/api/artworks/?q=&cc0=1&has_image=1`). A record's `share_license_status` is
  * `CC0` for Open Access works; anything else is refused. Its place fields are `find_spot` (an excavation site) and
  * `culture` (a list whose plain entries are places of origin — "Cyprus", "Egypt, Middle Kingdom"). Years are taken as
  * given.
  */
final class ClevelandCollection(conf: CollectionSettings)
    extends MuseumCollection(conf, "The Cleveland Museum of Art", "media.cleveland"):
  val title = "Cleveland Museum of Art objects"
  override val description = "The Cleveland Museum of Art (CC0, share_license_status)" + MetCollection.Museum
  val role = MediaCollection.Object

  def search(place: MediaPlace, http: Plugin.Http): Either[com.histmersion.plugins.media.ClevelandCollection.Problem, List[Either[com.histmersion.plugin.MediaItem.Key, MediaItem]]] =
    http
      .askJson(ClevelandCollection.searchUrl(conf.apiUrl, place.label, conf.candidates))
      .flatMap(ClevelandCollection.records)

object ClevelandCollection:
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Size = Size.T
  object Size extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  type Fields = FieldsValue
  type FieldsValue = FieldsValue.T
  object FieldsValue extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SearchUrlLimit = SearchUrlLimit.T
  object SearchUrlLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val Fields: com.histmersion.plugins.media.ClevelandCollection.Fields = List(
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

  def searchUrl(api: com.histmersion.plugin.Plugin.Http.GetUrl, name: com.histmersion.plugin.CollectionQuery.Text, limit: com.histmersion.plugins.media.ClevelandCollection.SearchUrlLimit): com.histmersion.plugin.Plugin.Http.GetUrl =
    s"${api.stripSuffix("/")}/artworks/?q=${name.enc}&cc0=1&has_image=1&limit=${limit.max(1)}&fields=$Fields"

  /** Pure: the records of a search answer. */
  def records(json: Json): Either[com.histmersion.plugins.media.ClevelandCollection.Problem, List[Either[com.histmersion.plugin.MediaItem.Key, MediaItem]]] =
    val c = json.hcursor
    c.downField("data").focus.flatMap(_.asArray) match
      case None       => Left(c.str("detail").orElse(c.str("message")).getOrElse("no data in the answer"))
      case Some(rows) => Right(rows.toList.map(record))

  def record(json: Json): Either[com.histmersion.plugins.media.ClevelandCollection.Problem, MediaItem] =
    val c = json.hcursor
    c.str("accession_number").orElse(c.str("id")).toRight("no accession number").map { acc =>
      val title = c.str("title").getOrElse(acc)
      val creator = c
        .downField("creators")
        .downArray
        .downField("description")
        .focus
        .flatMap(_.asString)
        .map(_.trim)
        .filter(_.nonEmpty)
      val page = c.str("url").getOrElse(s"https://clevelandart.org/art/$acc")
      val credit = (List(Some(title), creator).flatten ++ List("The Cleveland Museum of Art") ++
        c.str("creditline").toList :+ "CC0").mkString(", ")
      val rights =
        if c.str("share_license_status").exists(_.equalsIgnoreCase("CC0")) then Right(MediaRights.cc0(creator, credit))
        else Left(s"not Open Access (share_license_status ${c.str("share_license_status").getOrElse("missing")})")
      val img = c.downField("images")
      def url(size: com.histmersion.plugins.media.ClevelandCollection.Size) = img.downField(size).downField("url").focus.flatMap(_.asString)
      val geo = c.str("find_spot").map("find_spot" -> _).toList ++ c.strs("culture").map("culture" -> _)
      MediaItem(
        acc,
        title,
        page,
        url("web"),
        rights,
        originalUrl = url("print").orElse(url("full")),
        geography = geo,
        period = Period.range(c.long("creation_date_earliest"), c.long("creation_date_latest"))
      )
    }
