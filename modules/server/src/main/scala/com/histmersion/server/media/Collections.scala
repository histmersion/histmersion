package com.histmersion.server.media

import com.histmersion.pluginhost.{Polite, PluginMedia}

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.db.MediaRepo
import com.histmersion.db.MediaRepo.{PlaceFacts, ProviderAsset, Rights, Target}
import com.histmersion.server.ConverterClient

/** An open-access collection (#1446): how to search it for a place, read its records, and tie one to
  * a place ([[MediaMatch]]). The import around it is [[CollectionImport]]. */
trait Collection:
  /** The asset provider and the `media_checks` name. */
  def provider: String
  /** The role its pictures play for a place: `object` (a museum's) or `map`. */
  def role: String
  def conf: MediaCollectionConfig
  /** The records a search for the place's name answers; one record may be unreadable on its own. */
  def search(fetch: Polite, place: PlaceFacts): Either[String, List[Either[String, MediaRecord]]]
  /** Why a record is tied to the place, or why not. */
  def matches(rec: MediaRecord, place: MediaMatch.PlaceInfo, resolve: MediaMatch.Resolve): Either[String, String] =
    MediaMatch.museum(rec, place, resolve)
  /** Does a match read the gazetteer ids the places carry ([[matchesWithIds]])? */
  def usesIds: Boolean = false
  /** Why a record is tied to the place, given what its gazetteer ids name on the map — [[matches]]
    * unless the collection's records name places by id. */
  def matchesWithIds(rec: MediaRecord, place: MediaMatch.PlaceInfo, resolve: MediaMatch.Resolve,
      resolveId: MediaMatch.ResolveId): Either[String, String] = matches(rec, place, resolve)
  /** A record about to be stored, completed by the request a search does not answer (Open Context's
    * media item: the files, and the licence read again); a `Left` refuses it. Only stored records pay
    * for it. */
  def complete(fetch: Polite, rec: MediaRecord): Either[String, MediaRecord] = Right(rec)

/** The Met: a `/v1.1/search` by the place's name (as `q` and `geoLocation`), then each object. */
final class MetCollection(val conf: MediaCollectionConfig) extends Collection:
  val provider = MediaProvider.Met
  val role     = MediaRole.Object
  def search(fetch: Polite, place: PlaceFacts): Either[String, List[Either[String, MediaRecord]]] =
    fetch.json(MetMuseum.searchUrl(conf.apiUrl, place.label, conf.candidates)).flatMap(MetMuseum.ids).map(
      _.take(conf.candidates.max(0)).map(id =>
        fetch.json(MetMuseum.objectUrl(conf.apiUrl, id)).flatMap(MetMuseum.record).left.map(e => s"object $id: $e")))

/** Cleveland: one search answers the records. */
final class ClevelandCollection(val conf: MediaCollectionConfig) extends Collection:
  val provider = MediaProvider.Cleveland
  val role     = MediaRole.Object
  def search(fetch: Polite, place: PlaceFacts): Either[String, List[Either[String, MediaRecord]]] =
    fetch.json(ClevelandArt.searchUrl(conf.apiUrl, place.label, conf.candidates)).flatMap(ClevelandArt.records)

/** Chicago: one search answers the fields; images from its IIIF server. */
final class ArtInstituteCollection(val conf: MediaCollectionConfig, thumbPx: Int) extends Collection:
  val provider = MediaProvider.ArtInstitute
  val role     = MediaRole.Object
  def search(fetch: Polite, place: PlaceFacts): Either[String, List[Either[String, MediaRecord]]] =
    fetch.json(ArtInstitute.searchUrl(conf.apiUrl, place.label, conf.candidates))
      .flatMap(ArtInstitute.records(_, conf.imageUrl, conf.displayPx, thumbPx))

/** The Library of Congress's maps: a search, then each item for its rights and files. A 403 is
  * loc.gov's bot challenge to automated clients — reported as such, never worked around. */
