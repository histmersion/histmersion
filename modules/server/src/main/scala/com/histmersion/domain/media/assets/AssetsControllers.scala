package com.histmersion
package domain.media.assets

import com.histmersion.util.problem.{Legacy, Problem}
import io.scalaland.chimney.dsl.*
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
import com.histmersion.util.database.WritePermit
import com.histmersion.domain.Creator
import com.histmersion.lint.{tolerated, LintRule}

/** The pictures over REST and MCP: a source's figures listed, read, uploaded, described and patched (plan §1.8, §1.6);
  * the media list, its facets, one picture, its file and thumbnail, the map feed (#1446); the cards' pictures of a
  * place, an event and a source. A figure's URL embeds the citekey, so an OLD one is resolved first.
  */
@tolerated(LintRule.Direction, "pending G12 (a domain names only lower places)")
final class AssetsControllers(
    assets: AssetService,
    sources: MediaSources,
    cfg: () => AppConfig,
    maintenance: WriteGate
):

  private val Uuid = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"

  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  @tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
  private def notFound(uid: AssetService.AssetUid) =
    Api.Error(Problem.Unclassified(Legacy.Text(s"No picture '$uid'")), Some(Api.Error.Code(Write.CodeNotFound)))

  private def canonical(citekey: Citekey): Citekey =
    sources.current(citekey).map(c => c.citekey).getOrElse(citekey)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def uuid(uid: AssetService.AssetUid): Option[AssetService.AssetUid] =
    Option(uid.value.trim).filter(_.matches(Uuid)).map(AssetService.AssetUid(_))

  // ── a source's figures ──

  def figures(citekey: Citekey): List[AssetInfo] = assets.figures(canonical(citekey))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  @tolerated(LintRule.G44PlainDef, "pending G1 (the parameter takes the newtype, not its raw value)")
  @tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
  def figure(
      citekey: Citekey,
      name: AssetService.AssetName
  ): Either[Api.Error, (AssetService.Bytes, AssetService.Mime)] =
    assets
      .figure(canonical(citekey), AssetService.AssetName(AssetLinks.safeName(name.value).value))
      .map((mime, bytes) => (bytes, mime))
      .toRight(
        Api.Error(
          Problem.Unclassified(Legacy.Text(s"No asset '$name' for '$citekey'")),
          Some(Api.Error.Code(Write.CodeNotFound))
        )
      )

  /** Add figures to the source a client named: a name that is no citekey is refused (400). */
  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  @tolerated(LintRule.G44PlainDef, "pending G1 (the parameter takes the newtype, not its raw value)")
  @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
  def putFiguresAsked(
      citekey: com.histmersion.api.library.Citekey,
      in: List[AssetInput]
  )(using Creator.New): Either[Api.Error, AssetsStored] =
    maintenance.guard(WriteGate.OperationName("put_assets"))(
      Citekey
        .parse(citekey.value)
        .left
        .map(p => Write.invalid(p))
        .flatMap(storeFigures(_, citekey, in))
    )

  /** Add figures to an existing source (the host-side seed path). */
  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
  def putFigures(citekey: Citekey, in: List[AssetInput])(using Creator.New): Either[Api.Error, AssetsStored] =
    maintenance.guard(WriteGate.OperationName("put_assets"))(
      storeFigures(citekey, com.histmersion.api.library.Citekey(citekey.value), in)
    )

  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
  private def storeFigures(
      citekey: Citekey,
      asked: com.histmersion.api.library.Citekey,
      in: List[AssetInput]
  )(using WritePermit)(using Creator.New): Either[Api.Error, AssetsStored] =
    sources
      .lookup(citekey)
      .left
      .map {
        case MediaSources.Error.Invalid(message) => Write.invalid(message)
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
  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  def decode(in: List[AssetInput]): Either[Api.Error, List[NewFigure]] =
    in.foldLeft[Either[Api.Error, List[NewFigure]]](Right(Nil)) { (acc, a) =>
      acc.flatMap(xs =>
        NewFigure
          .of(a, ByteCount.option(cfg().limits.assetBytes.value).getOrElse(ByteCount(0L)))
          .left
          .map(p => Api.Error(p))
          .map(xs :+ _)
      )
    }

  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  @tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
  def detail(citekey: Citekey, name: AssetService.AssetName): Either[Api.Error, AssetDetail] =
    assets
      .detail(citekey, name)
      .toRight(
        Api.Error(
          Problem.Unclassified(Legacy.Text(s"No asset '$citekey/$name'")),
          Some(Api.Error.Code(Write.CodeNotFound))
        )
      )

  def patch(citekey: Citekey, name: AssetService.AssetName, p: AssetPatch): Either[Api.Error, AssetDetail] =
    maintenance.guard(WriteGate.OperationName("patch_asset"))(assets.patch(citekey, name, p))

  // ── the pictures ──

  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  def mediaGeo(q: Api.MediaGeoQuery): List[MediaGeoHit] = assets.geo(
    q,
    AssetService.Limit(cfg().limits.page(q.limit.flatMap(l => PageSize.option(l.value)), PageSize(200)).value)
  )

  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
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
  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  def placeThumbs(placeId: Place.Id): List[MediaThumb] =
    assets.placeThumbs(
      placeId,
      EventMatcher.NearFinding.Km(cfg().media.sameSiteKm.value),
      AssetService.Limit(AssetsControllers.CardPictures)
    )

  /** An event's pictures for its card. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  def eventThumbs(eventId: Event.Id): List[MediaThumb] =
    assets.thumbs(
      AssetService.TargetType(PictureTarget.Event.wire.value),
      AssetService.TargetId(eventId.value),
      AssetService.Limit(AssetsControllers.CardPictures)
    )

  /** A source's picture for its hover card: its cover, else its illustration. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
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
