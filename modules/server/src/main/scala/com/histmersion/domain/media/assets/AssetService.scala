package com.histmersion
package domain.media.assets

import com.histmersion.api.Problem
import com.histmersion.api.{Api, Write}
import com.histmersion.api.exchange.Bundle
import com.histmersion.domain.DeepTime
import com.histmersion.domain.catalogue.evidence.EventMatcher
import com.histmersion.domain.media.MediaState
import com.histmersion.domain.media.imports.{MediaMatch, Target}
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
  ImageSize,
  MediaCount,
  MediaFacets,
  MediaGeoHit,
  MediaItem,
  MediaList,
  MediaProvider,
  MediaTarget,
  MediaThumb
}
import com.histmersion.domain.library.Citekey
import com.histmersion.domain.catalogue.Place
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.ByteCount
import com.histmersion.api.Sha256
import com.histmersion.api.catalogue.Latitude
import com.histmersion.api.catalogue.Longitude
import com.histmersion.api.catalogue.Year
import com.histmersion.api.personal.UnreadPage

/** The pictures (`assets`) and where they are shown (`media_links`): a source's figures stored, read, described and
  * patched; a provider's pictures stored, re-read and given thumbnails; the places, events and sources they are shown
  * on; the media list, its facets, a picture, its file and thumbnail, the map feed and the cards' pictures.
  */
trait AssetService:

  // ── a source's figures ──

  def figures(citekey: Citekey): List[AssetInfo]

  /** A figure's mime type and bytes. */
  def figure(citekey: Citekey, name: AssetService.AssetName): Option[(AssetService.Mime, AssetService.Bytes)]

  def figureCount(citekey: Citekey): AssetService.FigureCount

  /** Figures added, any stored under the same name left alone; how many were written (after deduplication). */
  def addFigures(citekey: Citekey, figures: List[NewFigure]): AssetService.AddedFigureCount

  /** The whole set replaced (a re-conversion): the rows of the pictures it still has kept, renamed when only their name
    * moved; how many were kept.
    */
  def replaceFigures(citekey: Citekey, figures: List[NewFigure]): AssetService.ReplacedFigureCount

  def detail(citekey: Citekey, name: AssetService.AssetName): Option[AssetDetail]

  /** A figure's description changed; a period that would end before it starts is refused, the other fields kept. */
  def patch(citekey: Citekey, name: AssetService.AssetName, p: AssetPatch): Either[Api.Error, AssetDetail]

  /** A source's image figures, as the illustration's choice weighs them. */
  def sourceFigures(citekey: Citekey): List[SourceFigure]

  // ── a provider's pictures and where they are shown ──

  def known(provider: AssetService.Provider, keys: List[AssetService.ProviderKey]): Map[AssetService.ProviderKey, Known]

  /** A provider's picture stored, or its bytes and rights replaced; its uid and whether it is new. */
  def save(
      a: ProviderAsset,
      firstTarget: (AssetService.TargetType, AssetService.TargetId)
  ): (AssetService.AssetUid, Boolean)

  def refreshRights(uid: AssetService.AssetUid, r: Rights): Unit

  def setThumb(
      uid: AssetService.AssetUid,
      bytes: ProviderAsset.Thumb,
      mime: ProviderAsset.ThumbMime,
      width: Option[ProviderAsset.ThumbWidth],
      height: Option[ProviderAsset.ThumbHeight]
  ): Unit

  /** A picture shown on a place or event in a role; whether the link is new. */
  def link(
      uid: AssetService.AssetUid,
      targetType: AssetService.TargetType,
      targetId: AssetService.TargetId,
      role: MediaRole.Wire,
      property: Option[AssetService.LinkProperty],
      item: Option[AssetService.LinkItem],
      rank: AssetService.LinkRank,
      basis: Option[MediaMatch.Basis]
  ): Boolean

  /** `uid` made the source's picture in `role`, replacing the one it had in that role. */
  def setSourcePicture(
      citekey: Citekey,
      uid: AssetService.AssetUid,
      role: MediaRole.Wire,
      property: Option[AssetService.LinkProperty]
  ): Unit

  /** The source's picture in `role`: its uid and its provider key or figure name. */
  def sourcePicture(citekey: Citekey, role: MediaRole.Wire): Option[(AssetService.AssetUid, AssetService.PictureKey)]

  // ── reads ──

  def list(q: Api.MediaQuery, limit: AssetService.Limit): MediaList
  def show(uid: AssetService.AssetUid): Option[MediaItem]

  /** The thumbnail's type and bytes — a source's figure (or a cover) without one is its own thumbnail. */
  def thumb(uid: AssetService.AssetUid): Option[(AssetService.Mime, AssetService.Bytes)]

  def file(uid: AssetService.AssetUid): Option[(AssetService.Mime, AssetService.Bytes)]
  def facets: MediaFacets
  def geo(q: Api.MediaGeoQuery, limit: AssetService.Limit): List[MediaGeoHit]
  def geoOf(uids: List[AssetService.AssetUid]): Map[AssetService.AssetUid, MediaGeoHit]

  /** A place's, an event's or a source's pictures for its card, best first. */
  def thumbs(
      targetType: AssetService.TargetType,
      targetId: AssetService.TargetId,
      limit: AssetService.Limit
  ): List[MediaThumb]

  /** A place's pictures for its card, through its equivalents (its old ids, its Wikidata items, a point of the same
    * name within `sameSiteKm`).
    */
  def placeThumbs(
      placeId: Place.Id,
      sameSiteKm: EventMatcher.NearFinding.Km,
      limit: AssetService.Limit
  ): List[MediaThumb]

