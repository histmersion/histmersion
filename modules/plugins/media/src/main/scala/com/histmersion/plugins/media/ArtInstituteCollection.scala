package com.histmersion
package plugins.media

import com.histmersion.plugin.*
import io.circe.Json
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** `aic`: the Art Institute of Chicago API (https://api.artic.edu/docs/), no key. A search answers the fields asked
  * for; `is_public_domain` marks the works whose images are released under CC0 — any other is refused. Images come from
  * its IIIF server, `{iiif}/{image_id}/full/{w},/0/default.jpg` (843 px is the size it caches and recommends). The
  * place field is `place_of_origin`; years are signed already.
  */
final class ArtInstituteCollection(conf: CollectionSettings, thumbPx: ArtInstituteCollection.ThumbPx)
    extends MuseumCollection(conf, MuseumCollection.Provider("The Art Institute of Chicago"), MuseumCollection.Key("media.aic")):
  val title = MediaCollection.TitleResult("Art Institute of Chicago objects")
  override val description = MediaCollection.DescriptionResult("The Art Institute of Chicago (CC0, is_public_domain; IIIF images)" + MetCollection.Museum)
  val role = MediaCollection.RoleResult(MediaCollection.Object)

  def search(place: MediaPlace, http: Plugin.Http): Either[MediaCollection.SearchResultPart1, List[Either[MediaCollection.SearchResultPart2Part1, MediaItem]]] =
    http
      .askJson(ArtInstituteCollection.searchUrl(Plugin.Http.GetUrl(conf.apiUrl.value), CollectionQuery.Text(place.label.value), conf.candidates))
      .left
      .map(e => MediaCollection.SearchResultPart1(e))
      .flatMap(ArtInstituteCollection.records(_, Plugin.Http.GetUrl(conf.imageUrl.value), conf.displayPx, thumbPx))

object ArtInstituteCollection:
  type ImageId = ImageId.T
  object ImageId extends RefinedType[String, Pure]
  type Width = Width.T
  object Width extends RefinedType[Int, Pure]

  type ThumbPx = MediaSettings.ThumbPx
  val ThumbPx: MediaSettings.ThumbPx.type = MediaSettings.ThumbPx

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

  def searchUrl(api: Plugin.Http.GetUrl, name: CollectionQuery.Text, limit: CollectionSettings.Candidates): Plugin.Http.GetUrl =
    Plugin.Http.GetUrl(s"${api.value.stripSuffix("/")}/artworks/search?q=${name.value.enc}&limit=${limit.value.max(1)}&fields=$Fields")

  def imageUrl(iiif: Plugin.Http.GetUrl, imageId: ImageId, width: Width): MediaItem.ImageUrl =
    MediaItem.ImageUrl(s"${iiif.value.stripSuffix("/")}/${imageId.value.enc}/full/${width.value.max(1)},/0/default.jpg")

  def records(json: Json, iiif: Plugin.Http.GetUrl, displayPx: CollectionSettings.DisplayPx, thumbPx: ThumbPx): Either[MediaCollection.SearchResultPart1, List[Either[MediaCollection.SearchResultPart2Part1, MediaItem]]] =
    val c = json.hcursor
    c.downField("data").focus.flatMap(_.asArray) match
      case None       => Left(MediaCollection.SearchResultPart1(c.str("detail").orElse(c.str("error")).getOrElse("no data in the answer")))
      case Some(rows) => Right(rows.toList.map(r => record(r, iiif, displayPx, thumbPx)))

  def record(json: Json, iiif: Plugin.Http.GetUrl, displayPx: CollectionSettings.DisplayPx, thumbPx: ThumbPx): Either[MediaCollection.SearchResultPart2Part1, MediaItem] =
    val c = json.hcursor
    c.long("id").toRight(MediaCollection.SearchResultPart2Part1("no id")).map { id =>
      val title = c.str("title").getOrElse(s"Artwork $id")
      val artist = c.str("artist_title")
      val credit = (List(Some(title), artist).flatten :+ "The Art Institute of Chicago" :+ "CC0").mkString(", ")
      val image = c.str("image_id")
      val rights =
        if !c.bool("is_public_domain").contains(true) then Left(MediaItem.RightsPart1("not public domain (is_public_domain is false)"))
        else Right(MediaRights.cc0(artist.map(MediaRights.Author(_)), MediaRights.Credit(credit)))
      MediaItem(
        MediaItem.Key(id.toString),
        MediaItem.Title(title),
        MediaItem.PageUrl(s"https://www.artic.edu/artworks/$id"),
        image.map(i => imageUrl(iiif, ImageId(i), Width(displayPx.value))),
        rights,
        thumbUrl = image.map(i => MediaItem.ThumbUrl(imageUrl(iiif, ImageId(i), Width(thumbPx.value)).value)),
        originalUrl = image.map(i => MediaItem.OriginalUrl(s"${iiif.value.stripSuffix("/")}/${i.enc}/full/full/0/default.jpg")),
        geography = c.str("place_of_origin").map(p => (MediaItem.GeographyPart1("place_of_origin"), MediaItem.GeographyPart2(p))).toList,
        period = Period.range(c.long("date_start"), c.long("date_end"))
      )
    }
