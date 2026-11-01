package com.histmersion.domain.media.assets

import com.histmersion.api.*
import com.histmersion.domain.media.MediaState
import com.histmersion.util.database.{RangeSql, Transactor}
import com.histmersion.values.MediaRole

import hearth.kindlings.optics.syntax.*

import java.time.{Clock, Instant, ZoneOffset}
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

/** The pictures (`assets`) and where they are shown (`media_links`): a source's figures stored, read, described and
  * patched; a provider's pictures stored, re-read and given thumbnails; the places, events and sources they are shown
  * on; the media list, its facets, a picture, its file and thumbnail, the map feed and the cards' pictures.
  */
trait AssetService:

  // ── a source's figures ──

  def figures(citekey: String): List[AssetInfo]

  /** A figure's mime type and bytes. */
  def figure(citekey: String, name: String): Option[(String, Array[Byte])]

  def figureCount(citekey: String): Int

  /** Figures added, any stored under the same name left alone; how many were written (after deduplication). */
  def addFigures(citekey: String, figures: List[NewFigure]): Int

  /** The whole set replaced (a re-conversion): the rows of the pictures it still has kept, renamed when only their name
    * moved; how many were kept.
    */
  def replaceFigures(citekey: String, figures: List[NewFigure]): Int

  def detail(citekey: String, name: String): Option[AssetDetail]

  /** A figure's description changed; a period that would end before it starts is refused, the other fields kept. */
  def patch(citekey: String, name: String, p: AssetPatch): Either[ApiError, AssetDetail]

  /** A source's image figures, as the illustration's choice weighs them. */
  def sourceFigures(citekey: String): List[SourceFigure]

  // ── a provider's pictures and where they are shown ──

  def known(provider: String, keys: List[String]): Map[String, Known]

  /** A provider's picture stored, or its bytes and rights replaced; its uid and whether it is new. */
  def save(a: ProviderAsset, firstTarget: (String, String)): (String, Boolean)

  def refreshRights(uid: String, r: Rights): Unit

  def setThumb(uid: String, bytes: Array[Byte], mime: String, width: Option[Int], height: Option[Int]): Unit

  /** A picture shown on a place or event in a role; whether the link is new. */
  def link(
      uid: String,
      targetType: String,
      targetId: String,
      role: String,
      property: Option[String],
      item: Option[String],
      rank: Int,
      basis: Option[String]
  ): Boolean

  /** `uid` made the source's picture in `role`, replacing the one it had in that role. */
  def setSourcePicture(citekey: String, uid: String, role: String, property: Option[String]): Unit

  /** The source's picture in `role`: its uid and its provider key or figure name. */
  def sourcePicture(citekey: String, role: String): Option[(String, String)]

  // ── reads ──

  def list(q: Api.MediaQuery, limit: Int): MediaList
  def show(uid: String): Option[MediaItem]

  /** The thumbnail's type and bytes — a source's figure (or a cover) without one is its own thumbnail. */
  def thumb(uid: String): Option[(String, Array[Byte])]

  def file(uid: String): Option[(String, Array[Byte])]
  def facets: MediaFacets
  def geo(q: Api.MediaGeoQuery, limit: Int): List[MediaGeoHit]
  def geoOf(uids: List[String]): Map[String, MediaGeoHit]

  /** A place's, an event's or a source's pictures for its card, best first. */
  def thumbs(targetType: String, targetId: String, limit: Int): List[MediaThumb]

  /** A place's pictures for its card, through its equivalents (its old ids, its Wikidata items, a point of the same
    * name within `sameSiteKm`).
    */
  def placeThumbs(placeId: String, sameSiteKm: Double, limit: Int): List[MediaThumb]

