package com.histmersion.domain.media.assets

import com.histmersion.api.{Api, Write}
import com.histmersion.domain.media.MediaState
import com.histmersion.util.database.{RangeSql, Transactor}
import com.histmersion.domain.media.MediaRole

import hearth.kindlings.optics.syntax.*

import java.time.{Clock, Instant, ZoneOffset}
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import com.histmersion.api.media.{
  AssetDetail,
  AssetInfo,
  AssetPatch,
  MediaCount,
  MediaFacets,
  MediaGeoHit,
  MediaItem,
  MediaList,
  MediaProvider,
  MediaTarget,
  MediaThumb
}

/** The pictures (`assets`) and where they are shown (`media_links`): a source's figures stored, read, described and
  * patched; a provider's pictures stored, re-read and given thumbnails; the places, events and sources they are shown
  * on; the media list, its facets, a picture, its file and thumbnail, the map feed and the cards' pictures.
  */
trait AssetService:

  // ── a source's figures ──

  def figures(citekey: com.histmersion.domain.library.Citekey): List[AssetInfo]

  /** A figure's mime type and bytes. */
  def figure(citekey: com.histmersion.domain.library.Citekey, name: com.histmersion.domain.media.assets.AssetService.FigureName): Option[(com.histmersion.domain.media.assets.AssetService.FigureResultPart1, Array[com.histmersion.domain.media.assets.AssetService.FigureResultPart2])]

  def figureCount(citekey: com.histmersion.domain.library.Citekey): com.histmersion.domain.media.assets.AssetService.FigureCountResult

  /** Figures added, any stored under the same name left alone; how many were written (after deduplication). */
  def addFigures(citekey: com.histmersion.domain.library.Citekey, figures: List[NewFigure]): com.histmersion.domain.media.assets.AssetService.AddFiguresResult

  /** The whole set replaced (a re-conversion): the rows of the pictures it still has kept, renamed when only their name
    * moved; how many were kept.
    */
  def replaceFigures(citekey: com.histmersion.domain.library.Citekey, figures: List[NewFigure]): com.histmersion.domain.media.assets.AssetService.ReplaceFiguresResult

  def detail(citekey: com.histmersion.domain.library.Citekey, name: com.histmersion.domain.media.assets.AssetService.DetailName): Option[AssetDetail]

  /** A figure's description changed; a period that would end before it starts is refused, the other fields kept. */
  def patch(citekey: com.histmersion.domain.library.Citekey, name: com.histmersion.domain.media.assets.AssetService.PatchName, p: AssetPatch): Either[Api.Error, AssetDetail]

  /** A source's image figures, as the illustration's choice weighs them. */
  def sourceFigures(citekey: com.histmersion.domain.library.Citekey): List[SourceFigure]

  // ── a provider's pictures and where they are shown ──

  def known(provider: com.histmersion.domain.media.assets.AssetService.KnownProvider, keys: List[com.histmersion.domain.media.assets.AssetService.KnownKeys]): Map[com.histmersion.domain.media.assets.AssetService.KnownResultPart1, Known]

  /** A provider's picture stored, or its bytes and rights replaced; its uid and whether it is new. */
  def save(a: ProviderAsset, firstTarget: (com.histmersion.domain.media.assets.AssetService.SaveFirstTargetPart1, com.histmersion.domain.media.assets.AssetService.SaveFirstTargetPart2)): (com.histmersion.domain.media.assets.AssetService.SaveResultPart1, com.histmersion.domain.media.assets.AssetService.SaveResultPart2)

  def refreshRights(uid: com.histmersion.domain.media.assets.AssetService.RefreshRightsUid, r: Rights): Unit

  def setThumb(uid: com.histmersion.domain.media.assets.AssetService.SetThumbUid, bytes: Array[com.histmersion.domain.media.assets.AssetService.SetThumbBytes], mime: com.histmersion.domain.media.assets.AssetService.SetThumbMime, width: Option[com.histmersion.domain.media.assets.AssetService.SetThumbWidth], height: Option[com.histmersion.domain.media.assets.AssetService.SetThumbHeight]): Unit

  /** A picture shown on a place or event in a role; whether the link is new. */
  def link(
      uid: com.histmersion.domain.media.assets.AssetService.LinkUid,
      targetType: com.histmersion.domain.media.assets.AssetService.LinkTargetType,
      targetId: com.histmersion.domain.media.assets.AssetService.LinkTargetId,
      role: com.histmersion.domain.media.assets.AssetService.LinkRole,
      property: Option[com.histmersion.domain.media.assets.AssetService.LinkProperty],
      item: Option[com.histmersion.domain.media.assets.AssetService.LinkItem],
      rank: com.histmersion.domain.media.assets.AssetService.LinkRank,
      basis: Option[com.histmersion.domain.media.assets.AssetService.LinkBasis]
  ): com.histmersion.domain.media.assets.AssetService.LinkResult

  /** `uid` made the source's picture in `role`, replacing the one it had in that role. */
  def setSourcePicture(citekey: com.histmersion.domain.library.Citekey, uid: com.histmersion.domain.media.assets.AssetService.SetSourcePictureUid, role: com.histmersion.domain.media.assets.AssetService.SetSourcePictureRole, property: Option[com.histmersion.domain.media.assets.AssetService.SetSourcePictureProperty]): Unit

  /** The source's picture in `role`: its uid and its provider key or figure name. */
  def sourcePicture(citekey: com.histmersion.domain.library.Citekey, role: com.histmersion.domain.media.assets.AssetService.SourcePictureRole): Option[(com.histmersion.domain.media.assets.AssetService.SourcePictureResultPart1, com.histmersion.domain.media.assets.AssetService.SourcePictureResultPart2)]

  // ── reads ──

  def list(q: Api.MediaQuery, limit: com.histmersion.domain.media.assets.AssetService.ListLimit): MediaList
  def show(uid: com.histmersion.domain.media.assets.AssetService.ShowUid): Option[MediaItem]

  /** The thumbnail's type and bytes — a source's figure (or a cover) without one is its own thumbnail. */
  def thumb(uid: com.histmersion.domain.media.assets.AssetService.ThumbUid): Option[(com.histmersion.domain.media.assets.AssetService.ThumbResultPart1, Array[com.histmersion.domain.media.assets.AssetService.ThumbResultPart2])]

  def file(uid: com.histmersion.domain.media.assets.AssetService.FileUid): Option[(com.histmersion.domain.media.assets.AssetService.FileResultPart1, Array[com.histmersion.domain.media.assets.AssetService.FileResultPart2])]
  def facets: MediaFacets
  def geo(q: Api.MediaGeoQuery, limit: com.histmersion.domain.media.assets.AssetService.GeoLimit): List[MediaGeoHit]
  def geoOf(uids: List[com.histmersion.domain.media.assets.AssetService.GeoOfUids]): Map[com.histmersion.domain.media.assets.AssetService.GeoOfResultPart1, MediaGeoHit]

  /** A place's, an event's or a source's pictures for its card, best first. */
  def thumbs(targetType: com.histmersion.domain.media.assets.AssetService.ThumbsTargetType, targetId: com.histmersion.domain.media.assets.AssetService.ThumbsTargetId, limit: com.histmersion.domain.media.assets.AssetService.ThumbsLimit): List[MediaThumb]

  /** A place's pictures for its card, through its equivalents (its old ids, its Wikidata items, a point of the same
    * name within `sameSiteKm`).
    */
  def placeThumbs(placeId: com.histmersion.domain.catalogue.Place.Id, sameSiteKm: com.histmersion.domain.media.assets.AssetService.PlaceThumbsSameSiteKm, limit: com.histmersion.domain.media.assets.AssetService.PlaceThumbsLimit): List[MediaThumb]

