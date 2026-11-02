package com.histmersion.domain.media.assets

import com.histmersion.api.{Api, Write}
import com.histmersion.util.database.Sql

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

/** The pictures' repositories as the db suites reach them from outside the feature: each call on a connection or in a
  * transaction of its own, as the service runs it.
  */
object AssetFixtures:
  def add(ds: DataSource, citekey: String, figures: List[NewFigure]): Int =
    transact(ds)(AssetRepo.add(citekey, figures))
  def replace(ds: DataSource, citekey: String, figures: List[NewFigure]): Int =
    transact(ds)(AssetRepo.replace(citekey, figures))
  def list(ds: DataSource, citekey: String): Vector[AssetInfo] = connect(ds)(AssetRepo.list(citekey))
  def read(ds: DataSource, citekey: String, name: String): Option[(String, Array[Byte])] =
    connect(ds)(AssetRepo.read(citekey, name))
  def count(ds: DataSource, citekey: String): Int = connect(ds)(AssetRepo.count(citekey))
  def detail(ds: DataSource, citekey: String, name: String): Option[AssetDetail] =
    connect(ds)(AssetRepo.detail(citekey, name))
  def patch(ds: DataSource, citekey: String, name: String, p: AssetPatch): Either[Api.Error, AssetDetail] =
    transact(ds)(AssetRepo.patch(citekey, name, p))
      .flatMap(_ =>
        detail(ds, citekey, name).toRight(Api.Error(s"No asset '$citekey/$name'", Some(Write.CodeNotFound)))
      )

  /** What the media's `media-licence` check lists: (`citekey/name` or `media:<uid>`, what is missing). */
  def licenceGaps(ds: DataSource): List[(String, String)] = connect(ds)(MediaRepo.licenceGaps())
  def known(ds: DataSource, provider: String, keys: List[String]): Map[String, Known] =
    connect(ds)(MediaRepo.known(provider, keys))
  def save(ds: DataSource, a: ProviderAsset, firstTarget: (String, String)): (String, Boolean) =
    transact(ds)(MediaRepo.save(a, firstTarget))
  def refreshRights(ds: DataSource, uid: String, r: Rights): Unit = transact(ds)(MediaRepo.refreshRights(uid, r))
  def setThumb(ds: DataSource, uid: String, bytes: Array[Byte], mime: String, w: Option[Int], h: Option[Int]): Unit =
    transact(ds)(MediaRepo.setThumb(uid, bytes, mime, w, h))
  def link(
      ds: DataSource,
      uid: String,
      targetType: String,
      targetId: String,
      role: String,
      property: Option[String],
      item: Option[String],
      rank: Int,
      basis: Option[String] = None
  ): Boolean = transact(ds)(MediaRepo.link(uid, targetType, targetId, role, property, item, rank, basis))
  def setSourcePicture(ds: DataSource, citekey: String, uid: String, role: String, property: Option[String]): Unit =
    transact(ds)(MediaRepo.setSourcePicture(citekey, uid, role, property))
  def sourcePicture(ds: DataSource, citekey: String, role: String): Option[(String, String)] =
    connect(ds)(MediaRepo.sourcePicture(citekey, role))
  def thumbs(ds: DataSource, targetType: String, targetId: String, limit: Int = 4): List[MediaThumb] =
    connect(ds)(MediaRepo.thumbsIn(targetType, targetId, limit))
  def placeThumbs(ds: DataSource, placeId: String, sameSiteKm: Double, limit: Int = 4): List[MediaThumb] =
    connect(ds)(MediaRepo.placeThumbsIn(placeId, sameSiteKm, limit))
  def sourceThumb(ds: DataSource, citekey: String): Option[MediaThumb] =
    thumbs(ds, PictureTarget.Source.wire, citekey, 1).headOption
  def list(ds: DataSource, q: Api.MediaQuery, limit: Int): MediaList = connect(ds)(MediaRepo.list(q, limit))
  def show(ds: DataSource, uid: String): Option[MediaItem] = connect(ds)(MediaRepo.show(uid))
  def thumb(ds: DataSource, uid: String): Option[(String, Array[Byte])] = connect(ds)(MediaRepo.thumb(uid))
  def file(ds: DataSource, uid: String): Option[(String, Array[Byte])] = connect(ds)(MediaRepo.file(uid))
  def facets(ds: DataSource): MediaFacets = connect(ds)(MediaRepo.facets)
  def geo(ds: DataSource, q: Api.MediaGeoQuery, limit: Int): List[MediaGeoHit] = connect(ds)(MediaRepo.geo(q, limit))
  def geoOf(ds: DataSource, uids: List[String]): Map[String, MediaGeoHit] = connect(ds)(MediaRepo.geoOf(uids))
  def figures(ds: DataSource, citekey: String): List[SourceFigure] = connect(ds)(MediaRepo.sourceFigures(citekey))
  def tileCandidates(
      kind: Option[String],
      provider: Option[String],
      license: Option[String],
      window: Option[(Long, Long)],
      envelope: Sql
  ): Sql = MediaRepo.tileCandidates(kind, provider, license, window, envelope)