object AssetService:
  def apply(db: Transactor): AssetService = Implementation(db)

  final private class Implementation(db: Transactor) extends AssetService:
    def figures(citekey: String): List[AssetInfo] = db.read(AssetRepo.list(citekey)).toList
    def figure(citekey: String, name: String): Option[(String, Array[Byte])] = db.read(AssetRepo.read(citekey, name))
    def figureCount(citekey: String): Int = db.read(AssetRepo.count(citekey))
    def addFigures(citekey: String, figures: List[NewFigure]): Int = db.transact(AssetRepo.add(citekey, figures))
    def replaceFigures(citekey: String, figures: List[NewFigure]): Int =
      db.transact(AssetRepo.replace(citekey, figures))
    def detail(citekey: String, name: String): Option[AssetDetail] = db.read(AssetRepo.detail(citekey, name))
    def patch(citekey: String, name: String, p: AssetPatch): Either[ApiError, AssetDetail] =
      db.transact(AssetRepo.patch(citekey, name, p))
        .flatMap(_ => detail(citekey, name).toRight(ApiError(s"No asset '$citekey/$name'", Some(Write.CodeNotFound))))
    def sourceFigures(citekey: String): List[SourceFigure] = db.read(MediaRepo.sourceFigures(citekey))
    def known(provider: String, keys: List[String]): Map[String, Known] = db.read(MediaRepo.known(provider, keys))
    def save(a: ProviderAsset, firstTarget: (String, String)): (String, Boolean) =
      db.transact(MediaRepo.save(a, firstTarget))
    def refreshRights(uid: String, r: Rights): Unit = db.transact(MediaRepo.refreshRights(uid, r))
    def setThumb(uid: String, bytes: Array[Byte], mime: String, width: Option[Int], height: Option[Int]): Unit =
      db.transact(MediaRepo.setThumb(uid, bytes, mime, width, height))
    def link(
        uid: String,
        targetType: String,
        targetId: String,
        role: String,
        property: Option[String],
        item: Option[String],
        rank: Int,
        basis: Option[String]
    ): Boolean = db.transact(MediaRepo.link(uid, targetType, targetId, role, property, item, rank, basis))
    def setSourcePicture(citekey: String, uid: String, role: String, property: Option[String]): Unit =
      db.transact(MediaRepo.setSourcePicture(citekey, uid, role, property))
    def sourcePicture(citekey: String, role: String): Option[(String, String)] =
      db.read(MediaRepo.sourcePicture(citekey, role))
    def list(q: Api.MediaQuery, limit: Int): MediaList = db.read(MediaRepo.list(q, limit))
    def show(uid: String): Option[MediaItem] = db.read(MediaRepo.show(uid))
    def thumb(uid: String): Option[(String, Array[Byte])] = db.read(MediaRepo.thumb(uid))
    def file(uid: String): Option[(String, Array[Byte])] = db.read(MediaRepo.file(uid))
    def facets: MediaFacets = db.read(MediaRepo.facets)
    def geo(q: Api.MediaGeoQuery, limit: Int): List[MediaGeoHit] = db.read(MediaRepo.geo(q, limit))
    def geoOf(uids: List[String]): Map[String, MediaGeoHit] = db.read(MediaRepo.geoOf(uids))
    def thumbs(targetType: String, targetId: String, limit: Int): List[MediaThumb] =
      db.read(MediaRepo.thumbsIn(targetType, targetId, limit))
    def placeThumbs(placeId: String, sameSiteKm: Double, limit: Int): List[MediaThumb] =
      db.read(MediaRepo.placeThumbsIn(placeId, sameSiteKm, limit))

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
    private def ofSource(citekey: String): Vector[HeldAsset] = held.filter(_.citekey.contains(citekey))
    private def named(citekey: String, name: String): Option[HeldAsset] = ofSource(citekey).find(_.name == name)
    private def byUid(uid: String): Option[HeldAsset] = held.find(_.uid == uid)
    private def clean(o: Option[String]): Option[String] = o.map(_.trim).filter(_.nonEmpty)

    private def change(uid: String)(f: HeldAsset => HeldAsset): Unit =
      val _ = state.updateAndGet(_.modify(_.assets).using(_.map(a => if a.uid == uid then f(a) else a)))

    def figures(citekey: String): List[AssetInfo] =
      ofSource(citekey)
        .sortBy(_.name)
        .toList
        .map(a => AssetInfo(a.name, a.mime, a.bytes.length, a.sha256, a.width, a.height))

    def figure(citekey: String, name: String): Option[(String, Array[Byte])] =
      named(citekey, name).map(a => (a.mime, a.bytes))

    def figureCount(citekey: String): Int = ofSource(citekey).size

    private def newFigure(citekey: String, f: NewFigure): HeldAsset =
      HeldAsset(newId().toString, Some(citekey), f.name, f.mime, f.bytes, f.sha256, f.width, f.height)

    def addFigures(citekey: String, figures: List[NewFigure]): Int =
      val incoming = NewFigure.dedupe(figures)
      val _ = state.updateAndGet { s =>
        val taken = s.assets.filter(_.citekey.contains(citekey)).map(_.name).toSet
        s.modify(_.assets).using(_ ++ incoming.filterNot(f => taken(f.name)).map(newFigure(citekey, _)))
      }
      incoming.size

    def replaceFigures(citekey: String, figures: List[NewFigure]): Int =
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

    def detail(citekey: String, name: String): Option[AssetDetail] =
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

    def patch(citekey: String, name: String, p: AssetPatch): Either[ApiError, AssetDetail] =
      named(citekey, name) match
        case None    => Left(ApiError(s"No asset '$citekey/$name'", Some(Write.CodeNotFound)))
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
              detail(citekey, name).toRight(ApiError(s"No asset '$citekey/$name'", Some(Write.CodeNotFound)))
            )

    def sourceFigures(citekey: String): List[SourceFigure] =
      ofSource(citekey)
        .filter(_.mime.startsWith("image/"))
        .sortBy(_.name)
        .toList
        .map(a => SourceFigure(a.uid, a.name, a.width, a.height, a.thumb.isDefined))

    def known(provider: String, keys: List[String]): Map[String, Known] =
      held
        .filter(a => a.provider.contains(provider) && a.providerKey.exists(keys.contains))
        .flatMap(a => a.providerKey.map(_ -> Known(a.uid, a.license)))
        .toMap

    def save(a: ProviderAsset, firstTarget: (String, String)): (String, Boolean) =
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

    def refreshRights(uid: String, r: Rights): Unit =
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

    def setThumb(uid: String, bytes: Array[Byte], mime: String, width: Option[Int], height: Option[Int]): Unit =
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
        uid: String,
        targetType: String,
        targetId: String,
        role: String,
        property: Option[String],
        item: Option[String],
        rank: Int,
        basis: Option[String]
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

    def setSourcePicture(citekey: String, uid: String, role: String, property: Option[String]): Unit =
      def mine(m: HeldMediaLink) = m.targetType == PictureTarget.Source.wire && m.targetId == citekey && m.role == role
      val _ = state.updateAndGet { s =>
        val others = s.mediaLinks.filterNot(m => mine(m) && m.assetUid != uid)
        val updated =
          if others.exists(m => mine(m) && m.assetUid == uid) then
            others.map(m => if mine(m) && m.assetUid == uid then m.modify(_.property).setTo(property) else m)
          else others :+ HeldMediaLink(uid, PictureTarget.Source.wire, citekey, role, property, None, 0, None)
        s.modify(_.mediaLinks).setTo(updated)
      }

    def sourcePicture(citekey: String, role: String): Option[(String, String)] =
      state.get.mediaLinks
        .find(m => m.targetType == PictureTarget.Source.wire && m.targetId == citekey && m.role == role)
        .flatMap(m => byUid(m.assetUid))
        .map(a => (a.uid, a.providerKey.getOrElse(a.name)))

    private val stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC)

    private def targetsOf(uid: String): List[MediaTarget] =
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

    private def ofProvider(a: HeldAsset, provider: Option[String]): Boolean =
      provider.forall(p => (p == MediaProvider.Source && a.provider.isEmpty) || a.provider.contains(p))

    private def underLicence(a: HeldAsset, license: Option[String]): Boolean =
      license.forall(l => (l == "none" && a.license.forall(_.trim.isEmpty)) || a.license.contains(l))

    private def shownOn(a: HeldAsset, targetType: String, id: Option[String]): Boolean =
      id.forall(i =>
        state.get.mediaLinks.exists(m => m.assetUid == a.uid && m.targetType == targetType && m.targetId == i)
      )

    def list(q: Api.MediaQuery, limit: Int): MediaList =
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

    def show(uid: String): Option[MediaItem] = byUid(uid).map(item)

    def thumb(uid: String): Option[(String, Array[Byte])] =
      byUid(uid).flatMap { a =>
        a.thumb match
          case Some(t) => Some((a.thumbMime.getOrElse(a.mime), t))
          case None
              if a.provider.isEmpty || a.provider
                .exists(p => p == MediaProvider.Calibre || p == MediaProvider.OpenLibrary) =>
            Some((a.mime, a.bytes))
          case None => None
      }

    def file(uid: String): Option[(String, Array[Byte])] = byUid(uid).map(a => (a.mime, a.bytes))

    def facets: MediaFacets =
      def counted(keys: Vector[String]): List[MediaCount] =
        keys.groupBy(identity).toList.map((k, v) => MediaCount(k, v.size)).sortBy(c => (-c.n, c.key))
      MediaFacets(
        counted(held.map(_.provider.getOrElse(MediaProvider.Source))),
        counted(held.map(_.license.map(_.trim).filter(_.nonEmpty).getOrElse("none")))
      )

    private def hit(a: HeldAsset, p: (Double, Double)): MediaGeoHit =
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

    def geo(q: Api.MediaGeoQuery, limit: Int): List[MediaGeoHit] =
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

    def geoOf(uids: List[String]): Map[String, MediaGeoHit] =
      held.filter(a => uids.contains(a.uid)).flatMap(a => a.point.map(p => a.uid -> hit(a, p))).toMap

    private def ord(role: String): Int =
      MediaRole.all.indexOf(role) match
        case -1 => Int.MaxValue
        case i  => i

    def thumbs(targetType: String, targetId: String, limit: Int): List[MediaThumb] =
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

    def placeThumbs(placeId: String, sameSiteKm: Double, limit: Int): List[MediaThumb] = Nil