object AssetService:
  type AddedFigureCount = RowCount
  type FigureCount = RowCount
  type ReplacedFigureCount = RowCount
  type AssetName = AssetInfoRow.Name
  val AssetName: AssetInfoRow.Name.type = AssetInfoRow.Name
  type AssetUid = MediaItemRow.Uid
  val AssetUid: MediaItemRow.Uid.type = MediaItemRow.Uid
  type Provider = MediaItemRow.Provider
  val Provider: MediaItemRow.Provider.type = MediaItemRow.Provider
  type ProviderKey = MediaItemRow.ProviderKey
  val ProviderKey: MediaItemRow.ProviderKey.type = MediaItemRow.ProviderKey
  type TargetType = Target.TargetType
  val TargetType: Target.TargetType.type = Target.TargetType
  type TargetId = Target.Id
  val TargetId: Target.Id.type = Target.Id
  type Bytes = Bytes.T
  object Bytes extends Newtype[Array[Byte], Pure]
  type Mime = Mime.T
  object Mime extends Newtype[String, Pure]

  type Limit = Limit.T
  object Limit extends Newtype[Int, Pure]
  type LinkProperty = LinkProperty.T
  object LinkProperty extends Newtype[String, Pure]
  type LinkRole = MediaRole.Wire
  val LinkRole: MediaRole.Wire.type = MediaRole.Wire
  type LinkBasis = MediaMatch.Basis
  val LinkBasis: MediaMatch.Basis.type = MediaMatch.Basis
  type LinkItem = LinkItem.T
  object LinkItem extends Newtype[String, Pure]
  type LinkRank = LinkRank.T
  object LinkRank extends Newtype[Int, Pure]
  type PictureKey = PictureKey.T
  object PictureKey extends Newtype[String, Pure]

  def apply(db: Transactor): AssetService = Implementation(db)

  final private class Implementation(db: Transactor) extends AssetService:
    def figures(citekey: Citekey): List[AssetInfo] = db.read(AssetRepo.list(citekey)).toList
    def figure(citekey: Citekey, name: AssetName): Option[(Mime, Bytes)] =
      db.read(AssetRepo.read(citekey, name)).map((m, b) => (Mime(m.value), Bytes(b.value)))
    def figureCount(citekey: Citekey): FigureCount = db.read(AssetRepo.count(citekey))
    def addFigures(citekey: Citekey, figures: List[NewFigure]): AddedFigureCount =
      db.transact(AssetRepo.add(citekey, figures))
    def replaceFigures(citekey: Citekey, figures: List[NewFigure]): ReplacedFigureCount =
      db.transact(AssetRepo.replace(citekey, figures))
    def detail(citekey: Citekey, name: AssetName): Option[AssetDetail] = db.read(AssetRepo.detail(citekey, name))
    def patch(citekey: Citekey, name: AssetName, p: AssetPatch): Either[Api.Error, AssetDetail] =
      db.transact(AssetRepo.patch(citekey, name, p))
        .flatMap(_ =>
          detail(citekey, name).toRight(
            Api.Error(Problem(s"No asset '$citekey/$name'"), Some(Api.Error.Code(Write.CodeNotFound)))
          )
        )
    def sourceFigures(citekey: Citekey): List[SourceFigure] = db.read(MediaRepo.sourceFigures(citekey))
    def known(provider: Provider, keys: List[ProviderKey]): Map[ProviderKey, Known] =
      db.read(MediaRepo.known(provider, keys))
    def save(a: ProviderAsset, firstTarget: (TargetType, TargetId)): (AssetUid, Boolean) =
      db.transact(MediaRepo.save(a, firstTarget))
    def refreshRights(uid: AssetUid, r: Rights): Unit = db.transact(MediaRepo.refreshRights(uid, r))
    def setThumb(
        uid: AssetUid,
        bytes: ProviderAsset.Thumb,
        mime: ProviderAsset.ThumbMime,
        width: Option[ProviderAsset.ThumbWidth],
        height: Option[ProviderAsset.ThumbHeight]
    ): Unit =
      db.transact(
        MediaRepo.setThumb(
          uid,
          bytes,
          mime,
          width.map(w => MediaThumbRow.Width(w.value)),
          height.map(h => MediaThumbRow.Height(h.value))
        )
      )
    def link(
        uid: AssetUid,
        targetType: TargetType,
        targetId: TargetId,
        role: MediaRole.Wire,
        property: Option[LinkProperty],
        item: Option[LinkItem],
        rank: LinkRank,
        basis: Option[MediaMatch.Basis]
    ): Boolean = db.transact(
      MediaRepo.link(
        uid,
        targetType,
        targetId,
        role,
        property.map(p => MediaRepo.LinkProperty(p.value)),
        item.map(i => MediaRepo.LinkItem(i.value)),
        MediaRepo.LinkRank(rank.value),
        basis
      )
    )
    def setSourcePicture(citekey: Citekey, uid: AssetUid, role: MediaRole.Wire, property: Option[LinkProperty]): Unit =
      db.transact(
        MediaRepo.setSourcePicture(citekey, uid, role, property.map(p => MediaRepo.SetSourcePictureProperty(p.value)))
      )
    def sourcePicture(citekey: Citekey, role: MediaRole.Wire): Option[(AssetUid, PictureKey)] =
      db.read(MediaRepo.sourcePicture(citekey, role)).map((uid, key) => (uid, PictureKey(key.value)))
    def list(q: Api.MediaQuery, limit: Limit): MediaList = db.read(MediaRepo.list(q, MediaRepo.Limit(limit.value)))
    def show(uid: AssetUid): Option[MediaItem] = db.read(MediaRepo.show(uid))
    def thumb(uid: AssetUid): Option[(Mime, Bytes)] =
      db.read(MediaRepo.thumb(uid)).map((m, b) => (Mime(m.value), Bytes(b.value)))
    def file(uid: AssetUid): Option[(Mime, Bytes)] =
      db.read(MediaRepo.file(uid)).map((m, b) => (Mime(m.value), Bytes(b.value)))
    def facets: MediaFacets = db.read(MediaRepo.facets)
    def geo(q: Api.MediaGeoQuery, limit: Limit): List[MediaGeoHit] =
      db.read(MediaRepo.geo(q, MediaRepo.Limit(limit.value)))
    // no connection for a tile with no pictures
    def geoOf(uids: List[AssetUid]): Map[AssetUid, MediaGeoHit] =
      if uids.isEmpty then Map.empty else db.read(MediaRepo.geoOf(uids))
    def thumbs(targetType: TargetType, targetId: TargetId, limit: Limit): List[MediaThumb] =
      db.read(MediaRepo.thumbsIn(targetType, targetId, MediaRepo.Limit(limit.value)))
    def placeThumbs(placeId: Place.Id, sameSiteKm: EventMatcher.NearFinding.Km, limit: Limit): List[MediaThumb] =
      db.read(MediaRepo.placeThumbsIn(placeId, sameSiteKm, MediaRepo.Limit(limit.value)))

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
    private def ofSource(citekey: Citekey): Vector[HeldAsset] = held.filter(_.citekey.contains(citekey))
    private def named(citekey: Citekey, name: AssetName): Option[HeldAsset] = ofSource(citekey).find(_.name == name)
    private def byUid(uid: AssetUid): Option[HeldAsset] = held.find(_.uid == uid)
    private def clean(o: Option[String]): Option[String] = o.map(_.trim).filter(_.nonEmpty)

    private def change(uid: AssetUid)(f: HeldAsset => HeldAsset): Unit =
      val _ = state.updateAndGet(_.modify(_.assets).using(_.map(a => if a.uid == uid then f(a) else a)))

    def figures(citekey: Citekey): List[AssetInfo] =
      ofSource(citekey)
        .sortBy(_.name.value)
        .toList
        .map(a =>
          AssetInfo(
            AssetInfo.Name(a.name.value),
            AssetInfo.Mime(a.mime.value),
            ByteCount(a.bytes.value.length.toLong),
            Sha256(a.sha256.value),
            a.width.map(w => AssetInfo.Width(w.value)),
            a.height.map(h => AssetInfo.Height(h.value))
          )
        )

    def figure(citekey: Citekey, name: AssetName): Option[(Mime, Bytes)] =
      named(citekey, name).map(a => (a.mime, a.bytes))

    def figureCount(citekey: Citekey): FigureCount = RowCount(ofSource(citekey).size)

    private def newFigure(citekey: Citekey, f: NewFigure): HeldAsset =
      HeldAsset(
        AssetUid(newId().toString),
        Some(citekey),
        AssetName(f.name.value),
        Mime(f.mime.value),
        Bytes(f.bytes.value),
        f.sha256,
        f.width.map(w => HeldAsset.Width(w.value)),
        f.height.map(h => HeldAsset.Height(h.value))
      )

    def addFigures(citekey: Citekey, figures: List[NewFigure]): AddedFigureCount =
      val incoming = NewFigure.dedupe(figures)
      val _ = state.updateAndGet { s =>
        val taken = s.assets.filter(_.citekey.contains(citekey)).map(_.name).toSet
        s.modify(_.assets)
          .using(_ ++ incoming.filterNot(f => taken(AssetName(f.name.value))).map(newFigure(citekey, _)))
      }
      RowCount(incoming.size)

    def replaceFigures(citekey: Citekey, figures: List[NewFigure]): ReplacedFigureCount =
      val incoming = NewFigure.dedupe(figures)
      val _ = state.updateAndGet { s =>
        val mine = s.assets.filter(_.citekey.contains(citekey)).sortBy(_.name.value)
        val plan = Reconciled.of(
          mine.map(a => NewFigure.Name(a.name.value) -> a.sha256).toList,
          incoming.map(f => f.name -> f.sha256)
        )
        val gone = plan.deletes.map(_.value).toSet
        val renamed = plan.renames.map((from, to) => from.value -> to).toMap
        val goneUids = mine.filter(a => gone(a.name.value)).map(_.uid).toSet
        val kept = s.assets
          .filterNot(a => goneUids(a.uid))
          .map(a =>
            if a.citekey.contains(citekey) then
              renamed.get(a.name.value).fold(a)(n => a.modify(_.name).setTo(AssetName(n.value)))
            else a
          )
        val links = s.assetLinks
          .filterNot(l => l.citekey.value == citekey.value && gone(l.assetName.value))
          .map(l =>
            if l.citekey.value == citekey.value then
              renamed.get(l.assetName.value).fold(l)(n => l.modify(_.assetName).setTo(Bundle.Asset.FileName(n.value)))
            else l
          )
        val written = incoming.foldLeft(kept) { (as, f) =>
          as.indexWhere(a => a.citekey.contains(citekey) && a.name.value == f.name.value) match
            case -1 => as :+ newFigure(citekey, f)
            case i  =>
              val a = as(i)
              if (a.mime.value, a.sha256, a.width.map(_.value), a.height.map(_.value)) == (
                  f.mime.value,
                  f.sha256,
                  f.width.map(_.value),
                  f.height.map(_.value)
                )
              then as
              else
                as.updated(
                  i,
                  a.modify(_.mime)
                    .setTo(Mime(f.mime.value))
                    .modify(_.bytes)
                    .setTo(Bytes(f.bytes.value))
                    .modify(_.sha256)
                    .setTo(f.sha256)
                    .modify(_.width)
                    .setTo(f.width.map(w => HeldAsset.Width(w.value)))
                    .modify(_.height)
                    .setTo(f.height.map(h => HeldAsset.Height(h.value)))
                )
        }
        s.modify(_.assets)
          .setTo(written)
          .modify(_.assetLinks)
          .setTo(links)
          .modify(_.mediaLinks)
          .using(_.filterNot(m => goneUids(m.assetUid)))
      }
      RowCount(incoming.size)

    def detail(citekey: Citekey, name: AssetName): Option[AssetDetail] =
      named(citekey, name).map { a =>
        AssetDetail(
          Bundle.Asset.FileName(a.name.value),
          Bundle.Asset.MimeText(a.mime.value),
          ByteCount(a.bytes.value.length.toLong),
          Sha256(a.sha256.value),
          a.width.map(w => ImageSize.Width(w.value)),
          a.height.map(h => ImageSize.Height(h.value)),
          a.kind.map(k => MediaItem.Kind(k.value)),
          a.caption.map(c => MediaItem.Caption(c.value)),
          a.license.map(l => MediaItem.License(l.value)),
          a.point.map(p => Latitude(p._1.value)),
          a.point.map(p => Longitude(p._2.value)),
          a.period.flatMap(_._1).map(y => Year(y.value)),
          a.period.flatMap(_._2).map(y => Year(y.value))
        )
      }

    def patch(citekey: Citekey, name: AssetName, p: AssetPatch): Either[Api.Error, AssetDetail] =
      def missing = Api.Error(Problem(s"No asset '$citekey/$name'"), Some(Api.Error.Code(Write.CodeNotFound)))
      named(citekey, name) match
        case None    => Left(missing)
        case Some(a) =>
          change(a.uid) { x =>
            val point = (p.lat, p.lon) match
              case (Some(lat), Some(lon)) => Some((HeldAsset.Latitude(lat.value), HeldAsset.Longitude(lon.value)))
              case _                      => x.point
            x.modify(_.kind)
              .using(old => p.kind.map(k => HeldAsset.Kind(k.value)).orElse(old))
              .modify(_.caption)
              .using(old => p.caption.map(c => HeldAsset.Caption(c.value)).orElse(old))
              .modify(_.license)
              .using(old => p.license.map(l => HeldAsset.License(l.value)).orElse(old))
              .modify(_.point)
              .setTo(point)
          }
          val stored = named(citekey, name).flatMap(_.period).getOrElse((None, None))
          RangeSql
            .merge(
              stored,
              p.periodStart.map(y => DeepTime.SignedYear(y.value)),
              p.periodEnd.map(y => DeepTime.SignedYear(y.value)),
              s"asset '$citekey/$name'"
            )
            .getOrElse(Right(stored))
            .map { (s, e) =>
              if (s, e) != stored then
                change(a.uid)(_.modify(_.period).setTo(Option.when(s.isDefined || e.isDefined)((s, e))))
            }
            .flatMap(_ => detail(citekey, name).toRight(missing))

    def sourceFigures(citekey: Citekey): List[SourceFigure] =
      ofSource(citekey)
        .filter(_.mime.value.startsWith("image/"))
        .sortBy(_.name.value)
        .toList
        .map(a =>
          SourceFigure(
            SourceFigure.Uid(a.uid.value),
            SourceFigure.Name(a.name.value),
            a.width.map(w => SourceFigure.Width(w.value)),
            a.height.map(h => SourceFigure.Height(h.value)),
            SourceFigure.HasThumb(a.thumb.isDefined)
          )
        )

    def known(provider: Provider, keys: List[ProviderKey]): Map[ProviderKey, Known] =
      held
        .filter(a => a.provider.contains(provider) && a.providerKey.exists(keys.contains))
        .flatMap(a => a.providerKey.map(_ -> Known(Known.Uid(a.uid.value), a.license.map(l => Known.License(l.value)))))
        .toMap

    def save(a: ProviderAsset, firstTarget: (TargetType, TargetId)): (AssetUid, Boolean) =
      val t = now
      val license = Option(a.license.value).filter(_.nonEmpty).map(HeldAsset.License(_))
      val kind = a.kind.map(k => HeldAsset.Kind(k.value))
      held.find(x =>
        x.provider.exists(_.value == a.provider.value) && x.providerKey.exists(_.value == a.key.value)
      ) match
        case Some(x) =>
          change(x.uid)(
            _.modify(_.name)
              .setTo(AssetName(a.name.value))
              .modify(_.mime)
              .setTo(Mime(a.mime.value))
              .modify(_.bytes)
              .setTo(Bytes(a.bytes.value))
              .modify(_.sha256)
              .setTo(NewFigure.sha256(NewFigure.Bytes(a.bytes.value)))
              .modify(_.width)
              .setTo(a.width.map(w => HeldAsset.Width(w.value)))
              .modify(_.height)
              .setTo(a.height.map(h => HeldAsset.Height(h.value)))
              .modify(_.license)
              .setTo(license)
              .modify(_.sourceUrl)
              .setTo(Some(HeldAsset.SourceUrl(a.sourceUrl.value)))
              .modify(_.originalUrl)
              .setTo(a.originalUrl.map(u => HeldAsset.OriginalUrl(u.value)))
              .modify(_.author)
              .setTo(a.author.map(u => HeldAsset.Author(u.value)))
              .modify(_.credit)
              .setTo(Some(HeldAsset.Credit(a.credit.value)))
              .modify(_.licenseUrl)
              .setTo(a.licenseUrl.map(u => HeldAsset.LicenseUrl(u.value)))
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
              .using(_.orElse(kind))
          )
          (x.uid, false)
        case None =>
          val uid = AssetUid(newId().toString)
          val row = HeldAsset(
            uid,
            None,
            AssetName(a.name.value),
            Mime(a.mime.value),
            Bytes(a.bytes.value),
            NewFigure.sha256(NewFigure.Bytes(a.bytes.value)),
            a.width.map(w => HeldAsset.Width(w.value)),
            a.height.map(h => HeldAsset.Height(h.value)),
            kind = kind,
            license = license,
            licenseUrl = a.licenseUrl.map(u => HeldAsset.LicenseUrl(u.value)),
            author = a.author.map(u => HeldAsset.Author(u.value)),
            credit = Some(HeldAsset.Credit(a.credit.value)),
            provider = Some(Provider(a.provider.value)),
            providerKey = Some(ProviderKey(a.key.value)),
            sourceUrl = Some(HeldAsset.SourceUrl(a.sourceUrl.value)),
            originalUrl = a.originalUrl.map(u => HeldAsset.OriginalUrl(u.value)),
            retrievedAt = Some(t),
            thumb = a.thumb,
            thumbMime = a.thumbMime,
            thumbWidth = a.thumbWidth,
            thumbHeight = a.thumbHeight
          )
          val _ = state.updateAndGet(_.modify(_.assets).using(_ :+ row))
          (uid, true)

    def refreshRights(uid: AssetUid, r: Rights): Unit =
      val t = now
      change(uid)(
        _.modify(_.license)
          .setTo(Some(HeldAsset.License(r.license.value)))
          .modify(_.licenseUrl)
          .setTo(r.licenseUrl.map(u => HeldAsset.LicenseUrl(u.value)))
          .modify(_.author)
          .setTo(r.author.map(a => HeldAsset.Author(a.value)))
          .modify(_.credit)
          .setTo(Some(HeldAsset.Credit(r.credit.value)))
          .modify(_.retrievedAt)
          .setTo(Some(t))
      )

    def setThumb(
        uid: AssetUid,
        bytes: ProviderAsset.Thumb,
        mime: ProviderAsset.ThumbMime,
        width: Option[ProviderAsset.ThumbWidth],
        height: Option[ProviderAsset.ThumbHeight]
    ): Unit =
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
        uid: AssetUid,
        targetType: TargetType,
        targetId: TargetId,
        role: MediaRole.Wire,
        property: Option[LinkProperty],
        item: Option[LinkItem],
        rank: LinkRank,
        basis: Option[MediaMatch.Basis]
    ): Boolean =
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

    private def onSource(m: HeldMediaLink, target: PictureTarget): Boolean = m.targetType.value == target.wire.value

    def setSourcePicture(citekey: Citekey, uid: AssetUid, role: MediaRole.Wire, property: Option[LinkProperty]): Unit =
      def mine(m: HeldMediaLink) =
        onSource(m, PictureTarget.Source) && m.targetId.value == citekey.value && m.role == role
      val linked = property.map(p => LinkProperty(p.value))
      val _ = state.updateAndGet { s =>
        val others = s.mediaLinks.filterNot(m => mine(m) && m.assetUid != uid)
        val updated =
          if others.exists(m => mine(m) && m.assetUid == uid) then
            others.map(m => if mine(m) && m.assetUid == uid then m.modify(_.property).setTo(linked) else m)
          else
            others :+ HeldMediaLink(
              uid,
              TargetType(PictureTarget.Source.wire.value),
              TargetId(citekey.value),
              role,
              linked,
              None,
              LinkRank(0),
              None
            )
        s.modify(_.mediaLinks).setTo(updated)
      }

    def sourcePicture(citekey: Citekey, role: MediaRole.Wire): Option[(AssetUid, PictureKey)] =
      state.get.mediaLinks
        .find(m => onSource(m, PictureTarget.Source) && m.targetId.value == citekey.value && m.role == role)
        .flatMap(m => byUid(m.assetUid))
        .map(a => (a.uid, PictureKey(a.providerKey.map(_.value).getOrElse(a.name.value))))

    private val stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC)

    private def targetsOf(uid: AssetUid): List[MediaTarget] =
      state.get.mediaLinks
        .filter(_.assetUid == uid)
        .sortBy(m => (m.targetType.value: String, m.targetId.value: String, m.rank.value: Int))
        .toList
        .map(m =>
          MediaTarget(
            MediaTarget.TargetType(m.targetType.value),
            MediaTarget.TargetId(m.targetId.value),
            MediaTarget.Role(m.role.value),
            m.property.map(p => MediaTarget.Property(p.value)),
            m.item.map(i => MediaTarget.Item(i.value)),
            m.basis.map(b => MediaTarget.Basis(b.value))
          )
        )

    private def item(a: HeldAsset): MediaItem =
      MediaItem(
        MediaItem.Uid(a.uid.value),
        Bundle.Asset.FileName(a.name.value),
        Bundle.Asset.MimeText(a.mime.value),
        ByteCount(a.bytes.value.length.toLong),
        a.citekey.map(c => com.histmersion.api.library.Citekey(c.value)),
        a.provider.map(p => MediaItem.Provider(p.value)),
        a.providerKey.map(k => MediaItem.ProviderKey(k.value)),
        a.width.map(w => ImageSize.Width(w.value)),
        a.height.map(h => ImageSize.Height(h.value)),
        a.kind.map(k => MediaItem.Kind(k.value)),
        a.caption.map(c => MediaItem.Caption(c.value)),
        a.license.map(l => MediaItem.License(l.value)),
        a.licenseUrl.map(u => MediaItem.LicenseUrl(u.value)),
        a.author.map(x => MediaItem.Author(x.value)),
        a.credit.map(c => MediaItem.Credit(c.value)),
        a.sourceUrl.map(u => MediaItem.SourceUrl(u.value)),
        a.originalUrl.map(u => MediaItem.OriginalUrl(u.value)),
        a.retrievedAt.map(t => MediaItem.RetrievedAt(stamp.format(t))),
        Option.when(a.thumb.isDefined)(MediaItem.ThumbUrl(MediaThumb.thumbPath(Bundle.Asset.Uid(a.uid.value)).value)),
        MediaItem.FileUrl(MediaThumb.filePath(Bundle.Asset.Uid(a.uid.value)).value),
        targetsOf(a.uid)
      )

    private def ofProvider(a: HeldAsset, provider: Option[String]): Boolean =
      provider.forall(p => (p == MediaProvider.Source && a.provider.isEmpty) || a.provider.exists(_.value == p))

    private def underLicence(a: HeldAsset, license: Option[String]): Boolean =
      license.forall(l => (l == "none" && a.license.forall(_.value.trim.isEmpty)) || a.license.exists(_.value == l))

    private def shownOn(a: HeldAsset, target: PictureTarget, id: Option[String]): Boolean =
      id.forall(i =>
        state.get.mediaLinks.exists(m => m.assetUid == a.uid && onSource(m, target) && m.targetId.value == i)
      )

    def list(q: Api.MediaQuery, limit: Limit): MediaList =
      val (provider, license) = (clean(q.provider.map(_.value)), clean(q.license.map(_.value)))
      val text = clean(q.q.map(_.value)).map(_.toLowerCase)
      val offset = q.offset.map(_.value).getOrElse(0).max(0)
      def matches(a: HeldAsset) =
        text.forall(t =>
          List(Some(a.name.value), a.credit.map(_.value), a.providerKey.map(_.value)).flatten
            .exists(_.toLowerCase.contains(t))
        )
      val all = held
        .filter(a =>
          ofProvider(a, provider) && underLicence(a, license) && shownOn(
            a,
            PictureTarget.Place,
            clean(q.place.map(_.value))
          ) &&
            shownOn(a, PictureTarget.Event, clean(q.event.map(_.value))) && matches(a)
        )
        .sortBy(a =>
          (
            a.retrievedAt.isEmpty,
            a.retrievedAt.map(t => -(t.getEpochSecond * 1000000L + t.getNano / 1000)),
            a.citekey.isEmpty,
            a.citekey.map(_.value),
            a.name.value
          )
        )
      MediaList.of(
        com.histmersion.api.RowCount(all.size),
        all.slice(offset, offset + limit.value.max(1)).toList.map(item),
        UnreadPage.Offset(offset)
      )

    def show(uid: AssetUid): Option[MediaItem] = byUid(uid).map(item)

    def thumb(uid: AssetUid): Option[(Mime, Bytes)] =
      byUid(uid).flatMap { a =>
        a.thumb match
          case Some(t) => Some((Mime(a.thumbMime.map(_.value).getOrElse(a.mime.value)), Bytes(t.value)))
          case None
              if a.provider.isEmpty || a.provider
                .exists(p => p.value == MediaProvider.Calibre || p.value == MediaProvider.OpenLibrary) =>
            Some((a.mime, a.bytes))
          case None => None
      }

    def file(uid: AssetUid): Option[(Mime, Bytes)] = byUid(uid).map(a => (a.mime, a.bytes))

    def facets: MediaFacets =
      def counted(keys: Vector[String]): List[MediaCount] = MediaCount.of(keys.toList.map(MediaCount.Key(_)))
      MediaFacets(
        counted(held.map(_.provider.map(_.value).getOrElse(MediaProvider.Source))),
        counted(held.map(_.license.map(_.value.trim).filter(_.nonEmpty).getOrElse("none")))
      )

    private def hit(a: HeldAsset, p: (HeldAsset.Latitude, HeldAsset.Longitude)): MediaGeoHit =
      MediaGeoHit(
        a.citekey.map(c => com.histmersion.api.library.Citekey(c.value)),
        Bundle.Asset.FileName(a.name.value),
        a.kind.map(k => MediaItem.Kind(k.value)),
        Latitude(p._1.value),
        Longitude(p._2.value),
        a.period.flatMap(_._1).map(y => Year(y.value)),
        a.period.flatMap(_._2).map(y => Year(y.value)),
        a.caption.map(c => MediaItem.Caption(c.value)),
        MediaItem.Uid(a.uid.value),
        a.provider.map(x => MediaItem.Provider(x.value)),
        a.license.map(l => MediaItem.License(l.value)),
        a.credit.map(c => MediaItem.Credit(c.value)),
        Option.when(a.thumb.isDefined)(MediaItem.ThumbUrl(MediaThumb.thumbPath(Bundle.Asset.Uid(a.uid.value)).value)),
        fileUrl = Some(MediaItem.FileUrl(MediaThumb.filePath(Bundle.Asset.Uid(a.uid.value)).value)),
        sourceUrl = a.sourceUrl.map(u => MediaItem.SourceUrl(u.value)),
        licenseUrl = a.licenseUrl.map(u => MediaItem.LicenseUrl(u.value)),
        author = a.author.map(x => MediaItem.Author(x.value))
      )

    def geo(q: Api.MediaGeoQuery, limit: Limit): List[MediaGeoHit] =
      val k = q.kind.map(_.value.trim.toLowerCase).filter(_.nonEmpty)
      val (provider, license) = (clean(q.provider.map(_.value)), clean(q.license.map(_.value)))
      def inSpan(a: HeldAsset) = q.from.forall { from =>
        a.period.exists((s, e) => s.forall(x => q.to.forall(x.value < _.value)) && e.forall(from.value <= _.value))
      }
      held
        .filter(a =>
          a.point.isDefined && k.forall(kk => a.kind.exists(_.value == kk)) && inSpan(a) && ofProvider(a, provider) &&
            underLicence(a, license)
        )
        .sortBy(a => (a.period.flatMap(_._1).isEmpty, a.period.flatMap(_._1).map(y => y.value: Long)))
        .take(limit.value.max(1))
        .toList
        .map(a => hit(a, a.point.get))

    def geoOf(uids: List[AssetUid]): Map[AssetUid, MediaGeoHit] =
      held.filter(a => uids.contains(a.uid)).flatMap(a => a.point.map(p => a.uid -> hit(a, p))).toMap

    private def ord(role: MediaRole.Wire): Int =
      MediaRole.all.indexOf(role) match
        case -1 => Int.MaxValue
        case i  => i

    def thumbs(targetType: TargetType, targetId: TargetId, limit: Limit): List[MediaThumb] =
      val links = state.get.mediaLinks.filter(m => m.targetType == targetType && m.targetId == targetId)
      val best = links
        .flatMap(m => byUid(m.assetUid).map(a => (m, a)))
        .filter((_, a) => a.thumb.isDefined || targetType.value == PictureTarget.Source.wire.value)
        .groupBy(_._2.uid)
        .values
        .map(_.minBy((m, _) => (ord(m.role), m.rank.value: Int)))
        .toList
        .sortBy((m, a) => (ord(m.role), m.rank.value: Int, a.uid.value: String))
      best.take(limit.value.max(1)).map { (m, a) =>
        MediaThumb(
          MediaItem.Uid(a.uid.value),
          MediaThumb.Role(m.role.value),
          MediaThumb.Url(MediaThumb.thumbPath(Bundle.Asset.Uid(a.uid.value)).value),
          MediaItem.Credit(a.credit.map(_.value).getOrElse("")),
          a.license.map(l => MediaItem.License(l.value)),
          a.licenseUrl.map(u => MediaItem.LicenseUrl(u.value)),
          a.sourceUrl.map(u => MediaItem.SourceUrl(u.value)),
          a.thumbWidth.map(w => ImageSize.Width(w.value)).orElse(a.width.map(w => ImageSize.Width(w.value))),
          a.thumbHeight.map(h => ImageSize.Height(h.value)).orElse(a.height.map(h => ImageSize.Height(h.value)))
        )
      }

    def placeThumbs(placeId: Place.Id, sameSiteKm: EventMatcher.NearFinding.Km, limit: Limit): List[MediaThumb] = Nil
