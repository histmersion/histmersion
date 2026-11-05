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

  def search(place: MediaPlace, http: Plugin.Http): Either[com.histmersion.plugins.media.ArtInstituteCollection.SearchResultPart1, List[Either[com.histmersion.plugins.media.ArtInstituteCollection.SearchResultPart2Part1, MediaItem]]] =
    http
      .askJson(ArtInstituteCollection.searchUrl(conf.apiUrl, place.label, conf.candidates))
      .flatMap(ArtInstituteCollection.records(_, conf.imageUrl, conf.displayPx, thumbPx))

object ArtInstituteCollection:

  type SearchResultPart1 = SearchResultPart1.T
  object SearchResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SearchResultPart2Part1 = SearchResultPart2Part1.T
  object SearchResultPart2Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Fields = Fields.T
  object Fields extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SearchUrlResult = SearchUrlResult.T
  object SearchUrlResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SearchUrlApi = SearchUrlApi.T
  object SearchUrlApi extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SearchUrlName = SearchUrlName.T
  object SearchUrlName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SearchUrlLimit = SearchUrlLimit.T
  object SearchUrlLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ImageUrlResult = ImageUrlResult.T
  object ImageUrlResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ImageUrlIiif = ImageUrlIiif.T
  object ImageUrlIiif extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ImageUrlImageId = ImageUrlImageId.T
  object ImageUrlImageId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ImageUrlWidth = ImageUrlWidth.T
  object ImageUrlWidth extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RecordsResultPart1 = RecordsResultPart1.T
  object RecordsResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RecordsResultPart2Part1 = RecordsResultPart2Part1.T
  object RecordsResultPart2Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RecordsIiif = RecordsIiif.T
  object RecordsIiif extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RecordsDisplayPx = RecordsDisplayPx.T
  object RecordsDisplayPx extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RecordsThumbPx = RecordsThumbPx.T
  object RecordsThumbPx extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RecordResultPart1 = RecordResultPart1.T
  object RecordResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RecordIiif = RecordIiif.T
  object RecordIiif extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RecordDisplayPx = RecordDisplayPx.T
  object RecordDisplayPx extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RecordThumbPx = RecordThumbPx.T
  object RecordThumbPx extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

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

  def searchUrl(api: com.histmersion.plugins.media.ArtInstituteCollection.SearchUrlApi, name: com.histmersion.plugins.media.ArtInstituteCollection.SearchUrlName, limit: com.histmersion.plugins.media.ArtInstituteCollection.SearchUrlLimit): com.histmersion.plugins.media.ArtInstituteCollection.SearchUrlResult =
    s"${api.stripSuffix("/")}/artworks/search?q=${name.enc}&limit=${limit.max(1)}&fields=$Fields"

  def imageUrl(iiif: com.histmersion.plugins.media.ArtInstituteCollection.ImageUrlIiif, imageId: com.histmersion.plugins.media.ArtInstituteCollection.ImageUrlImageId, width: com.histmersion.plugins.media.ArtInstituteCollection.ImageUrlWidth): com.histmersion.plugins.media.ArtInstituteCollection.ImageUrlResult =
    s"${iiif.stripSuffix("/")}/${imageId.enc}/full/${width.max(1)},/0/default.jpg"

  def records(json: Json, iiif: com.histmersion.plugins.media.ArtInstituteCollection.RecordsIiif, displayPx: com.histmersion.plugins.media.ArtInstituteCollection.RecordsDisplayPx, thumbPx: com.histmersion.plugins.media.ArtInstituteCollection.RecordsThumbPx): Either[com.histmersion.plugins.media.ArtInstituteCollection.RecordsResultPart1, List[Either[com.histmersion.plugins.media.ArtInstituteCollection.RecordsResultPart2Part1, MediaItem]]] =
    val c = json.hcursor
    c.downField("data").focus.flatMap(_.asArray) match
      case None       => Left(c.str("detail").orElse(c.str("error")).getOrElse("no data in the answer"))
      case Some(rows) => Right(rows.toList.map(r => record(r, iiif, displayPx, thumbPx)))

  def record(json: Json, iiif: com.histmersion.plugins.media.ArtInstituteCollection.RecordIiif, displayPx: com.histmersion.plugins.media.ArtInstituteCollection.RecordDisplayPx, thumbPx: com.histmersion.plugins.media.ArtInstituteCollection.RecordThumbPx): Either[com.histmersion.plugins.media.ArtInstituteCollection.RecordResultPart1, MediaItem] =
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
