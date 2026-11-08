package com.histmersion
package domain.media.imports

import hearth.kindlings.optics.syntax.*

import com.histmersion.pluginhost.{HttpTransport, PluginMedia, Polite}

import com.histmersion.config.*
import com.histmersion.domain.{DeepTime, Licence}
import com.histmersion.domain.media.MediaRole
import com.histmersion.domain.media.assets.{AssetService, ProviderAsset, Rights}
import com.histmersion.util.ocr.ConverterClient
import com.histmersion.upstreams.media.MediaText
import com.histmersion.api.media.{ImageSize, MediaCount, MediaImportReport, MediaItem, MediaProvider, MediaSample, MediaTarget}
import com.histmersion.domain.catalogue.Place
import com.histmersion.domain.catalogue.places.ExternalIds
import com.histmersion.api.library.RepairAcquire
import com.histmersion.plugin.WorkKey
import com.histmersion.util.ocr.Pixels
import io.github.iltotore.iron.constraint.any.Pure

/** The import of one collection (#1446): for each place, a search by its name; each record judged — its own licence
  * statement first (never assumed), the collection's allowlist, then the match ([[SearchedCollection.matches]]) — and
  * at most `per-place` kept, in the collection's relevance order. A dry run stops there and lists the matches with
  * their reasons for a person to judge. Otherwise each new picture is downloaded (the collection's own web rendition,
  * at most `media.max-bytes`), its thumbnail taken from the collection when it offers one, else made here
  * ([[ConverterClient.thumbnail]]), and stored with the licence, credit line, page and original URL; the link keeps the
  * match's reason.
  */
