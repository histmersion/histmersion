package com.histmersion
package plugins.media
import com.histmersion.util.json.RawJson
import com.histmersion.util.json.RawJson.*
import com.histmersion.util.json.JsonCodec.given
import hearth.kindlings.jsoniterjson.Json
import com.histmersion.util.problem.Problem

import com.histmersion.plugin.*

/** `cleveland`: The Cleveland Museum of Art Open Access API (https://openaccess-api.clevelandart.org/), no key. A
  * search answers the records themselves (`/api/artworks/?q=&cc0=1&has_image=1`). A record's `share_license_status` is
  * `CC0` for Open Access works; anything else is refused. Its place fields are `find_spot` (an excavation site) and
  * `culture` (a list whose plain entries are places of origin — "Cyprus", "Egypt, Middle Kingdom"). Years are taken as
  * given.
  */
final class ClevelandCollection(conf: CollectionSettings)
    extends MuseumCollection(
      conf,
      MuseumCollection.Provider("The Cleveland Museum of Art"),
      MuseumCollection.Key("media.cleveland")
    ):
  val title = MediaCollection.Title("Cleveland Museum of Art objects")
  override val description =
    MediaCollection.Description("The Cleveland Museum of Art (CC0, share_license_status)" + MetCollection.Museum)
  val role = MediaCollection.Object

  def search(
      place: MediaPlace,
      http: Plugin.Http
  ): Either[Problem, List[Either[Problem, MediaItem]]] =
    http
      .askJson(
        ClevelandCollection
          .searchUrl(Plugin.Http.Url(conf.apiUrl.value), CollectionQuery.Text(place.label.value), conf.candidates)
      )
      .flatMap(j => ClevelandCollection.records(j).unreadableEach)

object ClevelandCollection:

  /** The plugin's id: what its problems name. */
  val Name = "media-cleveland"
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

  def searchUrl(
      api: Plugin.Http.Url,
      name: CollectionQuery.Text,
      limit: CollectionSettings.Candidates
  ): Plugin.Http.Url =
    Plugin.Http.Url(
      s"${api.value.stripSuffix("/")}/artworks/?q=${name.value.enc}&cc0=1&has_image=1&limit=${limit.value.max(1)}&fields=$Fields"
    )

  /** Pure: the records of a search answer. */
  def records(
      json: Json
  ): Either[Problem, List[Either[Problem, MediaItem]]] =
    val c = json.cursor
    c.downField("data").focus.flatMap(_.asArray) match
      case None =>
        Left(
          c.str("detail")
            .orElse(c.str("message"))
            .fold(MediaPlugin.missing("data"))(MediaPlugin.said(Name, Problem.Plugin.Claim.Failed, _))
        )
      case Some(rows) => Right(rows.toList.map(record))

  def record(json: Json): Either[Problem, MediaItem] =
    val c = json.cursor
    c.str("accession_number")
      .orElse(c.str("id"))
      .toRight(MediaPlugin.missing("accession_number"))
      .map { acc =>
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
          if c.str("share_license_status").exists(_.equalsIgnoreCase("CC0")) then
            Right(MediaRights.cc0(creator.map(MediaRights.Author(_)), MediaRights.Credit(credit)))
          else Left(MediaPlugin.rejected(Name, acc, Problem.Plugin.Refusal.Restricted))
        val img = c.downField("images")
        def url(size: String) = img.downField(size).downField("url").focus.flatMap(_.asString)
        val geo = c.str("find_spot").map("find_spot" -> _).toList ++ c.strs("culture").map("culture" -> _)
        MediaItem(
          MediaItem.Key(acc),
          MediaItem.Title(title),
          MediaItem.PageUrl(page),
          url("web").map(u => MediaItem.ImageUrl(u)),
          rights,
          originalUrl = url("print").orElse(url("full")).map(u => MediaItem.OriginalUrl(u)),
          geography = geo.map((f, v) => (MediaItem.Geography.Field(f), MediaItem.Geography.Value(v))),
          period = Period.range(c.long("creation_date_earliest"), c.long("creation_date_latest"))
        )
      }
