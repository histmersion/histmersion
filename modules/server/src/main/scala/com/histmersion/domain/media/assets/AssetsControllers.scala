package com.histmersion.domain.media.assets

import com.histmersion.api.{Api, Write}
import com.histmersion.config.AppConfig
import com.histmersion.domain.media.MediaSources
import com.histmersion.util.data.maintenance.WriteGate
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

  private def notFound(uid: com.histmersion.domain.media.assets.AssetService.AssetUid) = Api.Error(s"No picture '$uid'", Some(Write.CodeNotFound))

  private def canonical(citekey: com.histmersion.domain.library.Citekey): com.histmersion.domain.media.assets.AssetService.AssetUid = sources.current(citekey).getOrElse(citekey)

  private def uuid(uid: com.histmersion.domain.media.assets.AssetService.AssetUid): Option[com.histmersion.domain.media.assets.AssetService.AssetUid] = Option(uid.trim).filter(_.matches(Uuid))

  // ── a source's figures ──

  def figures(citekey: com.histmersion.domain.library.Citekey): List[AssetInfo] = assets.figures(canonical(citekey))

  def figure(citekey: com.histmersion.domain.library.Citekey, name: com.histmersion.domain.media.assets.AssetService.AssetName): Either[Api.Error, (com.histmersion.domain.media.assets.AssetService.Bytes, com.histmersion.domain.media.assets.AssetService.Mime)] =
    assets
      .figure(canonical(citekey), AssetLinks.safeName(name))
      .map((mime, bytes) => (bytes, mime))
      .toRight(Api.Error(s"No asset '$name' for '$citekey'", Some(Write.CodeNotFound)))

  /** Add figures to an existing source (the host-side seed path). */
  def putFigures(citekey: com.histmersion.domain.library.Citekey, in: List[AssetInput]): Either[Api.Error, AssetsStored] =
    maintenance.guard(com.histmersion.util.data.maintenance.WriteGate.OperationName("put_assets"))(
      sources.lookup(citekey).left.map {
        case MediaSources.Error.Invalid(message) => Write.invalid(message)
        case MediaSources.Error.NotFound => Write.notFound(citekey)
      }.flatMap { key =>
        decode(in).map(stored => AssetsStored(key.value, assets.addFigures(key.value, stored)))
      }
    )

  /** Wire figures → storable ones, failing the whole batch on the first unusable payload. */
  def decode(in: List[AssetInput]): Either[Api.Error, List[NewFigure]] =
    in.foldLeft[Either[Api.Error, List[NewFigure]]](Right(Nil)) { (acc, a) =>
      acc.flatMap(xs => NewFigure.of(a, cfg().limits.assetBytes).left.map(Api.Error(_)).map(xs :+ _))
    }

  def detail(citekey: com.histmersion.domain.library.Citekey, name: com.histmersion.domain.media.assets.AssetService.AssetName): Either[Api.Error, AssetDetail] =
    assets.detail(citekey, name).toRight(Api.Error(s"No asset '$citekey/$name'", Some(Write.CodeNotFound)))

  def patch(citekey: com.histmersion.domain.library.Citekey, name: com.histmersion.domain.media.assets.AssetService.AssetName, p: AssetPatch): Either[Api.Error, AssetDetail] =
    maintenance.guard(com.histmersion.util.data.maintenance.WriteGate.OperationName("patch_asset"))(assets.patch(citekey, name, p))

  // ── the pictures ──

  def mediaGeo(q: Api.MediaGeoQuery): List[MediaGeoHit] = assets.geo(q, cfg().limits.page(q.limit, com.histmersion.PageSize(200)))

  def list(q: Api.MediaQuery): MediaList = assets.list(q, cfg().limits.page(q.limit, com.histmersion.PageSize(50)))

  def facets: MediaFacets = assets.facets

  def show(uid: com.histmersion.domain.media.assets.AssetService.AssetUid): Either[Api.Error, MediaItem] = uuid(uid).flatMap(assets.show).toRight(notFound(uid))

  def thumb(uid: com.histmersion.domain.media.assets.AssetService.AssetUid): Either[Api.Error, (com.histmersion.domain.media.assets.AssetService.Bytes, com.histmersion.domain.media.assets.AssetService.Mime)] =
    uuid(uid).flatMap(assets.thumb).map((m, b) => (b, m)).toRight(notFound(uid))

  def file(uid: com.histmersion.domain.media.assets.AssetService.AssetUid): Either[Api.Error, (com.histmersion.domain.media.assets.AssetService.Bytes, com.histmersion.domain.media.assets.AssetService.Mime)] =
    uuid(uid).flatMap(assets.file).map((m, b) => (b, m)).toRight(notFound(uid))

  // ── the cards' pictures ──

  /** A place's pictures for its card, through its equivalents. */
  def placeThumbs(placeId: com.histmersion.domain.catalogue.Place.Id): List[MediaThumb] =
    assets.placeThumbs(placeId, cfg().media.sameSiteKm, AssetsControllers.CardPictures)

  /** An event's pictures for its card. */
  def eventThumbs(eventId: com.histmersion.domain.catalogue.Event.Id): List[MediaThumb] =
    assets.thumbs(PictureTarget.Event.wire, eventId, AssetsControllers.CardPictures)

  /** A source's picture for its hover card: its cover, else its illustration. */
  def sourceThumb(citekey: com.histmersion.domain.library.Citekey): Option[MediaThumb] =
    assets.thumbs(PictureTarget.Source.wire, citekey, com.histmersion.domain.media.assets.AssetService.ThumbsLimit(1)).headOption

object AssetsControllers:
  /** How many pictures a card shows. */

  val CardPictures = 4
