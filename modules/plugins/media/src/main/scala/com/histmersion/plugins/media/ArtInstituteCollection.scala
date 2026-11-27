package com.histmersion
package plugins.media
import com.histmersion.util.json.RawJson
import com.histmersion.util.json.RawJson.*
import com.histmersion.util.json.JsonCodec.given
import hearth.kindlings.jsoniterjson.Json
import com.histmersion.util.problem.Problem

import com.histmersion.plugin.*
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{atBoundary, BoundaryKind}
import com.histmersion.lint.{tolerated, LintRule}

/** `aic`: the Art Institute of Chicago API (https://api.artic.edu/docs/), no key. A search answers the fields asked
  * for; `is_public_domain` marks the works whose images are released under CC0 — any other is refused. Images come from
  * its IIIF server, `{iiif}/{image_id}/full/{w},/0/default.jpg` (843 px is the size it caches and recommends). The
  * place field is `place_of_origin`; years are signed already.
  */
@atBoundary(BoundaryKind.Json, "reads the art institute collection collection's JSON answers into media records")
final class ArtInstituteCollection(conf: CollectionSettings, thumbPx: ArtInstituteCollection.ThumbPx)
    extends MuseumCollection(
      conf,
      MuseumCollection.Provider("The Art Institute of Chicago"),
      MuseumCollection.Key("media.aic")
    ):
  val title = MediaCollection.Title("Art Institute of Chicago objects")
  override val description = MediaCollection.Description(
    "The Art Institute of Chicago (CC0, is_public_domain; IIIF images)" + MetCollection.Museum
  )
  val role = MediaCollection.Object

  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def search(
      place: MediaPlace,
      http: Plugin.Http
  ): Either[Problem, List[Either[Problem, MediaItem]]] =
    http
      .askJson(
        ArtInstituteCollection
          .searchUrl(Plugin.Http.Url(conf.apiUrl.value), CollectionQuery.Text(place.label.value), conf.candidates)
      )
      .flatMap(
        ArtInstituteCollection.records(_, Plugin.Http.Url(conf.imageUrl.value), conf.displayPx, thumbPx).unreadableEach
      )

object ArtInstituteCollection:

  /** The plugin's id: what its problems name. */
  val Name = "media-aic"
  type ImageId = ImageId.T
  object ImageId extends Newtype[String, Pure]
  type Width = Width.T
  object Width extends Newtype[Int, Pure]

  export MediaSettings.ThumbPx

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

  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def searchUrl(
      api: Plugin.Http.Url,
      name: CollectionQuery.Text,
      limit: CollectionSettings.Candidates
  ): Plugin.Http.Url =
    Plugin.Http.Url(
      s"${api.value.stripSuffix("/")}/artworks/search?q=${name.value.enc}&limit=${limit.value.max(1)}&fields=$Fields"
    )

  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def imageUrl(iiif: Plugin.Http.Url, imageId: ImageId, width: Width): MediaItem.ImageUrl =
    MediaItem.ImageUrl(s"${iiif.value.stripSuffix("/")}/${imageId.value.enc}/full/${width.value.max(1)},/0/default.jpg")

  def records(
      json: Json,
      iiif: Plugin.Http.Url,
      displayPx: CollectionSettings.DisplayPx,
      thumbPx: ThumbPx
  ): Either[Problem, List[Either[Problem, MediaItem]]] =
    val c = json.cursor
    c.downField("data").focus.flatMap(_.asArray) match
      case None =>
        Left(
          c.str("detail")
            .orElse(c.str("error"))
            .fold(MediaPlugin.missing("data"))(MediaPlugin.said(Name, Problem.Plugin.Claim.Failed, _))
        )
      case Some(rows) => Right(rows.toList.map(r => record(r, iiif, displayPx, thumbPx)))

  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def record(
      json: Json,
      iiif: Plugin.Http.Url,
      displayPx: CollectionSettings.DisplayPx,
      thumbPx: ThumbPx
  ): Either[Problem, MediaItem] =
    val c = json.cursor
    c.long("id").toRight(MediaPlugin.missing("id")).map { id =>
      val title = c.str("title").getOrElse(s"Artwork $id")
      val artist = c.str("artist_title")
      val credit = (List(Some(title), artist).flatten :+ "The Art Institute of Chicago" :+ "CC0").mkString(", ")
      val image = c.str("image_id")
      val rights =
        if !c.bool("is_public_domain").contains(true) then
          Left(
            MediaPlugin.rejected(
              Name,
              s"$id",
              MediaPlugin.notOpen(Problem.Field("is_public_domain"), c.bool("is_public_domain"))
            )
          )
        else Right(MediaRights.cc0(artist.map(MediaRights.Author(_)), MediaRights.Credit(credit)))
      MediaItem(
        MediaItem.Key(id.toString),
        MediaItem.Title(title),
        MediaItem.PageUrl(s"https://www.artic.edu/artworks/$id"),
        image.map(i => imageUrl(iiif, ImageId(i), Width(displayPx.value))),
        rights,
        thumbUrl = image.map(i => MediaItem.ThumbUrl(imageUrl(iiif, ImageId(i), Width(thumbPx.value)).value)),
        originalUrl =
          image.map(i => MediaItem.OriginalUrl(s"${iiif.value.stripSuffix("/")}/${i.enc}/full/full/0/default.jpg")),
        geography = c
          .str("place_of_origin")
          .map(p => (MediaItem.Geography.Field("place_of_origin"), MediaItem.Geography.Value(p)))
          .toList,
        period = Period.range(c.long("date_start"), c.long("date_end"))
      )
    }
