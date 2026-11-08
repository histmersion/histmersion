package com.histmersion
package domain.media.assets

import com.histmersion.api.{Api, Write}
import com.histmersion.config.AppConfig
import com.histmersion.domain.library.Citekey
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
import com.histmersion.domain.catalogue.Place
import com.histmersion.domain.catalogue.evidence.EventMatcher
import com.histmersion.domain.catalogue.Event

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

  private def notFound(uid: AssetService.AssetUid) =
    Api.Error(Api.Error.Message(s"No picture '$uid'"), Some(Api.Error.Code(Write.CodeNotFound)))

  private def canonical(citekey: Citekey): Citekey =
    sources.current(citekey).map(c => Citekey.assume(c.value)).getOrElse(citekey)

  private def uuid(uid: AssetService.AssetUid): Option[AssetService.AssetUid] =
    Option(uid.value.trim).filter(_.matches(Uuid)).map(AssetService.AssetUid(_))

  // ── a source's figures ──

  def figures(citekey: Citekey): List[AssetInfo] = assets.figures(canonical(citekey))

  def figure(
      citekey: Citekey,
      name: AssetService.AssetName
  ): Either[Api.Error, (AssetService.Bytes, AssetService.Mime)] =
    assets
      .figure(canonical(citekey), AssetService.AssetName(AssetLinks.safeName(name.value).value))
      .map((mime, bytes) => (bytes, mime))
      .toRight(
        Api.Error(Api.Error.Message(s"No asset '$name' for '$citekey'"), Some(Api.Error.Code(Write.CodeNotFound)))
      )

  /** Add figures to the source a client named: a name that is no citekey is refused (400). */
  def putFiguresAsked(
      citekey: com.histmersion.api.library.Citekey,
      in: List[AssetInput]
  ): Either[Api.Error, AssetsStored] =
    maintenance.guard(WriteGate.OperationName("put_assets"))(
      Citekey
        .parse(citekey.value)
        .left
        .map(p => Write.invalid(Write.InvalidMessage(p.value)))
        .flatMap(storeFigures(_, citekey, in))
    )

  /** Add figures to an existing source (the host-side seed path). */
  def putFigures(citekey: Citekey, in: List[AssetInput]): Either[Api.Error, AssetsStored] =
    maintenance.guard(WriteGate.OperationName("put_assets"))(
      storeFigures(citekey, com.histmersion.api.library.Citekey(citekey.value), in)
    )

  private def storeFigures(
      citekey: Citekey,
      asked: com.histmersion.api.library.Citekey,
      in: List[AssetInput]
  ): Either[Api.Error, AssetsStored] =
    sources
      .lookup(citekey)
      .left
      .map {
        case MediaSources.Error.Invalid(message) => Write.invalid(Write.InvalidMessage(message.value))
        case MediaSources.Error.NotFound         => Write.notFound(asked)
      }
      .flatMap { _ =>
        decode(in).map(stored =>
          AssetsStored(
            com.histmersion.api.library.Citekey(citekey.value),
            AssetsStored.Stored(assets.addFigures(citekey, stored).value)
          )
        )
      }

  /** Wire figures → storable ones, failing the whole batch on the first unusable payload. */
  def decode(in: List[AssetInput]): Either[Api.Error, List[NewFigure]] =
    in.foldLeft[Either[Api.Error, List[NewFigure]]](Right(Nil)) { (acc, a) =>
      acc.flatMap(xs =>
        NewFigure
          .of(a, ByteCount.option(cfg().limits.assetBytes.value).getOrElse(ByteCount(0L)))
          .left
          .map(p => Api.Error(Api.Error.Message(p.value)))
          .map(xs :+ _)
      )
    }

  def detail(citekey: Citekey, name: AssetService.AssetName): Either[Api.Error, AssetDetail] =
    assets
      .detail(citekey, name)
      .toRight(Api.Error(Api.Error.Message(s"No asset '$citekey/$name'"), Some(Api.Error.Code(Write.CodeNotFound))))

  def patch(citekey: Citekey, name: AssetService.AssetName, p: AssetPatch): Either[Api.Error, AssetDetail] =
    maintenance.guard(WriteGate.OperationName("patch_asset"))(assets.patch(citekey, name, p))

  // ── the pictures ──

  def mediaGeo(q: Api.MediaGeoQuery): List[MediaGeoHit] = assets.geo(
    q,
    AssetService.Limit(cfg().limits.page(q.limit.flatMap(l => PageSize.option(l.value)), PageSize(200)).value)
  )

  def list(q: Api.MediaQuery): MediaList = assets.list(
    q,
    AssetService.Limit(cfg().limits.page(q.limit.flatMap(l => PageSize.option(l.value)), PageSize(50)).value)
  )

  def facets: MediaFacets = assets.facets

  def show(uid: AssetService.AssetUid): Either[Api.Error, MediaItem] =
    uuid(uid).flatMap(assets.show).toRight(notFound(uid))

  def thumb(uid: AssetService.AssetUid): Either[Api.Error, (AssetService.Bytes, AssetService.Mime)] =
    uuid(uid).flatMap(assets.thumb).map((m, b) => (b, m)).toRight(notFound(uid))

  def file(uid: AssetService.AssetUid): Either[Api.Error, (AssetService.Bytes, AssetService.Mime)] =
    uuid(uid).flatMap(assets.file).map((m, b) => (b, m)).toRight(notFound(uid))

  // ── the cards' pictures ──

  /** A place's pictures for its card, through its equivalents. */
  def placeThumbs(placeId: Place.Id): List[MediaThumb] =
    assets.placeThumbs(
      placeId,
      EventMatcher.NearFinding.Km(cfg().media.sameSiteKm.value),
      AssetService.Limit(AssetsControllers.CardPictures)
    )

  /** An event's pictures for its card. */
  def eventThumbs(eventId: Event.Id): List[MediaThumb] =
    assets.thumbs(
      AssetService.TargetType(PictureTarget.Event.wire.value),
      AssetService.TargetId(eventId.value),
      AssetService.Limit(AssetsControllers.CardPictures)
    )

  /** A source's picture for its hover card: its cover, else its illustration. */
  def sourceThumb(citekey: Citekey): Option[MediaThumb] =
    assets
      .thumbs(
        AssetService.TargetType(PictureTarget.Source.wire.value),
        AssetService.TargetId(citekey.value),
        AssetService.Limit(1)
      )
      .headOption

object AssetsControllers:
  /** How many pictures a card shows. */

  val CardPictures = 4