final class LocCollection(val conf: MediaCollectionConfig, thumbPx: Int) extends Collection:
  val provider = MediaProvider.LibraryOfCongress
  val role     = MediaRole.Map
  private def explain(e: String): String =
    if e.contains("HTTP 403") then s"$e — loc.gov refuses automated clients with a bot challenge at the moment; not worked around"
    else e
  def search(fetch: Polite, place: PlaceFacts): Either[String, List[Either[String, MediaRecord]]] =
    fetch.json(LibraryOfCongress.searchUrl(conf.apiUrl, place.label, conf.candidates)).flatMap(LibraryOfCongress.results)
      .left.map(explain)
      .map(_.take(conf.candidates.max(0)).map(id =>
        fetch.json(LibraryOfCongress.itemUrl(conf.apiUrl, id))
          .flatMap(LibraryOfCongress.record(_, conf.apiUrl, conf.maxYear, conf.displayPx, thumbPx))
          .left.map(e => s"item $id: ${explain(e)}")))
  override def matches(rec: MediaRecord, place: MediaMatch.PlaceInfo, resolve: MediaMatch.Resolve) =
    MediaMatch.map(rec, place, resolve, conf.maxYear)

/** Open Context: one search by the place's name answers the records with their licences, creators,
  * subjects and spatial coverage; a record to store is completed from its media item. A record is tied
  * by a shared gazetteer id first ([[MediaMatch.withIds]]), else by its context path's names. */
final class OpenContextCollection(val conf: MediaCollectionConfig) extends Collection:
  val provider = MediaProvider.OpenContext
  val role     = MediaRole.Object
  def search(fetch: Polite, place: PlaceFacts): Either[String, List[Either[String, MediaRecord]]] =
    fetch.json(OpenContext.searchUrl(conf.apiUrl, place.label, conf.candidates)).flatMap(OpenContext.records)
  override def usesIds = true
  override def matchesWithIds(rec: MediaRecord, place: MediaMatch.PlaceInfo, resolve: MediaMatch.Resolve,
      resolveId: MediaMatch.ResolveId) = MediaMatch.withIds(rec, place, resolveId, resolve, Set(OpenContext.Context))
  override def complete(fetch: Polite, rec: MediaRecord): Either[String, MediaRecord] =
    fetch.json(OpenContext.itemUrl(conf.apiUrl, rec.key)).flatMap(OpenContext.item(_, rec))

object Collection:
  /** Pure: the equivalents of a place among the places a name names — itself, one with its Wikidata
    * item, one within `sameSiteKm` of its point — collapse to its id; others keep theirs. */
  def resolveFor(target: PlaceFacts, named: Map[String, List[PlaceFacts]], sameSiteKm: Double): MediaMatch.Resolve =
    key => named.getOrElse(key, Nil).map(p => if equivalent(target, p, sameSiteKm) then target.id else p.id).toSet

  /** Pure: what a gazetteer id names for `target` — the places on the map carrying it (`byId`), the
    * target's equivalents collapsed to its id, and the target itself when it carries the id (`own`:
    * a place asked by `--place` need not be on the map). */
  def resolveIdFor(target: PlaceFacts, own: Set[(String, String)], byId: Map[(String, String), List[PlaceFacts]],
      sameSiteKm: Double): MediaMatch.ResolveId =
    id => byId.getOrElse(id, Nil).map(p => if equivalent(target, p, sameSiteKm) then target.id else p.id).toSet ++
      (if own(id) then Set(target.id) else Set.empty)

  def equivalent(a: PlaceFacts, b: PlaceFacts, km: Double): Boolean =
    a.id == b.id || a.qid.exists(q => b.qid.contains(q)) ||
      (for la <- a.lat; oa <- a.lon; lb <- b.lat; ob <- b.lon yield distanceKm(la, oa, lb, ob) <= km).getOrElse(false)

  /** Pure: great-circle distance in km. */
  def distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double =
    val r = 6371.0
    val (p1, p2) = (math.toRadians(lat1), math.toRadians(lat2))
    val dp = p2 - p1
    val dl = math.toRadians(lon2 - lon1)
    val a = math.pow(math.sin(dp / 2), 2) + math.cos(p1) * math.cos(p2) * math.pow(math.sin(dl / 2), 2)
    2 * r * math.asin(math.sqrt(a.min(1.0)))

  /** Pure: a record's licence key for the counts — its SPDX id, or why it has none. */
  def licenceKey(rec: MediaRecord): String = rec.rights.fold(_ => "not open access", _.licence.id)