final class CollectionImport(col: SearchedCollection, fetch: Polite, cfg: MediaConfig, converter: ConverterClient):

  final private case class Judged(target: Target, rec: MediaRecord, decision: Either[MediaMatch.Refusal, MediaMatch.Basis])

  /** The role a record's picture plays: its own, else the collection's. */
  private def roleOf(rec: MediaRecord): MediaRole.Wire = rec.role.map(r => MediaRole.Wire(r.value)).getOrElse(col.role)

  private def providerTitle: String = MediaProvider.title(MediaProvider.Id(col.provider.value)).value

  private def placeId(t: Target): Place.Id = Place.Id.assume(t.id.value)

  def run(targets: List[Target], dryRun: CollectionImport.DryRun, refresh: CollectionImport.Refresh, store: MediaStore): MediaImportReport =
    val failed = List.newBuilder[String]
    val allow = LicencePolicy.of(Licence.AllowList(col.conf.licences.value))
    val places = targets.filter(_.targetType == ImportRepo.Place)
    val facts = store.placeFacts(places.map(placeId))

    // 1. the searches — a place whose search failed is not recorded as asked; a refusal (403: a bot
    // challenge, a key wanted) ends the batch instead of being asked again for every place
    // `var`: set by the first refusal
    var refused = false
    val answered = places.flatMap { t =>
      facts.get(placeId(t)) match
        case None               => failed += s"place ${t.id}: not found"; None
        case Some(_) if refused => None
        case Some(f)            =>
          col.search(fetch, f) match
            case Left(e) =>
              failed += s"$providerTitle search for ${t.id} (${t.label}): $e"
              if e.value.contains("HTTP 401") || e.value.contains("HTTP 403") then
                refused = true
                failed += s"$providerTitle refused the request; the rest of this batch was not asked"
              None
            case Right(rs) =>
              val errors = rs.collect { case Left(e) => e }
              errors.foreach(e => failed += s"${t.id}: $e")
              // a refusal of one of its records ends the batch too (#2561): the rest would be refused alike
              if errors.exists(e => e.value.contains("HTTP 401") || e.value.contains("HTTP 403")) then
                refused = true
                failed += s"$providerTitle refused the request; the rest of this batch was not asked"
                None
              else Some((t, f, rs.collect { case Right(r) => r }))
    }

    // 2. what the records' places name on the map
    val keys = answered.flatMap(_._3).flatMap(_.geography.map(g => MediaMatch.keyOf(g._2))).filter(_.value.nonEmpty).distinct
    val named = store.mapPlacesNamed(keys.map(k => ImportService.PlaceKey(k.value))).map((k, v) => MediaMatch.PlaceInfo.Keys(k.value) -> v)

    // 3. judge each record for its place (by the ids the place carries too, when the collection names them)
    val (own, byId) =
      if !col.usesIds then
        (
          Map.empty[Place.Id, Set[(ExternalIds.Scheme, ExternalIds.ExternalId)]],
          Map.empty[(ExternalIds.Scheme, ExternalIds.ExternalId), List[PlaceFacts]]
        )
      else
        (
          store.placeExternalIds(answered.map(a => placeId(a._1))),
          store.mapPlacesWithIds(answered.flatMap(_._3).flatMap(_.ids).distinct)
        )
    val judged = answered.flatMap { (t, f, recs) =>
      val info = MediaMatch.PlaceInfo(
        MediaMatch.PlaceInfo.Id(f.id.value),
        MediaMatch.PlaceInfo.Label(f.label.value),
        f.keys.map(k => MediaMatch.PlaceInfo.Keys(k.value)),
        f.period.flatMap((a, b) => DeepTime.span(DeepTime.SignedYear(a.value), DeepTime.SignedYear(b.value)))
      )
      val resolve = SearchedCollection.resolveFor(f, named, cfg.sameSiteKm)
      val resolveId = SearchedCollection.resolveIdFor(f, own.getOrElse(placeId(t), Set.empty), byId, cfg.sameSiteKm)
      recs.distinctBy(_.key).map { rec =>
        val d = PluginMedia
          .refusal(rec, allow, PluginMedia.Provider(col.provider.value))
          .map(r => MediaMatch.Refusal(r.value))
          .toLeft(())
          .flatMap(_ => col.matchesWithIds(rec, info, resolve, resolveId))
        Judged(t, rec, d)
      }
    }
    val kept = judged
      .groupBy(_.target.id)
      .values
      .toList
      .flatMap(_.filter(_.decision.isRight).take(col.conf.perPlace.value.max(0)))
      .sortBy(j => (places.indexOf(j.target), judged.indexOf(j)))
    val records = judged.map(_.rec).distinctBy(_.key)
    val samples = kept.map { j =>
      MediaSample(
        MediaTarget.TargetType(j.target.targetType.value),
        MediaTarget.TargetId(j.target.id.value),
        MediaSample.TargetLabel(j.target.label.value),
        MediaSample.Key(j.rec.key.value),
        MediaSample.Title(j.rec.title.value),
        MediaItem.License(SearchedCollection.licenceKey(j.rec).value),
        MediaSample.Basis(j.decision.toOption.fold("")(_.value)),
        MediaSample.Url(j.rec.pageUrl.value)
      )
    }
    val base = MediaImportReport(
      provider = MediaItem.Provider(col.provider.value),
      dryRun = RepairAcquire.RepairRequest.DryRun(dryRun.value),
      targets = MediaImportReport.Targets(places.size),
      items = MediaImportReport.Items(records.size),
      statements = MediaImportReport.Statements(kept.size),
      files = MediaImportReport.Files(kept.map(_.rec.key).distinct.size),
      byLicence = records
        .groupBy(SearchedCollection.licenceKey)
        .toList
        .map((k, rs) => MediaCount(MediaCount.Key(k.value), MediaCount.N(rs.size), MediaCount.Allowed(rs.head.rights.exists(r => allow.allows(r.licence)))))
        .sortBy(c => (-c.n.value, c.key.value)),
      byRole = MediaCount.of(kept.map(j => MediaCount.Key(roleOf(j.rec).value))),
      rejected = MediaCount.of(judged.flatMap(_.decision.left.toOption).map(r => MediaCount.Key(r.value))),
      samples = samples.take(cfg.samples.value.max(0))
    )

    if dryRun.value then base.modify(_.failed).setTo(failed.result().map(MediaImportReport.Failed(_)))
    else persist(base, kept, judged, answered.map(_._1), refresh, store, failed)

  private def persist(
      base: MediaImportReport,
      kept: List[Judged],
      judged: List[Judged],
      asked: List[Target],
      refresh: CollectionImport.Refresh,
      db: MediaStore,
      failed: scala.collection.mutable.Builder[String, List[String]]
  ): MediaImportReport =
    val allow = LicencePolicy.of(Licence.AllowList(col.conf.licences.value))
    val known = db.known(col.provider, judged.map(j => AssetService.ProviderKey(j.rec.key.value)).distinct)
    def knownOf(rec: MediaRecord) = known.get(AssetService.ProviderKey(rec.key.value))
    def each[A](what: => String)(write: => A): Option[A] = MediaStep.each(failed)(what)(write)

    // a stored picture whose record no longer allows it is KEPT, and listed for review
    val revoked = judged.map(_.rec).distinctBy(_.key).flatMap { rec =>
      knownOf(rec).flatMap { k =>
        rec.rights match
          case _ if rec.withheld.isDefined => Some(s"${col.provider} ${rec.key} (media:${k.uid}): ${rec.withheld.get}")
          case Left(why)                   => Some(s"${col.provider} ${rec.key} (media:${k.uid}): $why")
          case Right(r) if !allow.allows(r.licence) =>
            Some(s"${col.provider} ${rec.key} (media:${k.uid}): licence ${r.licence.family.wire} not allowed")
          case _ => None
      }
    }

    // `var`s: the counts of what this run wrote
    var stored, updated = 0
    val uids = scala.collection.mutable.Map.empty[String, AssetService.AssetUid]
    def rightsOf(rec: MediaRecord) =
      val r = rec.rights.toOption.get // kept ⇒ readable rights
      Rights(Rights.License(r.licence.id.value), r.licenceUrl.map(u => Rights.LicenseUrl(u.value)), r.author.map(a => Rights.Author(a.value)), Rights.Credit(r.credit.value))
    kept.distinctBy(_.rec.key).foreach { j =>
      knownOf(j.rec) match
        case Some(k) =>
          if refresh.value then
            each(s"${j.rec.key} (licence re-read)")(db.refreshRights(AssetService.AssetUid(k.uid.value), rightsOf(j.rec)))
            updated += 1
          uids(j.rec.key.value) = AssetService.AssetUid(k.uid.value)
        case None =>
          // the record completed by what the search does not answer; its licence must still be allowed
          val completed = col
            .complete(fetch, j.rec)
            .filterOrElse(
              _.rights.exists(r => allow.allows(r.licence)),
              SearchedCollection.Problem("licence not allowed on the full record")
            )
          completed.left.map(_.value).flatMap(rec => download(rec).map(d => (rec, d))) match
            case Left(why)                          => failed += s"${col.provider} ${j.rec.key}: $why"
            case Right((rec, (bytes, mime, thumb))) =>
              val rights = rightsOf(rec)
              val size = ImageSize.of(ImageSize.Bytes(bytes.value))
              val a = ProviderAsset(
                ProviderAsset.Provider(col.provider.value),
                ProviderAsset.Key(rec.key.value),
                ProviderAsset.Name(MediaText.safeName(WorkKey.Title(s"${col.provider}-${rec.key}-${rec.title}")).value),
                mime,
                bytes,
                size.map(s => ProviderAsset.Width(s._1.value)),
                size.map(s => ProviderAsset.Height(s._2.value)),
                thumb.map(_._1),
                thumb.map(_._2),
                thumb.flatMap(_._3),
                thumb.flatMap(_._4),
                ProviderAsset.License(rights.license.value),
                rights.licenseUrl.map(u => ProviderAsset.LicenseUrl(u.value)),
                rights.author.map(a => ProviderAsset.Author(a.value)),
                ProviderAsset.Credit(rights.credit.value),
                ProviderAsset.SourceUrl(rec.pageUrl.value),
                rec.originalUrl.map(u => ProviderAsset.OriginalUrl(u.value)),
                Some(ProviderAsset.kindOf(roleOf(rec)))
              )
              each(s"${rec.key} (store)")(db.save(a, (j.target.targetType, j.target.id))).foreach { (uid, inserted) =>
                if inserted then stored += 1 else updated += 1
                uids(rec.key.value) = uid
              }
    }

    val ranks = kept.groupBy(_.target.id).view.mapValues(_.map(_.rec.key)).toMap
    val linked = kept.count(j =>
      uids
        .get(j.rec.key.value)
        .exists(uid =>
          each(s"${j.rec.key} → ${j.target.targetType} ${j.target.id} (link)")(
            db.linkBasis(
              uid,
              j.target.targetType,
              j.target.id,
              roleOf(j.rec),
              None,
              None,
              AssetService.LinkRank(ranks.getOrElse(j.target.id, Nil).indexOf(j.rec.key).max(0)),
              j.decision.toOption
            )
          ).getOrElse(false)
        )
    )
    val found = kept.filter(j => uids.contains(j.rec.key.value)).groupBy(_.target.id).view.mapValues(_.size).toMap
    asked.foreach(t =>
      each(s"${t.targetType} ${t.id} (check)")(
        db.checked(col.provider, t.targetType, t.id, ImportService.FoundCount(found.getOrElse(t.id, 0)), None)
      )
    )
    base.modify(_.stored).setTo(MediaImportReport.Stored(stored)).modify(_.updated).setTo(MediaImportReport.Updated(updated)).modify(_.linked).setTo(MediaImportReport.Linked(linked)).modify(_.revoked).setTo(revoked.sorted.map(MediaImportReport.Revoked(_))).modify(_.failed).setTo(failed.result().map(MediaImportReport.Failed(_)))

  /** The record's rendition (at most `max-bytes`) and its thumbnail — the collection's own small rendition, else one
    * made here; without either the card shows no picture for it.
    */
  private def download(
      rec: MediaRecord
  ): Either[String, (ProviderAsset.Bytes, ProviderAsset.Mime, Option[(ProviderAsset.Thumb, ProviderAsset.ThumbMime, Option[ProviderAsset.ThumbWidth], Option[ProviderAsset.ThumbHeight])])] =
    rec.imageUrl.toRight("no image").flatMap(u => fetch.file(HttpTransport.Url(u.value), Polite.Mime("image/jpeg")).left.map(_.value)).flatMap { (bytes, mime) =>
      if bytes.value.length.toLong > cfg.maxBytes.value then
        Left(s"rendition of ${bytes.value.length} bytes is larger than media.max-bytes")
      else if !mime.value.startsWith("image/") then Left(s"not an image ($mime)")
      else
        val own = rec.thumbUrl.flatMap { u =>
          fetch.file(HttpTransport.Url(u.value), Polite.Mime("image/jpeg")) match
            case Right((tb, tm)) if tm.value.startsWith("image/") =>
              val s = ImageSize.of(ImageSize.Bytes(tb.value))
              Some((ProviderAsset.Thumb(tb.value), ProviderAsset.ThumbMime(tm.value), s.map(x => ProviderAsset.ThumbWidth(x._1.value)), s.map(x => ProviderAsset.ThumbHeight(x._2.value))))
            case Right(_)  => None
            case Left(why) => scribe.warn(s"media: thumbnail of ${col.provider} ${rec.key} not fetched: $why"); None
        }
        val made = own.orElse(
          converter.thumbnail(
            ConverterClient.DocumentBytes(bytes.value),
            ConverterClient.Filename(MediaText.safeName(WorkKey.Title(rec.key.value)).value + ".jpg"),
            Pixels(cfg.thumbPx.value)
          ) match
            case Right(t) =>
              Some((ProviderAsset.Thumb(t.bytes.value), ProviderAsset.ThumbMime(t.mime.value), Some(t.width.value).filter(_ > 0).map(ProviderAsset.ThumbWidth(_)), Some(t.height.value).filter(_ > 0).map(ProviderAsset.ThumbHeight(_))))
            case Left(why) => scribe.info(s"media: no thumbnail for ${col.provider} ${rec.key} ($why)"); None
        )
        Right((ProviderAsset.Bytes(bytes.value), ProviderAsset.Mime(mime.value), made))
    }

object CollectionImport:
  type DryRun = DryRun.T
  object DryRun extends Newtype[Boolean, Pure]
  type Refresh = Refresh.T
  object Refresh extends Newtype[Boolean, Pure]
