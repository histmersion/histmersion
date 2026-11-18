package com.histmersion
package domain.media.assets
import com.histmersion.util.problem.{Legacy, Problem}

import com.histmersion.api.{Api, Write}
import com.histmersion.domain.media.MediaRole
import com.histmersion.domain.media.imports.{MediaMatch, Target}

import com.augustnagro.magnum.*
import javax.sql.DataSource
import com.histmersion.api.media.{
  AssetDetail,
  AssetInfo,
  AssetPatch,
  MediaFacets,
  MediaGeoHit,
  MediaItem,
  MediaList,
  MediaThumb
}
import com.histmersion.domain.library.Citekey
import com.histmersion.domain.catalogue.Place
import com.histmersion.domain.catalogue.evidence.EventMatcher
import com.histmersion.domain.DeepTime

/** The pictures' repositories as the db suites reach them from outside the feature: each call on a connection or in a
  * transaction of its own, as the service runs it.
  */
object AssetFixtures:
  type Limit = MediaRepo.Limit
  val Limit: MediaRepo.Limit.type = MediaRepo.Limit
  type LinkProperty = MediaRepo.LinkProperty
  val LinkProperty: MediaRepo.LinkProperty.type = MediaRepo.LinkProperty
  type LinkItem = MediaRepo.LinkItem
  val LinkItem: MediaRepo.LinkItem.type = MediaRepo.LinkItem
  type LinkRank = MediaRepo.LinkRank
  val LinkRank: MediaRepo.LinkRank.type = MediaRepo.LinkRank
  type SetSourcePictureProperty = MediaRepo.SetSourcePictureProperty
  val SetSourcePictureProperty: MediaRepo.SetSourcePictureProperty.type = MediaRepo.SetSourcePictureProperty

  def add(ds: DataSource, citekey: Citekey, figures: List[NewFigure]): RowCount =
    transact(ds)(AssetRepo.add(citekey, figures))
  def replace(ds: DataSource, citekey: Citekey, figures: List[NewFigure]): RowCount =
    transact(ds)(AssetRepo.replace(citekey, figures))
  def list(ds: DataSource, citekey: Citekey): Vector[AssetInfo] = connect(ds)(AssetRepo.list(citekey))
  def read(
      ds: DataSource,
      citekey: Citekey,
      name: AssetInfoRow.Name
  ): Option[(AssetBytesRow.Mime, AssetBytesRow.Bytes)] =
    connect(ds)(AssetRepo.read(citekey, name))
  def count(ds: DataSource, citekey: Citekey): RowCount = connect(ds)(AssetRepo.count(citekey))
  def detail(ds: DataSource, citekey: Citekey, name: AssetInfoRow.Name): Option[AssetDetail] =
    connect(ds)(AssetRepo.detail(citekey, name))
  def patch(
      ds: DataSource,
      citekey: Citekey,
      name: AssetInfoRow.Name,
      p: AssetPatch
  ): Either[Api.Error, AssetDetail] =
    transact(ds)(AssetRepo.patch(citekey, name, p))
      .flatMap(_ =>
        detail(ds, citekey, name).toRight(
          Api.Error(
            Problem.Unclassified(Legacy.Text(s"No asset '$citekey/$name'")),
            Some(Api.Error.Code(Write.CodeNotFound))
          )
        )
      )

  /** What the media's `media-licence` check lists: (`citekey/name` or `media:<uid>`, what is missing). */
  def licenceGaps(ds: DataSource): List[(MediaRepo.GapItem, MediaRepo.GapDetail)] = connect(ds)(MediaRepo.licenceGaps())
  def known(
      ds: DataSource,
      provider: MediaItemRow.Provider,
      keys: List[MediaItemRow.ProviderKey]
  ): Map[MediaItemRow.ProviderKey, Known] =
    connect(ds)(MediaRepo.known(provider, keys))
  def save(
      ds: DataSource,
      a: ProviderAsset,
      firstTarget: (Target.TargetType, Target.Id)
  ): (MediaItemRow.Uid, Boolean) =
    transact(ds)(MediaRepo.save(a, firstTarget))
  def refreshRights(ds: DataSource, uid: MediaItemRow.Uid, r: Rights): Unit =
    transact(ds)(MediaRepo.refreshRights(uid, r))
  def setThumb(
      ds: DataSource,
      uid: MediaItemRow.Uid,
      bytes: ProviderAsset.Thumb,
      mime: ProviderAsset.ThumbMime,
      w: Option[MediaThumbRow.Width],
      h: Option[MediaThumbRow.Height]
  ): Unit =
    transact(ds)(MediaRepo.setThumb(uid, bytes, mime, w, h))
  def link(
      ds: DataSource,
      uid: MediaItemRow.Uid,
      targetType: Target.TargetType,
      targetId: Target.Id,
      role: MediaRole.Wire,
      property: Option[MediaRepo.LinkProperty],
      item: Option[MediaRepo.LinkItem],
      rank: MediaRepo.LinkRank,
      basis: Option[MediaMatch.Basis] = None
  ): Boolean = transact(ds)(MediaRepo.link(uid, targetType, targetId, role, property, item, rank, basis))
  def setSourcePicture(
      ds: DataSource,
      citekey: Citekey,
      uid: MediaItemRow.Uid,
      role: MediaRole.Wire,
      property: Option[MediaRepo.SetSourcePictureProperty]
  ): Unit =
    transact(ds)(MediaRepo.setSourcePicture(citekey, uid, role, property))
  def sourcePicture(
      ds: DataSource,
      citekey: Citekey,
      role: MediaRole.Wire
  ): Option[(MediaItemRow.Uid, MediaRepo.PictureKey)] =
    connect(ds)(MediaRepo.sourcePicture(citekey, role))
  def thumbs(
      ds: DataSource,
      targetType: Target.TargetType,
      targetId: Target.Id,
      limit: MediaRepo.Limit = MediaRepo.Limit(4)
  ): List[MediaThumb] =
    connect(ds)(MediaRepo.thumbsIn(targetType, targetId, limit))
  def placeThumbs(
      ds: DataSource,
      placeId: Place.Id,
      sameSiteKm: EventMatcher.NearFinding.Km,
      limit: MediaRepo.Limit = MediaRepo.Limit(4)
  ): List[MediaThumb] =
    connect(ds)(MediaRepo.placeThumbsIn(placeId, sameSiteKm, limit))
  def sourceThumb(ds: DataSource, citekey: Citekey): Option[MediaThumb] =
    thumbs(
      ds,
      Target.TargetType(PictureTarget.Source.wire.value),
      Target.Id(citekey.value),
      MediaRepo.Limit(1)
    ).headOption
  def list(ds: DataSource, q: Api.MediaQuery, limit: MediaRepo.Limit): MediaList = connect(ds)(MediaRepo.list(q, limit))
  def show(ds: DataSource, uid: MediaItemRow.Uid): Option[MediaItem] = connect(ds)(MediaRepo.show(uid))
  def thumb(ds: DataSource, uid: MediaItemRow.Uid): Option[(ProviderAsset.ThumbMime, ProviderAsset.Thumb)] =
    connect(ds)(MediaRepo.thumb(uid))
  def file(ds: DataSource, uid: MediaItemRow.Uid): Option[(AssetBytesRow.Mime, AssetBytesRow.Bytes)] =
    connect(ds)(MediaRepo.file(uid))
  def facets(ds: DataSource): MediaFacets = connect(ds)(MediaRepo.facets)
  def geo(ds: DataSource, q: Api.MediaGeoQuery, limit: MediaRepo.Limit): List[MediaGeoHit] =
    connect(ds)(MediaRepo.geo(q, limit))
  def geoOf(ds: DataSource, uids: List[MediaItemRow.Uid]): Map[MediaItemRow.Uid, MediaGeoHit] =
    connect(ds)(MediaRepo.geoOf(uids))
  def figures(ds: DataSource, citekey: Citekey): List[SourceFigure] =
    connect(ds)(MediaRepo.sourceFigures(citekey))
  def tileCandidates(
      kind: Option[MediaItemRow.Kind],
      provider: Option[MediaItemRow.Provider],
      license: Option[MediaItemRow.License],
      window: Option[(DeepTime.SignedYear, DeepTime.SignedYear)],
      envelope: Frag
  ): Frag = MediaRepo.tileCandidates(kind, provider, license, window, envelope)
