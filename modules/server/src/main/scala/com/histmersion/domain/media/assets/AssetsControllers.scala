package com.histmersion.domain.media.assets

import com.histmersion.api.{Api, ApiError, Write}
import com.histmersion.config.AppConfig
import com.histmersion.domain.media.MediaSources
import com.histmersion.util.data.maintenance.WriteGate
import com.histmersion.domain.library.Citekey
import com.histmersion.api.media.{
  AssetDetail,
  AssetInfo,
  AssetInput,
  AssetLinks,
  AssetPatch,
  AssetsStored,
  MediaFacets,
  MediaGeoHit,
  MediaItem,
  MediaList,
  MediaThumb
}
import com.histmersion.api.library.of

/** The pictures over REST and MCP: a source's figures listed, read, uploaded, described and patched (plan §1.8, §1.6);
  * the media list, its facets, one picture, its file and thumbnail, the map feed (#1446); the cards' pictures of a
  * place, an event and a source. A figure's URL embeds the citekey, so an OLD one is resolved first.
  */
final class AssetsControllers(
    assets: AssetService,
    sources: MediaSources,
    cfg: () => AppConfig,
    maintenance: WriteGate
):

  private val Uuid = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"

  private def notFound(uid: String) = ApiError(s"No picture '$uid'", Some(Write.CodeNotFound))

  private def canonical(citekey: String): String = sources.current(citekey).getOrElse(citekey)

  private def uuid(uid: String): Option[String] = Option(uid.trim).filter(_.matches(Uuid))

  // ── a source's figures ──

  def figures(citekey: String): List[AssetInfo] = assets.figures(canonical(citekey))

  def figure(citekey: String, name: String): Either[ApiError, (Array[Byte], String)] =
    assets
      .figure(canonical(citekey), AssetLinks.safeName(name))
      .map((mime, bytes) => (bytes, mime))
      .toRight(ApiError(s"No asset '$name' for '$citekey'", Some(Write.CodeNotFound)))

  /** Add figures to an existing source (the host-side seed path). */
  def putFigures(citekey: String, in: List[AssetInput]): Either[ApiError, AssetsStored] =
    maintenance.guard("put_assets")(
      Citekey.of(citekey).flatMap { _ =>
        if !sources.exists(citekey) then Left(Write.notFound(citekey))
        else decode(in).map(stored => AssetsStored(citekey, assets.addFigures(citekey, stored)))
      }
    )

  /** Wire figures → storable ones, failing the whole batch on the first unusable payload. */
  def decode(in: List[AssetInput]): Either[ApiError, List[NewFigure]] =
    in.foldLeft[Either[ApiError, List[NewFigure]]](Right(Nil)) { (acc, a) =>
      acc.flatMap(xs => NewFigure.of(a, cfg().limits.assetBytes).left.map(ApiError(_)).map(xs :+ _))
    }

  def detail(citekey: String, name: String): Either[ApiError, AssetDetail] =
    assets.detail(citekey, name).toRight(ApiError(s"No asset '$citekey/$name'", Some(Write.CodeNotFound)))

  def patch(citekey: String, name: String, p: AssetPatch): Either[ApiError, AssetDetail] =
    maintenance.guard("patch_asset")(assets.patch(citekey, name, p))

  // ── the pictures ──

  def mediaGeo(q: Api.MediaGeoQuery): List[MediaGeoHit] = assets.geo(q, cfg().limits.page(q.limit, 200))

  def list(q: Api.MediaQuery): MediaList = assets.list(q, cfg().limits.page(q.limit, 50))

  def facets: MediaFacets = assets.facets

  def show(uid: String): Either[ApiError, MediaItem] = uuid(uid).flatMap(assets.show).toRight(notFound(uid))

  def thumb(uid: String): Either[ApiError, (Array[Byte], String)] =
    uuid(uid).flatMap(assets.thumb).map((m, b) => (b, m)).toRight(notFound(uid))

  def file(uid: String): Either[ApiError, (Array[Byte], String)] =
    uuid(uid).flatMap(assets.file).map((m, b) => (b, m)).toRight(notFound(uid))

  // ── the cards' pictures ──

  /** A place's pictures for its card, through its equivalents. */
  def placeThumbs(placeId: String): List[MediaThumb] =
    assets.placeThumbs(placeId, cfg().media.sameSiteKm, AssetsControllers.CardPictures)

  /** An event's pictures for its card. */
  def eventThumbs(eventId: String): List[MediaThumb] =
    assets.thumbs(PictureTarget.Event.wire, eventId, AssetsControllers.CardPictures)

  /** A source's picture for its hover card: its cover, else its illustration. */
  def sourceThumb(citekey: String): Option[MediaThumb] =
    assets.thumbs(PictureTarget.Source.wire, citekey, 1).headOption

object AssetsControllers:
  /** How many pictures a card shows. */
  val CardPictures = 4
