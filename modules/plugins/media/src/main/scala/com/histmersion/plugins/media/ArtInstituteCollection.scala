package com.histmersion.plugins.media

import com.histmersion.plugin.*
import io.circe.Json

/** `aic`: the Art Institute of Chicago API (https://api.artic.edu/docs/), no key. A search answers the fields asked
  * for; `is_public_domain` marks the works whose images are released under CC0 — any other is refused. Images come from
  * its IIIF server, `{iiif}/{image_id}/full/{w},/0/default.jpg` (843 px is the size it caches and recommends). The
  * place field is `place_of_origin`; years are signed already.
  */
final class ArtInstituteCollection(conf: CollectionSettings, thumbPx: ArtInstituteCollection.ThumbPx)
    extends MuseumCollection(conf, com.histmersion.plugins.media.MuseumCollection.Provider("The Art Institute of Chicago"), com.histmersion.plugins.media.MuseumCollection.Key("media.aic")):
  val title = com.histmersion.plugin.MediaCollection.TitleResult("Art Institute of Chicago objects")
  override val description = "The Art Institute of Chicago (CC0, is_public_domain; IIIF images)" + MetCollection.Museum
  val role = MediaCollection.Object

  def search(place: MediaPlace, http: Plugin.Http): Either[com.histmersion.plugins.media.ArtInstituteCollection.Problem, List[Either[com.histmersion.plugin.MediaItem.Key, MediaItem]]] =
    http
      .askJson(ArtInstituteCollection.searchUrl(conf.apiUrl, place.label, conf.candidates))
      .flatMap(ArtInstituteCollection.records(_, conf.imageUrl, conf.displayPx, thumbPx))

object ArtInstituteCollection:
  type ImageId = ImageId.T
  object ImageId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type ThumbPx = ThumbPx.T
  object ThumbPx extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type Fields = FieldsValue
  type FieldsValue = FieldsValue.T
  object FieldsValue extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SearchUrlLimit = SearchUrlLimit.T
  object SearchUrlLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ImageUrlWidth = ImageUrlWidth.T
  object ImageUrlWidth extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RecordsIiif = _root_.com.histmersion.plugin.Plugin.Http.GetUrl
  val RecordsIiif: _root_.com.histmersion.plugin.Plugin.Http.GetUrl.type = _root_.com.histmersion.plugin.Plugin.Http.GetUrl
  type RecordsDisplayPx = _root_.com.histmersion.plugins.media.CollectionSettings.DisplayPx
  val RecordsDisplayPx: _root_.com.histmersion.plugins.media.CollectionSettings.DisplayPx.type = _root_.com.histmersion.plugins.media.CollectionSettings.DisplayPx
  type RecordsThumbPx = _root_.com.histmersion.plugins.media.ArtInstituteCollection.ThumbPx
  val RecordsThumbPx: _root_.com.histmersion.plugins.media.ArtInstituteCollection.ThumbPx.type = _root_.com.histmersion.plugins.media.ArtInstituteCollection.ThumbPx
  type RecordIiif = _root_.com.histmersion.plugin.Plugin.Http.GetUrl
  val RecordIiif: _root_.com.histmersion.plugin.Plugin.Http.GetUrl.type = _root_.com.histmersion.plugin.Plugin.Http.GetUrl

  val Fields: com.histmersion.plugins.media.ArtInstituteCollection.Fields = List(
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

  def searchUrl(api: com.histmersion.plugin.Plugin.Http.GetUrl, name: com.histmersion.plugin.CollectionQuery.Text, limit: com.histmersion.plugins.media.ArtInstituteCollection.SearchUrlLimit): com.histmersion.plugin.Plugin.Http.GetUrl =
    s"${api.stripSuffix("/")}/artworks/search?q=${name.enc}&limit=${limit.max(1)}&fields=$Fields"

  def imageUrl(iiif: com.histmersion.plugin.Plugin.Http.GetUrl, imageId: com.histmersion.plugins.media.ArtInstituteCollection.ImageId, width: com.histmersion.plugins.media.ArtInstituteCollection.ImageUrlWidth): com.histmersion.plugin.Plugin.Http.GetUrl =
    s"${iiif.stripSuffix("/")}/${imageId.enc}/full/${width.max(1)},/0/default.jpg"

  def records(json: Json, iiif: com.histmersion.plugins.media.ArtInstituteCollection.RecordsIiif, displayPx: com.histmersion.plugins.media.ArtInstituteCollection.RecordsDisplayPx, thumbPx: com.histmersion.plugins.media.ArtInstituteCollection.RecordsThumbPx): Either[com.histmersion.plugins.media.ArtInstituteCollection.Problem, List[Either[com.histmersion.plugin.MediaItem.Key, MediaItem]]] =
    val c = json.hcursor
    c.downField("data").focus.flatMap(_.asArray) match
      case None       => Left(c.str("detail").orElse(c.str("error")).getOrElse("no data in the answer"))
      case Some(rows) => Right(rows.toList.map(r => record(r, iiif, displayPx, thumbPx)))

  def record(json: Json, iiif: com.histmersion.plugins.media.ArtInstituteCollection.RecordIiif, displayPx: com.histmersion.plugins.media.CollectionSettings.DisplayPx, thumbPx: com.histmersion.plugins.media.ArtInstituteCollection.ThumbPx): Either[com.histmersion.plugins.media.ArtInstituteCollection.Problem, MediaItem] =
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