/** The import of one collection (#1446): for each place, a search by its name; each record judged —
  * its own licence statement first (never assumed), the collection's allowlist, then the match
  * ([[Collection.matches]]) — and at most `per-place` kept, in the collection's relevance order. A dry
  * run stops there and lists the matches with their reasons for a person to judge. Otherwise each new
  * picture is downloaded (the collection's own web rendition, at most `media.max-bytes`), its thumbnail
  * taken from the collection when it offers one, else made here ([[ConverterClient.thumbnail]]), and
  * stored with the licence, credit line, page and original URL; the link keeps the match's reason. */
final class CollectionImport(col: Collection, fetch: Polite, cfg: MediaConfig, converter: ConverterClient):

  private final case class Judged(target: Target, rec: MediaRecord, decision: Either[String, String])

  def run(targets: List[Target], dryRun: Boolean, refresh: Boolean, store: MediaStore): MediaImportReport =
    val failed = List.newBuilder[String]
    val allow  = col.conf.allowed
    val places = targets.filter(_.targetType == MediaRepo.Place)
    val facts  = store.placeFacts(places.map(_.id))

    // 1. the searches — a place whose search failed is not recorded as asked; a refusal (403: a bot
    // challenge, a key wanted) ends the batch instead of being asked again for every place
    // `var`: set by the first refusal
    var refused = false
    val answered = places.flatMap { t =>
      facts.get(t.id) match
        case None => failed += s"place ${t.id}: not found"; None
        case Some(_) if refused => None
        case Some(f) =>
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
    val keys  = answered.flatMap(_._3).flatMap(_.geography.map(g => MediaMatch.keyOf(g._2))).filter(_.nonEmpty).distinct
    val named = store.mapPlacesNamed(keys)

    // 3. judge each record for its place (by the ids the place carries too, when the collection names them)
    val (own, byId) =
      if !col.usesIds then (Map.empty[String, Set[(String, String)]], Map.empty[(String, String), List[PlaceFacts]])
      else (store.placeExternalIds(answered.map(_._1.id)), store.mapPlacesWithIds(answered.flatMap(_._3).flatMap(_.ids).distinct))
    val judged = answered.flatMap { (t, f, recs) =>
      val info    = MediaMatch.PlaceInfo(f.id, f.label, f.keys, f.period)
      val resolve = Collection.resolveFor(f, named, cfg.sameSiteKm)
      val resolveId = Collection.resolveIdFor(f, own.getOrElse(t.id, Set.empty), byId, cfg.sameSiteKm)
      recs.distinctBy(_.key).map { rec =>
        val d = PluginMedia.refusal(rec, allow, col.provider).toLeft(())
          .flatMap(_ => col.matchesWithIds(rec, info, resolve, resolveId))
        Judged(t, rec, d)
      }
    }
    val kept = judged.groupBy(_.target.id).values.toList.flatMap(_.filter(_.decision.isRight).take(col.conf.perPlace.max(0)))
      .sortBy(j => (places.indexOf(j.target), judged.indexOf(j)))
    val records = judged.map(_.rec).distinctBy(_.key)
    val samples = kept.map { j =>
      MediaSample(j.target.targetType, j.target.id, j.target.label, j.rec.key, j.rec.title,
        Collection.licenceKey(j.rec), j.decision.getOrElse(""), j.rec.pageUrl)
    }
    val base = MediaImportReport(
      provider = col.provider, dryRun = dryRun, targets = places.size, items = records.size,
      statements = kept.size, files = kept.map(_.rec.key).distinct.size,
      byLicence = records.groupBy(Collection.licenceKey).toList.map((k, rs) =>
        MediaCount(k, rs.size, rs.head.rights.exists(r => MediaLicence.allowed(r.licence, allow)))).sortBy(c => (-c.n, c.key)),
      byRole = MediaCount.of(kept.map(_ => col.role)),
      rejected = MediaCount.of(judged.flatMap(_.decision.left.toOption)),
      samples = samples.take(cfg.samples.max(0)))

    if dryRun then base.copy(failed = failed.result())
    else persist(base, kept, judged, answered.map(_._1), refresh, store, failed)

  private def persist(base: MediaImportReport, kept: List[Judged], judged: List[Judged], asked: List[Target],
      refresh: Boolean, db: MediaStore, failed: scala.collection.mutable.Builder[String, List[String]]): MediaImportReport =
    val allow = col.conf.allowed
    val known = db.known(col.provider, judged.map(_.rec.key).distinct)
    def each[A](what: => String)(write: => A): Option[A] = MediaStep.each(failed)(what)(write)

    // a stored picture whose record no longer allows it is KEPT, and listed for review
    val revoked = judged.map(_.rec).distinctBy(_.key).flatMap { rec =>
      known.get(rec.key).flatMap { k =>
        rec.rights match
          case _ if rec.withheld.isDefined => Some(s"${col.provider} ${rec.key} (media:${k.uid}): ${rec.withheld.get}")
          case Left(why) => Some(s"${col.provider} ${rec.key} (media:${k.uid}): $why")
          case Right(r) if !MediaLicence.allowed(r.licence, allow) =>
            Some(s"${col.provider} ${rec.key} (media:${k.uid}): licence ${r.licence.family} not allowed")
          case _ => None
      }
    }

    // `var`s: the counts of what this run wrote
    var stored, updated = 0
    val uids = scala.collection.mutable.Map.empty[String, String]
    def rightsOf(rec: MediaRecord) =
      val r = rec.rights.toOption.get   // kept ⇒ readable rights
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
          val completed = col.complete(fetch, j.rec).filterOrElse(
            _.rights.exists(r => MediaLicence.allowed(r.licence, allow)), "licence not allowed on the full record")
          completed.flatMap(rec => download(rec).map(d => (rec, d))) match
            case Left(why) => failed += s"${col.provider} ${j.rec.key}: $why"
            case Right((rec, (bytes, mime, thumb))) =>
              val rights = rightsOf(rec)
              val size = ImageSize.of(bytes)
              val a = ProviderAsset(col.provider, rec.key, MediaText.safeName(s"${col.provider}-${rec.key}-${rec.title}"),
                mime, bytes, size.map(_._1), size.map(_._2), thumb.map(_._1), thumb.map(_._2), thumb.flatMap(_._3),
                thumb.flatMap(_._4), rights.license, rights.licenseUrl, rights.author, rights.credit, rec.pageUrl,
                rec.originalUrl, Some(MediaRepo.kindOf(col.role)))
              each(s"${rec.key} (store)")(db.save(a, (j.target.targetType, j.target.id))).foreach { (uid, inserted) =>
                if inserted then stored += 1 else updated += 1
                uids(rec.key) = uid
              }
    }

    val ranks = kept.groupBy(_.target.id).view.mapValues(_.map(_.rec.key)).toMap
    val linked = kept.count(j => uids.get(j.rec.key).exists(uid =>
      each(s"${j.rec.key} → ${j.target.targetType} ${j.target.id} (link)")(
        db.linkBasis(uid, j.target.targetType, j.target.id, col.role, None, None,
          ranks.getOrElse(j.target.id, Nil).indexOf(j.rec.key).max(0), j.decision.toOption)).getOrElse(false)))
    val found = kept.filter(j => uids.contains(j.rec.key)).groupBy(_.target.id).view.mapValues(_.size).toMap
    asked.foreach(t =>
      each(s"${t.targetType} ${t.id} (check)")(db.checked(col.provider, t.targetType, t.id, found.getOrElse(t.id, 0), None)))
    base.copy(stored = stored, updated = updated, linked = linked, revoked = revoked.sorted, failed = failed.result())

  /** The record's rendition (at most `max-bytes`) and its thumbnail — the collection's own small
    * rendition, else one made here; without either the card shows no picture for it. */
  private def download(rec: MediaRecord)
      : Either[String, (Array[Byte], String, Option[(Array[Byte], String, Option[Int], Option[Int])])] =
    rec.imageUrl.toRight("no image").flatMap(u => fetch.file(u, "image/jpeg")).flatMap { (bytes, mime) =>
      if bytes.length.toLong > cfg.maxBytes then Left(s"rendition of ${bytes.length} bytes is larger than media.max-bytes")
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