object AssetService:
  type FigureResultPart1 = FigureResultPart1.T
  object FigureResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FigureResultPart2 = FigureResultPart2.T
  object FigureResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Byte, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FigureName = FigureName.T
  object FigureName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FigureCountResult = FigureCountResult.T
  object FigureCountResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AddFiguresResult = AddFiguresResult.T
  object AddFiguresResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReplaceFiguresResult = ReplaceFiguresResult.T
  object ReplaceFiguresResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DetailName = DetailName.T
  object DetailName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PatchName = PatchName.T
  object PatchName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type KnownResultPart1 = KnownResultPart1.T
  object KnownResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type KnownProvider = KnownProvider.T
  object KnownProvider extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type KnownKeys = KnownKeys.T
  object KnownKeys extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SaveResultPart1 = SaveResultPart1.T
  object SaveResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SaveResultPart2 = SaveResultPart2.T
  object SaveResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SaveFirstTargetPart1 = SaveFirstTargetPart1.T
  object SaveFirstTargetPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SaveFirstTargetPart2 = SaveFirstTargetPart2.T
  object SaveFirstTargetPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RefreshRightsUid = RefreshRightsUid.T
  object RefreshRightsUid extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SetThumbUid = SetThumbUid.T
  object SetThumbUid extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SetThumbBytes = SetThumbBytes.T
  object SetThumbBytes extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Byte, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SetThumbMime = SetThumbMime.T
  object SetThumbMime extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SetThumbWidth = SetThumbWidth.T
  object SetThumbWidth extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SetThumbHeight = SetThumbHeight.T
  object SetThumbHeight extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LinkResult = LinkResult.T
  object LinkResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LinkUid = LinkUid.T
  object LinkUid extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LinkTargetType = LinkTargetType.T
  object LinkTargetType extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LinkTargetId = LinkTargetId.T
  object LinkTargetId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LinkRole = LinkRole.T
  object LinkRole extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LinkProperty = LinkProperty.T
  object LinkProperty extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LinkItem = LinkItem.T
  object LinkItem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LinkRank = LinkRank.T
  object LinkRank extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LinkBasis = LinkBasis.T
  object LinkBasis extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SetSourcePictureUid = SetSourcePictureUid.T
  object SetSourcePictureUid extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SetSourcePictureRole = SetSourcePictureRole.T
  object SetSourcePictureRole extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SetSourcePictureProperty = SetSourcePictureProperty.T
  object SetSourcePictureProperty extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SourcePictureResultPart1 = SourcePictureResultPart1.T
  object SourcePictureResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SourcePictureResultPart2 = SourcePictureResultPart2.T
  object SourcePictureResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SourcePictureRole = SourcePictureRole.T
  object SourcePictureRole extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ListLimit = ListLimit.T
  object ListLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ShowUid = ShowUid.T
  object ShowUid extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ThumbResultPart1 = ThumbResultPart1.T
  object ThumbResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ThumbResultPart2 = ThumbResultPart2.T
  object ThumbResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Byte, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ThumbUid = ThumbUid.T
  object ThumbUid extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FileResultPart1 = FileResultPart1.T
  object FileResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FileResultPart2 = FileResultPart2.T
  object FileResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Byte, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FileUid = FileUid.T
  object FileUid extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GeoLimit = GeoLimit.T
  object GeoLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GeoOfResultPart1 = GeoOfResultPart1.T
  object GeoOfResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GeoOfUids = GeoOfUids.T
  object GeoOfUids extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ThumbsTargetType = ThumbsTargetType.T
  object ThumbsTargetType extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ThumbsTargetId = ThumbsTargetId.T
  object ThumbsTargetId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ThumbsLimit = ThumbsLimit.T
  object ThumbsLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlaceThumbsSameSiteKm = PlaceThumbsSameSiteKm.T
  object PlaceThumbsSameSiteKm extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlaceThumbsLimit = PlaceThumbsLimit.T
  object PlaceThumbsLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def apply(db: Transactor): AssetService = Implementation(db)

  final private class Implementation(db: Transactor) extends AssetService:
    def figures(citekey: com.histmersion.domain.library.Citekey): List[AssetInfo] = db.read(AssetRepo.list(citekey)).toList
    def figure(citekey: com.histmersion.domain.library.Citekey, name: com.histmersion.domain.media.assets.AssetService.Implementation.FigureName): Option[(com.histmersion.domain.media.assets.AssetService.Implementation.FigureResultPart1, Array[com.histmersion.domain.media.assets.AssetService.Implementation.FigureResultPart2])] = db.read(AssetRepo.read(citekey, name))
    def figureCount(citekey: com.histmersion.domain.library.Citekey): com.histmersion.domain.media.assets.AssetService.Implementation.FigureCountResult = db.read(AssetRepo.count(citekey))
    def addFigures(citekey: com.histmersion.domain.library.Citekey, figures: List[NewFigure]): com.histmersion.domain.media.assets.AssetService.Implementation.AddFiguresResult = db.transact(AssetRepo.add(citekey, figures))
    def replaceFigures(citekey: com.histmersion.domain.library.Citekey, figures: List[NewFigure]): com.histmersion.domain.media.assets.AssetService.Implementation.ReplaceFiguresResult =
      db.transact(AssetRepo.replace(citekey, figures))
    def detail(citekey: com.histmersion.domain.library.Citekey, name: com.histmersion.domain.media.assets.AssetService.Implementation.DetailName): Option[AssetDetail] = db.read(AssetRepo.detail(citekey, name))
    def patch(citekey: com.histmersion.domain.library.Citekey, name: com.histmersion.domain.media.assets.AssetService.Implementation.PatchName, p: AssetPatch): Either[Api.Error, AssetDetail] =
      db.transact(AssetRepo.patch(citekey, name, p))
        .flatMap(_ => detail(citekey, name).toRight(Api.Error(s"No asset '$citekey/$name'", Some(Write.CodeNotFound))))
    def sourceFigures(citekey: com.histmersion.domain.library.Citekey): List[SourceFigure] = db.read(MediaRepo.sourceFigures(citekey))
    def known(provider: com.histmersion.domain.media.assets.AssetService.Implementation.KnownProvider, keys: List[com.histmersion.domain.media.assets.AssetService.Implementation.KnownKeys]): Map[com.histmersion.domain.media.assets.AssetService.Implementation.KnownResultPart1, Known] = db.read(MediaRepo.known(provider, keys))
    def save(a: ProviderAsset, firstTarget: (com.histmersion.domain.media.assets.AssetService.Implementation.SaveFirstTargetPart1, com.histmersion.domain.media.assets.AssetService.Implementation.SaveFirstTargetPart2)): (com.histmersion.domain.media.assets.AssetService.Implementation.SaveResultPart1, com.histmersion.domain.media.assets.AssetService.Implementation.SaveResultPart2) =
      db.transact(MediaRepo.save(a, firstTarget))
    def refreshRights(uid: com.histmersion.domain.media.assets.AssetService.Implementation.RefreshRightsUid, r: Rights): Unit = db.transact(MediaRepo.refreshRights(uid, r))
    def setThumb(uid: com.histmersion.domain.media.assets.AssetService.Implementation.SetThumbUid, bytes: Array[com.histmersion.domain.media.assets.AssetService.Implementation.SetThumbBytes], mime: com.histmersion.domain.media.assets.AssetService.Implementation.SetThumbMime, width: Option[com.histmersion.domain.media.assets.AssetService.Implementation.SetThumbWidth], height: Option[com.histmersion.domain.media.assets.AssetService.Implementation.SetThumbHeight]): Unit =
      db.transact(MediaRepo.setThumb(uid, bytes, mime, width, height))
    def link(
        uid: com.histmersion.domain.media.assets.AssetService.Implementation.LinkUid,
        targetType: com.histmersion.domain.media.assets.AssetService.Implementation.LinkTargetType,
        targetId: com.histmersion.domain.media.assets.AssetService.Implementation.LinkTargetId,
        role: com.histmersion.domain.media.assets.AssetService.Implementation.LinkRole,
        property: Option[com.histmersion.domain.media.assets.AssetService.Implementation.LinkProperty],
        item: Option[com.histmersion.domain.media.assets.AssetService.Implementation.LinkItem],
        rank: com.histmersion.domain.media.assets.AssetService.Implementation.LinkRank,
        basis: Option[com.histmersion.domain.media.assets.AssetService.Implementation.LinkBasis]
    ): com.histmersion.domain.media.assets.AssetService.Implementation.LinkResult = db.transact(MediaRepo.link(uid, targetType, targetId, role, property, item, rank, basis))
    def setSourcePicture(citekey: com.histmersion.domain.library.Citekey, uid: com.histmersion.domain.media.assets.AssetService.Implementation.SetSourcePictureUid, role: com.histmersion.domain.media.assets.AssetService.Implementation.SetSourcePictureRole, property: Option[com.histmersion.domain.media.assets.AssetService.Implementation.SetSourcePictureProperty]): Unit =
      db.transact(MediaRepo.setSourcePicture(citekey, uid, role, property))
    def sourcePicture(citekey: com.histmersion.domain.library.Citekey, role: com.histmersion.domain.media.assets.AssetService.Implementation.SourcePictureRole): Option[(com.histmersion.domain.media.assets.AssetService.Implementation.SourcePictureResultPart1, com.histmersion.domain.media.assets.AssetService.Implementation.SourcePictureResultPart2)] =
      db.read(MediaRepo.sourcePicture(citekey, role))
    def list(q: Api.MediaQuery, limit: com.histmersion.domain.media.assets.AssetService.Implementation.ListLimit): MediaList = db.read(MediaRepo.list(q, limit))
    def show(uid: com.histmersion.domain.media.assets.AssetService.Implementation.ShowUid): Option[MediaItem] = db.read(MediaRepo.show(uid))
    def thumb(uid: com.histmersion.domain.media.assets.AssetService.Implementation.ThumbUid): Option[(com.histmersion.domain.media.assets.AssetService.Implementation.ThumbResultPart1, Array[com.histmersion.domain.media.assets.AssetService.Implementation.ThumbResultPart2])] = db.read(MediaRepo.thumb(uid))
    def file(uid: com.histmersion.domain.media.assets.AssetService.Implementation.FileUid): Option[(com.histmersion.domain.media.assets.AssetService.Implementation.FileResultPart1, Array[com.histmersion.domain.media.assets.AssetService.Implementation.FileResultPart2])] = db.read(MediaRepo.file(uid))
    def facets: MediaFacets = db.read(MediaRepo.facets)
    def geo(q: Api.MediaGeoQuery, limit: com.histmersion.domain.media.assets.AssetService.Implementation.GeoLimit): List[MediaGeoHit] = db.read(MediaRepo.geo(q, limit))
    // no connection for a tile with no pictures
    def geoOf(uids: List[com.histmersion.domain.media.assets.AssetService.Implementation.GeoOfUids]): Map[com.histmersion.domain.media.assets.AssetService.Implementation.GeoOfResultPart1, MediaGeoHit] =
      if uids.isEmpty then Map.empty else db.read(MediaRepo.geoOf(uids))
    def thumbs(targetType: com.histmersion.domain.media.assets.AssetService.Implementation.ThumbsTargetType, targetId: com.histmersion.domain.media.assets.AssetService.Implementation.ThumbsTargetId, limit: com.histmersion.domain.media.assets.AssetService.Implementation.ThumbsLimit): List[MediaThumb] =
      db.read(MediaRepo.thumbsIn(targetType, targetId, limit))
    def placeThumbs(placeId: com.histmersion.domain.catalogue.Place.Id, sameSiteKm: com.histmersion.domain.media.assets.AssetService.Implementation.PlaceThumbsSameSiteKm, limit: com.histmersion.domain.media.assets.AssetService.Implementation.PlaceThumbsLimit): List[MediaThumb] =
      db.read(MediaRepo.placeThumbsIn(placeId, sameSiteKm, limit))

  object Implementation:
    type FigureResultPart1 = FigureResultPart1.T
    object FigureResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FigureResultPart2 = FigureResultPart2.T
    object FigureResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Byte, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FigureName = FigureName.T
    object FigureName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FigureCountResult = FigureCountResult.T
    object FigureCountResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type AddFiguresResult = AddFiguresResult.T
    object AddFiguresResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ReplaceFiguresResult = ReplaceFiguresResult.T
    object ReplaceFiguresResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type DetailName = DetailName.T
    object DetailName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PatchName = PatchName.T
    object PatchName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type KnownResultPart1 = KnownResultPart1.T
    object KnownResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type KnownProvider = KnownProvider.T
    object KnownProvider extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type KnownKeys = KnownKeys.T
    object KnownKeys extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SaveResultPart1 = SaveResultPart1.T
    object SaveResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SaveResultPart2 = SaveResultPart2.T
    object SaveResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SaveFirstTargetPart1 = SaveFirstTargetPart1.T
    object SaveFirstTargetPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SaveFirstTargetPart2 = SaveFirstTargetPart2.T
    object SaveFirstTargetPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RefreshRightsUid = RefreshRightsUid.T
    object RefreshRightsUid extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SetThumbUid = SetThumbUid.T
    object SetThumbUid extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SetThumbBytes = SetThumbBytes.T
    object SetThumbBytes extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Byte, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SetThumbMime = SetThumbMime.T
    object SetThumbMime extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SetThumbWidth = SetThumbWidth.T
    object SetThumbWidth extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SetThumbHeight = SetThumbHeight.T
    object SetThumbHeight extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LinkResult = LinkResult.T
    object LinkResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LinkUid = LinkUid.T
    object LinkUid extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LinkTargetType = LinkTargetType.T
    object LinkTargetType extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LinkTargetId = LinkTargetId.T
    object LinkTargetId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LinkRole = LinkRole.T
    object LinkRole extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LinkProperty = LinkProperty.T
    object LinkProperty extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LinkItem = LinkItem.T
    object LinkItem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LinkRank = LinkRank.T
    object LinkRank extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LinkBasis = LinkBasis.T
    object LinkBasis extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SetSourcePictureUid = SetSourcePictureUid.T
    object SetSourcePictureUid extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SetSourcePictureRole = SetSourcePictureRole.T
    object SetSourcePictureRole extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SetSourcePictureProperty = SetSourcePictureProperty.T
    object SetSourcePictureProperty extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SourcePictureResultPart1 = SourcePictureResultPart1.T
    object SourcePictureResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SourcePictureResultPart2 = SourcePictureResultPart2.T
    object SourcePictureResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SourcePictureRole = SourcePictureRole.T
    object SourcePictureRole extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ListLimit = ListLimit.T
    object ListLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ShowUid = ShowUid.T
    object ShowUid extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ThumbResultPart1 = ThumbResultPart1.T
    object ThumbResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ThumbResultPart2 = ThumbResultPart2.T
    object ThumbResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Byte, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ThumbUid = ThumbUid.T
    object ThumbUid extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FileResultPart1 = FileResultPart1.T
    object FileResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FileResultPart2 = FileResultPart2.T
    object FileResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Byte, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FileUid = FileUid.T
    object FileUid extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type GeoLimit = GeoLimit.T
    object GeoLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type GeoOfResultPart1 = GeoOfResultPart1.T
    object GeoOfResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type GeoOfUids = GeoOfUids.T
    object GeoOfUids extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ThumbsTargetType = ThumbsTargetType.T
    object ThumbsTargetType extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ThumbsTargetId = ThumbsTargetId.T
    object ThumbsTargetId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ThumbsLimit = ThumbsLimit.T
    object ThumbsLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PlaceThumbsSameSiteKm = PlaceThumbsSameSiteKm.T
    object PlaceThumbsSameSiteKm extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PlaceThumbsLimit = PlaceThumbsLimit.T
    object PlaceThumbsLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** Media's pictures alone, in memory: no place, event or source is held, so a provider's picture gets no point or
    * period from the target it is stored for, and a place's card shows nothing (the place is not there to have
    * equivalents); `clock` stands in for the database's `now()`, `newId` for its uids.
    */
  final class InMemory(
      state: AtomicReference[MediaState],
      clock: Clock,
      newId: () => UUID = () => UUID.randomUUID()
  ) extends AssetService:

    private def now: Instant = Instant.now(clock)
    private def held: Vector[HeldAsset] = state.get.assets
    private def ofSource(citekey: com.histmersion.domain.library.Citekey): Vector[HeldAsset] = held.filter(_.citekey.contains(citekey))
    private def named(citekey: com.histmersion.domain.library.Citekey, name: com.histmersion.domain.media.assets.AssetService.InMemory.NamedName): Option[HeldAsset] = ofSource(citekey).find(_.name == name)
    private def byUid(uid: com.histmersion.domain.media.assets.AssetService.InMemory.ByUidUid): Option[HeldAsset] = held.find(_.uid == uid)
    private def clean(o: Option[com.histmersion.domain.media.assets.AssetService.InMemory.CleanO]): Option[com.histmersion.domain.media.assets.AssetService.InMemory.CleanResult] = o.map(_.trim).filter(_.nonEmpty)

    private def change(uid: com.histmersion.domain.media.assets.AssetService.InMemory.ChangeUid)(f: HeldAsset => HeldAsset): Unit =
      val _ = state.updateAndGet(_.modify(_.assets).using(_.map(a => if a.uid == uid then f(a) else a)))

    def figures(citekey: com.histmersion.domain.library.Citekey): List[AssetInfo] =
      ofSource(citekey)
        .sortBy(_.name)
        .toList
        .map(a => AssetInfo(a.name, a.mime, a.bytes.length, com.histmersion.api.Sha256(a.sha256), a.width, a.height))

    def figure(citekey: com.histmersion.domain.library.Citekey, name: com.histmersion.domain.media.assets.AssetService.InMemory.FigureName): Option[(com.histmersion.domain.media.assets.AssetService.InMemory.FigureResultPart1, Array[com.histmersion.domain.media.assets.AssetService.InMemory.FigureResultPart2])] =
      named(citekey, name).map(a => (a.mime, a.bytes))

    def figureCount(citekey: com.histmersion.domain.library.Citekey): com.histmersion.domain.media.assets.AssetService.InMemory.FigureCountResult = ofSource(citekey).size

    private def newFigure(citekey: com.histmersion.domain.library.Citekey, f: NewFigure): HeldAsset =
      HeldAsset(newId().toString, Some(citekey), f.name, f.mime, f.bytes, f.sha256, f.width, f.height)

    def addFigures(citekey: com.histmersion.domain.library.Citekey, figures: List[NewFigure]): com.histmersion.domain.media.assets.AssetService.InMemory.AddFiguresResult =
      val incoming = NewFigure.dedupe(figures)
      val _ = state.updateAndGet { s =>
        val taken = s.assets.filter(_.citekey.contains(citekey)).map(_.name).toSet
        s.modify(_.assets).using(_ ++ incoming.filterNot(f => taken(f.name)).map(newFigure(citekey, _)))
      }
      incoming.size

    def replaceFigures(citekey: com.histmersion.domain.library.Citekey, figures: List[NewFigure]): com.histmersion.domain.media.assets.AssetService.InMemory.ReplaceFiguresResult =
      val incoming = NewFigure.dedupe(figures)
      val _ = state.updateAndGet { s =>
        val mine = s.assets.filter(_.citekey.contains(citekey)).sortBy(_.name)
        val plan = Reconciled.of(mine.map(a => a.name -> a.sha256).toList, incoming.map(f => f.name -> f.sha256))
        val gone = plan.deletes.toSet
        val renamed = plan.renames.toMap
        val goneUids = mine.filter(a => gone(a.name)).map(_.uid).toSet
        val kept = s.assets
          .filterNot(a => goneUids(a.uid))
          .map(a =>
            if a.citekey.contains(citekey) then renamed.get(a.name).fold(a)(n => a.modify(_.name).setTo(n)) else a
          )
        val links = s.assetLinks
          .filterNot(l => l.citekey == citekey && gone(l.assetName))
          .map(l =>
            if l.citekey == citekey then renamed.get(l.assetName).fold(l)(n => l.modify(_.assetName).setTo(n)) else l
          )
        val written = incoming.foldLeft(kept) { (as, f) =>
          as.indexWhere(a => a.citekey.contains(citekey) && a.name == f.name) match
            case -1 => as :+ newFigure(citekey, f)
            case i  =>
              val a = as(i)
              if (a.mime, a.sha256, a.width, a.height) == (f.mime, f.sha256, f.width, f.height) then as
              else
                as.updated(
                  i,
                  a.modify(_.mime)
                    .setTo(f.mime)
                    .modify(_.bytes)
                    .setTo(f.bytes)
                    .modify(_.sha256)
                    .setTo(f.sha256)
                    .modify(_.width)
                    .setTo(f.width)
                    .modify(_.height)
                    .setTo(f.height)
                )
        }
        s.modify(_.assets)
          .setTo(written)
          .modify(_.assetLinks)
          .setTo(links)
          .modify(_.mediaLinks)
          .using(_.filterNot(m => goneUids(m.assetUid)))
      }
      incoming.size

    def detail(citekey: com.histmersion.domain.library.Citekey, name: com.histmersion.domain.media.assets.AssetService.InMemory.DetailName): Option[AssetDetail] =
      named(citekey, name).map { a =>
        AssetDetail(
          a.name,
          a.mime,
          a.bytes.length,
          a.sha256,
          a.width,
          a.height,
          a.kind,
          a.caption,
          a.license,
          a.point.map(_._1),
          a.point.map(_._2),
          a.period.flatMap(_._1),
          a.period.flatMap(_._2)
        )
      }

    def patch(citekey: com.histmersion.domain.library.Citekey, name: com.histmersion.domain.media.assets.AssetService.InMemory.PatchName, p: AssetPatch): Either[Api.Error, AssetDetail] =
      named(citekey, name) match
        case None    => Left(Api.Error(s"No asset '$citekey/$name'", Some(Write.CodeNotFound)))
        case Some(a) =>
          change(a.uid) { x =>
            val point = (p.lat, p.lon) match
              case (Some(lat), Some(lon)) => Some((lat, lon))
              case _                      => x.point
            x.modify(_.kind)
              .using(p.kind.orElse(_))
              .modify(_.caption)
              .using(p.caption.orElse(_))
              .modify(_.license)
              .using(p.license.orElse(_))
              .modify(_.point)
              .setTo(point)
          }
          val stored = named(citekey, name).flatMap(_.period).getOrElse((None, None))
          RangeSql
            .merge(stored, p.periodStart, p.periodEnd, s"asset '$citekey/$name'")
            .getOrElse(Right(stored))
            .map { (s, e) =>
              if (s, e) != stored then
                change(a.uid)(_.modify(_.period).setTo(Option.when(s.isDefined || e.isDefined)((s, e))))
            }
            .flatMap(_ =>
              detail(citekey, name).toRight(Api.Error(s"No asset '$citekey/$name'", Some(Write.CodeNotFound)))
            )

    def sourceFigures(citekey: com.histmersion.domain.library.Citekey): List[SourceFigure] =
      ofSource(citekey)
        .filter(_.mime.startsWith("image/"))
        .sortBy(_.name)
        .toList
        .map(a => SourceFigure(a.uid, a.name, a.width, a.height, a.thumb.isDefined))

    def known(provider: com.histmersion.domain.media.assets.AssetService.InMemory.KnownProvider, keys: List[com.histmersion.domain.media.assets.AssetService.InMemory.KnownKeys]): Map[com.histmersion.domain.media.assets.AssetService.InMemory.KnownResultPart1, Known] =
      held
        .filter(a => a.provider.contains(provider) && a.providerKey.exists(keys.contains))
        .flatMap(a => a.providerKey.map(_ -> Known(a.uid, a.license)))
        .toMap

    def save(a: ProviderAsset, firstTarget: (com.histmersion.domain.media.assets.AssetService.InMemory.SaveFirstTargetPart1, com.histmersion.domain.media.assets.AssetService.InMemory.SaveFirstTargetPart2)): (com.histmersion.domain.media.assets.AssetService.InMemory.SaveResultPart1, com.histmersion.domain.media.assets.AssetService.InMemory.SaveResultPart2) =
      val t = now
      val license = Option(a.license).filter(_.nonEmpty)
      held.find(x => x.provider.contains(a.provider) && x.providerKey.contains(a.key)) match
        case Some(x) =>
          change(x.uid)(
            _.modify(_.name)
              .setTo(a.name)
              .modify(_.mime)
              .setTo(a.mime)
              .modify(_.bytes)
              .setTo(a.bytes)
              .modify(_.sha256)
              .setTo(NewFigure.sha256(a.bytes))
              .modify(_.width)
              .setTo(a.width)
              .modify(_.height)
              .setTo(a.height)
              .modify(_.license)
              .setTo(license)
              .modify(_.sourceUrl)
              .setTo(Some(a.sourceUrl))
              .modify(_.originalUrl)
              .setTo(a.originalUrl)
              .modify(_.author)
              .setTo(a.author)
              .modify(_.credit)
              .setTo(Some(a.credit))
              .modify(_.licenseUrl)
              .setTo(a.licenseUrl)
              .modify(_.retrievedAt)
              .setTo(Some(t))
              .modify(_.thumb)
              .setTo(a.thumb)
              .modify(_.thumbMime)
              .setTo(a.thumbMime)
              .modify(_.thumbWidth)
              .setTo(a.thumbWidth)
              .modify(_.thumbHeight)
              .setTo(a.thumbHeight)
              .modify(_.kind)
              .using(_.orElse(a.kind))
          )
          (x.uid, false)
        case None =>
          val uid = newId().toString
          val row = HeldAsset(
            uid,
            None,
            a.name,
            a.mime,
            a.bytes,
            NewFigure.sha256(a.bytes),
            a.width,
            a.height,
            kind = a.kind,
            license = license,
            licenseUrl = a.licenseUrl,
            author = a.author,
            credit = Some(a.credit),
            provider = Some(a.provider),
            providerKey = Some(a.key),
            sourceUrl = Some(a.sourceUrl),
            originalUrl = a.originalUrl,
            retrievedAt = Some(t),
            thumb = a.thumb,
            thumbMime = a.thumbMime,
            thumbWidth = a.thumbWidth,
            thumbHeight = a.thumbHeight
          )
          val _ = state.updateAndGet(_.modify(_.assets).using(_ :+ row))
          (uid, true)

    def refreshRights(uid: com.histmersion.domain.media.assets.AssetService.InMemory.RefreshRightsUid, r: Rights): Unit =
      val t = now
      change(uid)(
        _.modify(_.license)
          .setTo(Some(r.license))
          .modify(_.licenseUrl)
          .setTo(r.licenseUrl)
          .modify(_.author)
          .setTo(r.author)
          .modify(_.credit)
          .setTo(Some(r.credit))
          .modify(_.retrievedAt)
          .setTo(Some(t))
      )

    def setThumb(uid: com.histmersion.domain.media.assets.AssetService.InMemory.SetThumbUid, bytes: Array[com.histmersion.domain.media.assets.AssetService.InMemory.SetThumbBytes], mime: com.histmersion.domain.media.assets.AssetService.InMemory.SetThumbMime, width: Option[com.histmersion.domain.media.assets.AssetService.InMemory.SetThumbWidth], height: Option[com.histmersion.domain.media.assets.AssetService.InMemory.SetThumbHeight]): Unit =
      change(uid)(
        _.modify(_.thumb)
          .setTo(Some(bytes))
          .modify(_.thumbMime)
          .setTo(Some(mime))
          .modify(_.thumbWidth)
          .setTo(width)
          .modify(_.thumbHeight)
          .setTo(height)
      )

    def link(
        uid: com.histmersion.domain.media.assets.AssetService.InMemory.LinkUid,
        targetType: com.histmersion.domain.media.assets.AssetService.InMemory.LinkTargetType,
        targetId: com.histmersion.domain.media.assets.AssetService.InMemory.LinkTargetId,
        role: com.histmersion.domain.media.assets.AssetService.InMemory.LinkRole,
        property: Option[com.histmersion.domain.media.assets.AssetService.InMemory.LinkProperty],
        item: Option[com.histmersion.domain.media.assets.AssetService.InMemory.LinkItem],
        rank: com.histmersion.domain.media.assets.AssetService.InMemory.LinkRank,
        basis: Option[com.histmersion.domain.media.assets.AssetService.InMemory.LinkBasis]
    ): com.histmersion.domain.media.assets.AssetService.InMemory.LinkResult =
      require(byUid(uid).isDefined, s"no picture $uid")
      def same(m: HeldMediaLink) =
        m.assetUid == uid && m.targetType == targetType && m.targetId == targetId && m.role == role
      val before = state.getAndUpdate { s =>
        if s.mediaLinks.exists(same) then
          s.modify(_.mediaLinks)
            .using(
              _.map(m =>
                if same(m) then
                  HeldMediaLink(uid, targetType, targetId, role, property, item, rank, basis.orElse(m.basis))
                else m
              )
            )
        else
          s.modify(_.mediaLinks).using(_ :+ HeldMediaLink(uid, targetType, targetId, role, property, item, rank, basis))
      }
      !before.mediaLinks.exists(same)

    def setSourcePicture(citekey: com.histmersion.domain.library.Citekey, uid: com.histmersion.domain.media.assets.AssetService.InMemory.SetSourcePictureUid, role: com.histmersion.domain.media.assets.AssetService.InMemory.SetSourcePictureRole, property: Option[com.histmersion.domain.media.assets.AssetService.InMemory.SetSourcePictureProperty]): Unit =
      def mine(m: HeldMediaLink) = m.targetType == PictureTarget.Source.wire && m.targetId == citekey && m.role == role
      val _ = state.updateAndGet { s =>
        val others = s.mediaLinks.filterNot(m => mine(m) && m.assetUid != uid)
        val updated =
          if others.exists(m => mine(m) && m.assetUid == uid) then
            others.map(m => if mine(m) && m.assetUid == uid then m.modify(_.property).setTo(property) else m)
          else others :+ HeldMediaLink(uid, PictureTarget.Source.wire, citekey, role, property, None, 0, None)
        s.modify(_.mediaLinks).setTo(updated)
      }

    def sourcePicture(citekey: com.histmersion.domain.library.Citekey, role: com.histmersion.domain.media.assets.AssetService.InMemory.SourcePictureRole): Option[(com.histmersion.domain.media.assets.AssetService.InMemory.SourcePictureResultPart1, com.histmersion.domain.media.assets.AssetService.InMemory.SourcePictureResultPart2)] =
      state.get.mediaLinks
        .find(m => m.targetType == PictureTarget.Source.wire && m.targetId == citekey && m.role == role)
        .flatMap(m => byUid(m.assetUid))
        .map(a => (a.uid, a.providerKey.getOrElse(a.name)))

    private val stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC)

    private def targetsOf(uid: com.histmersion.domain.media.assets.AssetService.InMemory.TargetsOfUid): List[MediaTarget] =
      state.get.mediaLinks
        .filter(_.assetUid == uid)
        .sortBy(m => (m.targetType, m.targetId, m.rank))
        .toList
        .map(m => MediaTarget(m.targetType, m.targetId, m.role, m.property, m.item, m.basis))

    private def item(a: HeldAsset): MediaItem =
      MediaItem(
        a.uid,
        a.name,
        a.mime,
        a.bytes.length,
        a.citekey,
        a.provider,
        a.providerKey,
        a.width,
        a.height,
        a.kind,
        a.caption,
        a.license,
        a.licenseUrl,
        a.author,
        a.credit,
        a.sourceUrl,
        a.originalUrl,
        a.retrievedAt.map(stamp.format),
        Option.when(a.thumb.isDefined)(MediaThumb.thumbPath(a.uid)),
        MediaThumb.filePath(a.uid),
        targetsOf(a.uid)
      )

    private def ofProvider(a: HeldAsset, provider: Option[com.histmersion.domain.media.assets.AssetService.InMemory.OfProviderProvider]): com.histmersion.domain.media.assets.AssetService.InMemory.OfProviderResult =
      provider.forall(p => (p == MediaProvider.Source && a.provider.isEmpty) || a.provider.contains(p))

    private def underLicence(a: HeldAsset, license: Option[com.histmersion.domain.media.assets.AssetService.InMemory.UnderLicenceLicense]): com.histmersion.domain.media.assets.AssetService.InMemory.UnderLicenceResult =
      license.forall(l => (l == "none" && a.license.forall(_.trim.isEmpty)) || a.license.contains(l))

    private def shownOn(a: HeldAsset, targetType: com.histmersion.domain.media.assets.AssetService.InMemory.ShownOnTargetType, id: Option[com.histmersion.domain.media.assets.AssetService.InMemory.ShownOnId]): com.histmersion.domain.media.assets.AssetService.InMemory.ShownOnResult =
      id.forall(i =>
        state.get.mediaLinks.exists(m => m.assetUid == a.uid && m.targetType == targetType && m.targetId == i)
      )

    def list(q: Api.MediaQuery, limit: com.histmersion.domain.media.assets.AssetService.InMemory.ListLimit): MediaList =
      val (provider, license) = (clean(q.provider), clean(q.license))
      val text = clean(q.q).map(_.toLowerCase)
      val offset = q.offset.getOrElse(0).max(0)
      def matches(a: HeldAsset) =
        text.forall(t => List(Some(a.name), a.credit, a.providerKey).flatten.exists(_.toLowerCase.contains(t)))
      val all = held
        .filter(a =>
          ofProvider(a, provider) && underLicence(a, license) && shownOn(a, PictureTarget.Place.wire, clean(q.place)) &&
            shownOn(a, PictureTarget.Event.wire, clean(q.event)) && matches(a)
        )
        .sortBy(a =>
          (
            a.retrievedAt.isEmpty,
            a.retrievedAt.map(t => -(t.getEpochSecond * 1000000L + t.getNano / 1000)),
            a.citekey.isEmpty,
            a.citekey,
            a.name
          )
        )
      MediaList.of(all.size, all.slice(offset, offset + limit.max(1)).toList.map(item), offset)

    def show(uid: com.histmersion.domain.media.assets.AssetService.InMemory.ShowUid): Option[MediaItem] = byUid(uid).map(item)

    def thumb(uid: com.histmersion.domain.media.assets.AssetService.InMemory.ThumbUid): Option[(com.histmersion.domain.media.assets.AssetService.InMemory.ThumbResultPart1, Array[com.histmersion.domain.media.assets.AssetService.InMemory.ThumbResultPart2])] =
      byUid(uid).flatMap { a =>
        a.thumb match
          case Some(t) => Some((a.thumbMime.getOrElse(a.mime), t))
          case None
              if a.provider.isEmpty || a.provider
                .exists(p => p == MediaProvider.Calibre || p == MediaProvider.OpenLibrary) =>
            Some((a.mime, a.bytes))
          case None => None
      }

    def file(uid: com.histmersion.domain.media.assets.AssetService.InMemory.FileUid): Option[(com.histmersion.domain.media.assets.AssetService.InMemory.FileResultPart1, Array[com.histmersion.domain.media.assets.AssetService.InMemory.FileResultPart2])] = byUid(uid).map(a => (a.mime, a.bytes))

    def facets: MediaFacets =
      def counted(keys: Vector[String]): List[MediaCount] =
        keys.groupBy(identity).toList.map((k, v) => MediaCount(k, v.size)).sortBy(c => (-c.n, c.key))
      MediaFacets(
        counted(held.map(_.provider.getOrElse(MediaProvider.Source))),
        counted(held.map(_.license.map(_.trim).filter(_.nonEmpty).getOrElse("none")))
      )

    private def hit(a: HeldAsset, p: (com.histmersion.domain.media.assets.AssetService.InMemory.HitPPart1, com.histmersion.domain.media.assets.AssetService.InMemory.HitPPart2)): MediaGeoHit =
      MediaGeoHit(
        a.citekey,
        a.name,
        a.kind,
        p._1,
        p._2,
        a.period.flatMap(_._1),
        a.period.flatMap(_._2),
        a.caption,
        a.uid,
        a.provider,
        a.license,
        a.credit,
        Option.when(a.thumb.isDefined)(MediaThumb.thumbPath(a.uid)),
        fileUrl = Some(MediaThumb.filePath(a.uid)),
        sourceUrl = a.sourceUrl,
        licenseUrl = a.licenseUrl,
        author = a.author
      )

    def geo(q: Api.MediaGeoQuery, limit: com.histmersion.domain.media.assets.AssetService.InMemory.GeoLimit): List[MediaGeoHit] =
      val k = q.kind.map(_.trim.toLowerCase).filter(_.nonEmpty)
      val (provider, license) = (clean(q.provider), clean(q.license))
      def inSpan(a: HeldAsset) = q.from.forall { from =>
        a.period.exists((s, e) => s.forall(x => q.to.forall(x < _)) && e.forall(from <= _))
      }
      held
        .filter(a =>
          a.point.isDefined && k.forall(a.kind.contains) && inSpan(a) && ofProvider(a, provider) &&
            underLicence(a, license)
        )
        .sortBy(a => (a.period.flatMap(_._1).isEmpty, a.period.flatMap(_._1)))
        .take(limit.max(1))
        .toList
        .map(a => hit(a, a.point.get))

    def geoOf(uids: List[com.histmersion.domain.media.assets.AssetService.InMemory.GeoOfUids]): Map[com.histmersion.domain.media.assets.AssetService.InMemory.GeoOfResultPart1, MediaGeoHit] =
      held.filter(a => uids.contains(a.uid)).flatMap(a => a.point.map(p => a.uid -> hit(a, p))).toMap

    private def ord(role: com.histmersion.domain.media.assets.AssetService.InMemory.OrdRole): com.histmersion.domain.media.assets.AssetService.InMemory.OrdResult =
      MediaRole.all.indexOf(role) match
        case -1 => Int.MaxValue
        case i  => i

    def thumbs(targetType: com.histmersion.domain.media.assets.AssetService.InMemory.ThumbsTargetType, targetId: com.histmersion.domain.media.assets.AssetService.InMemory.ThumbsTargetId, limit: com.histmersion.domain.media.assets.AssetService.InMemory.ThumbsLimit): List[MediaThumb] =
      val links = state.get.mediaLinks.filter(m => m.targetType == targetType && m.targetId == targetId)
      val best = links
        .flatMap(m => byUid(m.assetUid).map(a => (m, a)))
        .filter((_, a) => a.thumb.isDefined || targetType == PictureTarget.Source.wire)
        .groupBy(_._2.uid)
        .values
        .map(_.minBy((m, _) => (ord(m.role), m.rank)))
        .toList
        .sortBy((m, a) => (ord(m.role), m.rank, a.uid))
      best.take(limit.max(1)).map { (m, a) =>
        MediaThumb(
          a.uid,
          m.role,
          MediaThumb.thumbPath(a.uid),
          a.credit.getOrElse(""),
          a.license,
          a.licenseUrl,
          a.sourceUrl,
          a.thumbWidth.orElse(a.width),
          a.thumbHeight.orElse(a.height)
        )
      }

    def placeThumbs(placeId: com.histmersion.domain.catalogue.Place.Id, sameSiteKm: com.histmersion.domain.media.assets.AssetService.InMemory.PlaceThumbsSameSiteKm, limit: com.histmersion.domain.media.assets.AssetService.InMemory.PlaceThumbsLimit): List[MediaThumb] = Nil

  object InMemory:
    type NamedName = NamedName.T
    object NamedName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ByUidUid = ByUidUid.T
    object ByUidUid extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CleanResult = CleanResult.T
    object CleanResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CleanO = CleanO.T
    object CleanO extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ChangeUid = ChangeUid.T
    object ChangeUid extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FigureResultPart1 = FigureResultPart1.T
    object FigureResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FigureResultPart2 = FigureResultPart2.T
    object FigureResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Byte, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FigureName = FigureName.T
    object FigureName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FigureCountResult = FigureCountResult.T
    object FigureCountResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type AddFiguresResult = AddFiguresResult.T
    object AddFiguresResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ReplaceFiguresResult = ReplaceFiguresResult.T
    object ReplaceFiguresResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type DetailName = DetailName.T
    object DetailName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PatchName = PatchName.T
    object PatchName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type KnownResultPart1 = KnownResultPart1.T
    object KnownResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type KnownProvider = KnownProvider.T
    object KnownProvider extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type KnownKeys = KnownKeys.T
    object KnownKeys extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SaveResultPart1 = SaveResultPart1.T
    object SaveResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SaveResultPart2 = SaveResultPart2.T
    object SaveResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SaveFirstTargetPart1 = SaveFirstTargetPart1.T
    object SaveFirstTargetPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SaveFirstTargetPart2 = SaveFirstTargetPart2.T
    object SaveFirstTargetPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RefreshRightsUid = RefreshRightsUid.T
    object RefreshRightsUid extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SetThumbUid = SetThumbUid.T
    object SetThumbUid extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SetThumbBytes = SetThumbBytes.T
    object SetThumbBytes extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Byte, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SetThumbMime = SetThumbMime.T
    object SetThumbMime extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SetThumbWidth = SetThumbWidth.T
    object SetThumbWidth extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SetThumbHeight = SetThumbHeight.T
    object SetThumbHeight extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LinkResult = LinkResult.T
    object LinkResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LinkUid = LinkUid.T
    object LinkUid extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LinkTargetType = LinkTargetType.T
    object LinkTargetType extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LinkTargetId = LinkTargetId.T
    object LinkTargetId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LinkRole = LinkRole.T
    object LinkRole extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LinkProperty = LinkProperty.T
    object LinkProperty extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LinkItem = LinkItem.T
    object LinkItem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LinkRank = LinkRank.T
    object LinkRank extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LinkBasis = LinkBasis.T
    object LinkBasis extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SetSourcePictureUid = SetSourcePictureUid.T
    object SetSourcePictureUid extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SetSourcePictureRole = SetSourcePictureRole.T
    object SetSourcePictureRole extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SetSourcePictureProperty = SetSourcePictureProperty.T
    object SetSourcePictureProperty extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SourcePictureResultPart1 = SourcePictureResultPart1.T
    object SourcePictureResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SourcePictureResultPart2 = SourcePictureResultPart2.T
    object SourcePictureResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SourcePictureRole = SourcePictureRole.T
    object SourcePictureRole extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type TargetsOfUid = TargetsOfUid.T
    object TargetsOfUid extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OfProviderResult = OfProviderResult.T
    object OfProviderResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OfProviderProvider = OfProviderProvider.T
    object OfProviderProvider extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type UnderLicenceResult = UnderLicenceResult.T
    object UnderLicenceResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type UnderLicenceLicense = UnderLicenceLicense.T
    object UnderLicenceLicense extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ShownOnResult = ShownOnResult.T
    object ShownOnResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ShownOnTargetType = ShownOnTargetType.T
    object ShownOnTargetType extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ShownOnId = ShownOnId.T
    object ShownOnId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ListLimit = ListLimit.T
    object ListLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ShowUid = ShowUid.T
    object ShowUid extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ThumbResultPart1 = ThumbResultPart1.T
    object ThumbResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ThumbResultPart2 = ThumbResultPart2.T
    object ThumbResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Byte, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ThumbUid = ThumbUid.T
    object ThumbUid extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FileResultPart1 = FileResultPart1.T
    object FileResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FileResultPart2 = FileResultPart2.T
    object FileResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Byte, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FileUid = FileUid.T
    object FileUid extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type HitPPart1 = HitPPart1.T
    object HitPPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type HitPPart2 = HitPPart2.T
    object HitPPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type GeoLimit = GeoLimit.T
    object GeoLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type GeoOfResultPart1 = GeoOfResultPart1.T
    object GeoOfResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type GeoOfUids = GeoOfUids.T
    object GeoOfUids extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OrdResult = OrdResult.T
    object OrdResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OrdRole = OrdRole.T
    object OrdRole extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ThumbsTargetType = ThumbsTargetType.T
    object ThumbsTargetType extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ThumbsTargetId = ThumbsTargetId.T
    object ThumbsTargetId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ThumbsLimit = ThumbsLimit.T
    object ThumbsLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PlaceThumbsSameSiteKm = PlaceThumbsSameSiteKm.T
    object PlaceThumbsSameSiteKm extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PlaceThumbsLimit = PlaceThumbsLimit.T
    object PlaceThumbsLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
