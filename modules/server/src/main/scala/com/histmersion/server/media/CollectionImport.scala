package com.histmersion.server.media

import com.histmersion.pluginhost.{allowed, PluginMedia, Polite}

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.db.MediaRepo
import com.histmersion.db.MediaRepo.{PlaceFacts, ProviderAsset, Rights, Target}
import com.histmersion.server.ConverterClient

/** The import of one collection (#1446): for each place, a search by its name; each record judged — its own licence
  * statement first (never assumed), the collection's allowlist, then the match ([[Collection.matches]]) — and at most
  * `per-place` kept, in the collection's relevance order. A dry run stops there and lists the matches with their
  * reasons for a person to judge. Otherwise each new picture is downloaded (the collection's own web rendition, at most
  * `media.max-bytes`), its thumbnail taken from the collection when it offers one, else made here
  * ([[ConverterClient.thumbnail]]), and stored with the licence, credit line, page and original URL; the link keeps the
  * match's reason.
  */
final class CollectionImport(col: Collection, fetch: Polite, cfg: MediaConfig, converter: ConverterClient):

  final private case class Judged(target: Target, rec: MediaRecord, decision: Either[String, String])

  def run(targets: List[Target], dryRun: Boolean, refresh: Boolean, store: MediaStore): MediaImportReport =
    val failed = List.newBuilder[String]
    val allow = col.conf.allowed
    val places = targets.filter(_.targetType == MediaRepo.Place)
    val facts = store.placeFacts(places.map(_.id))

    // 1. the searches — a place whose search failed is not recorded as asked; a refusal (403: a bot
    // challenge, a key wanted) ends the batch instead of being asked again for every place
    // `var`: set by the first refusal
    var refused = false
    val answered = places.flatMap { t =>
      facts.get(t.id) match
        case None               => failed += s"place ${t.id}: not found"; None
        case Some(_) if refused => None
        case Some(f)            =>
          col.search(fetch, f) match
            case Left(e) =>
              failed += s"${MediaProvider.title(col.provider)} search for ${t.id} (${t.label}): $e"
              if e.contains("HTTP 401") || e.contains("HTTP 403") then
                refused = true
                failed += s"${MediaProvider.title(col.provider)} refused the request; the rest of this batch was not asked"
              None
            case Right(rs) =>
              val errors = rs.collect { case Left(e) => e }
              errors.foreach(e => failed += s"${t.id}: $e")
              // a refusal of one of its records ends the batch too (#2561): the rest would be refused alike
              if errors.exists(e => e.contains("HTTP 401") || e.contains("HTTP 403")) then
                refused = true
                failed += s"${MediaProvider.title(col.provider)} refused the request; the rest of this batch was not asked"
                None
              else Some((t, f, rs.collect { case Right(r) => r }))
    }

    // 2. what the records' places name on the map
    val keys = answered.flatMap(_._3).flatMap(_.geography.map(g => MediaMatch.keyOf(g._2))).filter(_.nonEmpty).distinct
    val named = store.mapPlacesNamed(keys)

    // 3. judge each record for its place (by the ids the place carries too, when the collection names them)
    val (own, byId) =
      if !col.usesIds then (Map.empty[String, Set[(String, String)]], Map.empty[(String, String), List[PlaceFacts]])
      else
        (
          store.placeExternalIds(answered.map(_._1.id)),
          store.mapPlacesWithIds(answered.flatMap(_._3).flatMap(_.ids).distinct)
        )
    val judged = answered.flatMap { (t, f, recs) =>
      val info = MediaMatch.PlaceInfo(f.id, f.label, f.keys, f.period.flatMap((a, b) => DeepTime.span(a, b)))
      val resolve = Collection.resolveFor(f, named, cfg.sameSiteKm)
      val resolveId = Collection.resolveIdFor(f, own.getOrElse(t.id, Set.empty), byId, cfg.sameSiteKm)
      recs.distinctBy(_.key).map { rec =>
        val d = PluginMedia
          .refusal(rec, allow, col.provider)
          .toLeft(())
          .flatMap(_ => col.matchesWithIds(rec, info, resolve, resolveId))
        Judged(t, rec, d)
      }
    }
    val kept = judged
      .groupBy(_.target.id)
      .values
      .toList
      .flatMap(_.filter(_.decision.isRight).take(col.conf.perPlace.max(0)))
      .sortBy(j => (places.indexOf(j.target), judged.indexOf(j)))
    val records = judged.map(_.rec).distinctBy(_.key)
    val samples = kept.map { j =>
      MediaSample(
        j.target.targetType,
        j.target.id,
        j.target.label,
        j.rec.key,
        j.rec.title,
        Collection.licenceKey(j.rec),
        j.decision.getOrElse(""),
        j.rec.pageUrl
      )
    }
    val base = MediaImportReport(
      provider = col.provider,
      dryRun = dryRun,
      targets = places.size,
      items = records.size,
      statements = kept.size,
      files = kept.map(_.rec.key).distinct.size,
      byLicence = records
        .groupBy(Collection.licenceKey)
        .toList
        .map((k, rs) => MediaCount(k, rs.size, rs.head.rights.exists(r => MediaLicence.allowed(r.licence, allow))))
        .sortBy(c => (-c.n, c.key)),
      byRole = MediaCount.of(kept.map(_ => col.role)),
      rejected = MediaCount.of(judged.flatMap(_.decision.left.toOption)),
      samples = samples.take(cfg.samples.max(0))
    )

    if dryRun then base.copy(failed = failed.result())
    else persist(base, kept, judged, answered.map(_._1), refresh, store, failed)

  private def persist(
      base: MediaImportReport,
      kept: List[Judged],
      judged: List[Judged],
      asked: List[Target],
      refresh: Boolean,
      db: MediaStore,
      failed: scala.collection.mutable.Builder[String, List[String]]
  ): MediaImportReport =
    val allow = col.conf.allowed
    val known = db.known(col.provider, judged.map(_.rec.key).distinct)
    def each[A](what: => String)(write: => A): Option[A] = MediaStep.each(failed)(what)(write)

    // a stored picture whose record no longer allows it is KEPT, and listed for review
    val revoked = judged.map(_.rec).distinctBy(_.key).flatMap { rec =>
      known.get(rec.key).flatMap { k =>
        rec.rights match
          case _ if rec.withheld.isDefined => Some(s"${col.provider} ${rec.key} (media:${k.uid}): ${rec.withheld.get}")
          case Left(why)                   => Some(s"${col.provider} ${rec.key} (media:${k.uid}): $why")
          case Right(r) if !MediaLicence.allowed(r.licence, allow) =>
            Some(s"${col.provider} ${rec.key} (media:${k.uid}): licence ${r.licence.family} not allowed")
          case _ => None
      }
    }

    // `var`s: the counts of what this run wrote
    var stored, updated = 0
    val uids = scala.collection.mutable.Map.empty[String, String]
    def rightsOf(rec: MediaRecord) =
      val r = rec.rights.toOption.get // kept ⇒ readable rights
      Rights(r.licence.id, r.licenceUrl, r.author, r.credit)
    kept.distinctBy(_.rec.key).foreach { j =>
      known.get(j.rec.key) match
        case Some(k) =>
          if refresh then
            each(s"${j.rec.key} (licence re-read)")(db.refreshRights(k.uid, rightsOf(j.rec)))
            updated += 1
          uids(j.rec.key) = k.uid
        case None =>
          // the record completed by what the search does not answer; its licence must still be allowed
          val completed = col
            .complete(fetch, j.rec)
            .filterOrElse(
              _.rights.exists(r => MediaLicence.allowed(r.licence, allow)),
              "licence not allowed on the full record"
            )
          completed.flatMap(rec => download(rec).map(d => (rec, d))) match
            case Left(why)                          => failed += s"${col.provider} ${j.rec.key}: $why"
            case Right((rec, (bytes, mime, thumb))) =>
              val rights = rightsOf(rec)
              val size = ImageSize.of(bytes)
              val a = ProviderAsset(
                col.provider,
                rec.key,
                MediaText.safeName(s"${col.provider}-${rec.key}-${rec.title}"),
                mime,
                bytes,
                size.map(_._1),
                size.map(_._2),
                thumb.map(_._1),
                thumb.map(_._2),
                thumb.flatMap(_._3),
                thumb.flatMap(_._4),
                rights.license,
                rights.licenseUrl,
                rights.author,
                rights.credit,
                rec.pageUrl,
                rec.originalUrl,
                Some(MediaRepo.kindOf(col.role))
              )
              each(s"${rec.key} (store)")(db.save(a, (j.target.targetType, j.target.id))).foreach { (uid, inserted) =>
                if inserted then stored += 1 else updated += 1
                uids(rec.key) = uid
              }
    }

    val ranks = kept.groupBy(_.target.id).view.mapValues(_.map(_.rec.key)).toMap
    val linked = kept.count(j =>
      uids
        .get(j.rec.key)
        .exists(uid =>
          each(s"${j.rec.key} → ${j.target.targetType} ${j.target.id} (link)")(
            db.linkBasis(
              uid,
              j.target.targetType,
              j.target.id,
              col.role,
              None,
              None,
              ranks.getOrElse(j.target.id, Nil).indexOf(j.rec.key).max(0),
              j.decision.toOption
            )
          ).getOrElse(false)
        )
    )
    val found = kept.filter(j => uids.contains(j.rec.key)).groupBy(_.target.id).view.mapValues(_.size).toMap
    asked.foreach(t =>
      each(s"${t.targetType} ${t.id} (check)")(
        db.checked(col.provider, t.targetType, t.id, found.getOrElse(t.id, 0), None)
      )
    )
    base.copy(stored = stored, updated = updated, linked = linked, revoked = revoked.sorted, failed = failed.result())

  /** The record's rendition (at most `max-bytes`) and its thumbnail — the collection's own small rendition, else one
    * made here; without either the card shows no picture for it.
    */
  private def download(
      rec: MediaRecord
  ): Either[String, (Array[Byte], String, Option[(Array[Byte], String, Option[Int], Option[Int])])] =
    rec.imageUrl.toRight("no image").flatMap(u => fetch.file(u, "image/jpeg")).flatMap { (bytes, mime) =>
      if bytes.length.toLong > cfg.maxBytes then
        Left(s"rendition of ${bytes.length} bytes is larger than media.max-bytes")
      else if !mime.startsWith("image/") then Left(s"not an image ($mime)")
      else
        val own = rec.thumbUrl.flatMap { u =>
          fetch.file(u, "image/jpeg") match
            case Right((tb, tm)) if tm.startsWith("image/") =>
              val s = ImageSize.of(tb)
              Some((tb, tm, s.map(_._1), s.map(_._2)))
            case Right(_)  => None
            case Left(why) => scribe.warn(s"media: thumbnail of ${col.provider} ${rec.key} not fetched: $why"); None
        }
        val made = own.orElse(converter.thumbnail(bytes, MediaText.safeName(rec.key) + ".jpg", cfg.thumbPx) match
          case Right(t)  => Some((t.bytes, t.mime, Some(t.width).filter(_ > 0), Some(t.height).filter(_ > 0)))
          case Left(why) => scribe.info(s"media: no thumbnail for ${col.provider} ${rec.key} ($why)"); None)
        Right((bytes, mime, made))
    }
