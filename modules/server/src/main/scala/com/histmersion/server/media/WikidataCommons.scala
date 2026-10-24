package com.histmersion.server.media

import com.histmersion.pluginhost.Polite

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.db.MediaRepo
import com.histmersion.db.MediaRepo.{Known, ProviderAsset, Rights, Target}

/** Where an import writes. The database in the server ([[DbMediaStore]]); a map in a test. */
trait MediaStore:
  def known(provider: String, keys: List[String]): Map[String, Known]
  def save(a: ProviderAsset, firstTarget: (String, String)): (String, Boolean)
  def refreshRights(uid: String, r: Rights): Unit
  def link(uid: String, targetType: String, targetId: String, role: String, property: Option[String],
      item: Option[String], rank: Int): Boolean
  def commonsCategory(placeId: String, category: String, qid: String): Boolean
  def checked(provider: String, targetType: String, targetId: String, found: Int, qid: Option[String]): Unit
  /** A link with the reason a collection's record is tied to its target (V094). */
  def linkBasis(uid: String, targetType: String, targetId: String, role: String, property: Option[String],
      item: Option[String], rank: Int, basis: Option[String]): Boolean =
    link(uid, targetType, targetId, role, property, item, rank)
  /** The places a collection import asks about, as a match sees them. */
  def placeFacts(ids: List[String]): Map[String, MediaRepo.PlaceFacts] = Map.empty
  /** The places on the map each name key names. */
  def mapPlacesNamed(keys: List[String]): Map[String, List[MediaRepo.PlaceFacts]] = Map.empty
  /** The gazetteer ids each place carries ([[ExternalIds]] pairs). */
  def placeExternalIds(ids: List[String]): Map[String, Set[(String, String)]] = Map.empty
  /** The Pleiades ids of places (those without one left out). */
  def pleiadesIds(ids: List[String]): Map[String, List[String]] = Map.empty
  /** The places on the map carrying each gazetteer id. */
  def mapPlacesWithIds(ids: List[(String, String)]): Map[(String, String), List[MediaRepo.PlaceFacts]] = Map.empty

final class DbMediaStore(ds: javax.sql.DataSource) extends MediaStore:
  def known(provider: String, keys: List[String]) = MediaRepo.known(ds, provider, keys)
  def save(a: ProviderAsset, firstTarget: (String, String)) = MediaRepo.save(ds, a, firstTarget)
  def refreshRights(uid: String, r: Rights) = MediaRepo.refreshRights(ds, uid, r)
  def link(uid: String, targetType: String, targetId: String, role: String, property: Option[String],
      item: Option[String], rank: Int) = MediaRepo.link(ds, uid, targetType, targetId, role, property, item, rank)
  def commonsCategory(placeId: String, category: String, qid: String) =
    MediaRepo.commonsCategory(ds, placeId, category, qid)
  def checked(provider: String, targetType: String, targetId: String, found: Int, qid: Option[String]) =
    MediaRepo.checked(ds, provider, targetType, targetId, found, qid)
  override def linkBasis(uid: String, targetType: String, targetId: String, role: String, property: Option[String],
      item: Option[String], rank: Int, basis: Option[String]) =
    MediaRepo.link(ds, uid, targetType, targetId, role, property, item, rank, basis)
  override def placeFacts(ids: List[String]) = MediaRepo.placeFacts(ds, ids)
  override def mapPlacesNamed(keys: List[String]) = MediaRepo.mapPlacesNamed(ds, keys)
  override def placeExternalIds(ids: List[String]) = MediaRepo.placeExternalIds(ds, ids)
  override def pleiadesIds(ids: List[String]) = MediaRepo.pleiadesIds(ds, ids)
  override def mapPlacesWithIds(ids: List[(String, String)]) = MediaRepo.mapPlacesWithIds(ds, ids)

/** One picture of one target, the file named by its canonical Commons title: the role it plays, the
  * statement it was read from (`P18` of the item, `P180` depicts, `P1299` depicted by; empty when it
  * is not a Wikidata statement), its rank among the target's pictures of that kind, the Wikidata item
  * the link records (empty: none), and why it is tied to the target when no statement says so (a
  * Pleiades place record linking it). */
private[media] final case class CommonsUse(target: Target, role: String, property: String, file: String, rank: Int,
    item: String, basis: Option[String] = None)

/** The Commons half every Commons-backed import shares (#1446): what Commons says about each file
  * — licence, author, credit from `imageinfo` + `extmetadata`, read and never assumed — judged by the
  * allowlist; the counts a dry run reports; and, for a real run, the download of Commons' own
  * renditions, the store, the links and the checks. */
private[media] final class CommonsFiles(fetch: Polite, cfg: MediaConfig):

  val provider: String = MediaProvider.Commons

  /** What Commons said about one file: its licence key (for the counts), and the info and rights when
    * it may be stored, else why not. */
  final case class Verdict(licence: Option[String], decision: Either[String, (Commons.FileInfo, Commons.Rights)])

  type Failed = scala.collection.mutable.Builder[String, List[String]]

  /** Each title's verdict under the allowlist `allow`. */
  def judge(titles: List[String], allow: Set[String], failed: Failed): Map[String, Verdict] =
    val infos = imageInfo(titles, cfg.displayPx, meta = true, failed)
    titles.map { title =>
      title -> (infos.get(title) match
        case None            => Verdict(None, Left("no answer from Commons"))
        case Some(Left(why)) => Verdict(None, Left(why))
        case Some(Right(info)) =>
          val lic = MediaLicence.parse(info.meta.get("License"), info.meta.get("LicenseShortName").map(MediaText.plain(_)))
          val key = Some(if lic.id.isEmpty then MediaLicence.Unknown else lic.id)
          Commons.rights(info.meta) match
            case Left(why) => Verdict(key, Left(why))
            case Right(r) if !MediaLicence.allowed(r.licence, allow) =>
              Verdict(key, Left(s"licence ${r.licence.family} not allowed for $provider"))
            case Right(r) => Verdict(key, Right(info -> r)))
    }.toMap

  /** The counts of a report: per licence, per role, and the refusals by reason. */
  def counts(base: MediaImportReport, uses: List[CommonsUse], verdicts: Map[String, Verdict], allow: Set[String])
      : MediaImportReport =
    base.copy(statements = uses.size, files = verdicts.size,
      byLicence = MediaCount.of(verdicts.values.flatMap(_.licence).toList,
        k => MediaLicence.allowed(MediaLicence.classify(k), allow)),
      byRole = MediaCount.of(uses.map(_.role)),
      rejected = MediaCount.of(verdicts.values.flatMap(_.decision.left.toOption).toList))

  /** `imageinfo` for the titles, in batches; a failed batch is reported and its titles get no answer. */
  def imageInfo(titles: List[String], width: Int, meta: Boolean, failed: Failed)
      : Map[String, Either[String, Commons.FileInfo]] =
    titles.grouped(cfg.commons.batch.max(1)).toList.flatMap { batch =>
      fetch.json(Commons.imageInfoUrl(cfg.commons.apiUrl, batch, width, cfg.commons.maxlag, meta))
        .flatMap(Commons.imageInfo) match
          case Right(m) => m.toList
          case Left(e)  => failed += s"Commons ${batch.head}…: $e"; Nil
    }.toMap

  /** What [[persist]] wrote, for the report. */
  final case class Written(stored: Int, updated: Int, linked: Int, revoked: List[String])

  /** Store the accepted files not stored yet, link every use whose file is stored, and record each
    * target as asked by `check` (with the number of pictures it got). A stored picture whose licence a
    * re-read no longer allows is KEPT, and listed for review. */
  def persist(uses: List[CommonsUse], verdicts: Map[String, Verdict], targets: List[Target], refresh: Boolean,
      db: MediaStore, failed: Failed, check: String): Written =
    val known = db.known(provider, verdicts.keys.toList)

    val revoked = verdicts.toList.collect {
      case (title, Verdict(_, Left(why))) if known.contains(title) && !why.startsWith("no answer") &&
          !why.startsWith("missing") => s"$title (media:${known(title).uid}): $why"
    }

    val accepted = verdicts.collect { case (t, Verdict(_, Right(v))) => t -> v }
    val toFetch  = accepted.keys.filterNot(known.contains).toList.sorted
    val thumbs   = imageInfo(toFetch, cfg.thumbPx, meta = false, failed)
    val firstUse = uses.groupBy(_.file).view.mapValues(_.head).toMap

    // `var`s: the counts of what this run wrote
    var stored, updated = 0
    val uids = scala.collection.mutable.Map.empty[String, String]
    // one picture that cannot be written is a line in the report, not the end of the run (#1491)
    def each[A](what: => String)(write: => A): Option[A] = MediaStep.each(failed)(what)(write)
    accepted.toList.sortBy(_._1).foreach { case (title, (info, rights)) =>
      val r = Rights(rights.licence.id, rights.licenceUrl, rights.author, rights.credit)
      known.get(title) match
        case Some(k) =>
          if refresh then each(s"$title (licence re-read)")(db.refreshRights(k.uid, r))
          uids(title) = k.uid
        case None =>
          download(info, thumbs.get(title).flatMap(_.toOption)) match
            case Left(why) => failed += s"$title: $why"
            case Right((bytes, mime, thumb)) =>
              val use = firstUse(title)
              val a = ProviderAsset(provider, title, MediaText.safeName(title), mime, bytes,
                info.renditionWidth.orElse(info.width), info.renditionHeight.orElse(info.height),
                thumb.map(_._1), thumb.map(_._2), thumb.flatMap(_._3), thumb.flatMap(_._4),
                r.license, r.licenseUrl, r.author, r.credit, info.pageUrl, Some(info.originalUrl),
                Some(MediaRepo.kindOf(use.role)))
              each(s"$title (store)")(db.save(a, (use.target.targetType, use.target.id))).foreach { (uid, inserted) =>
                if inserted then stored += 1 else updated += 1
                uids(title) = uid
              }
    }
    if refresh then updated += accepted.keys.count(known.contains)

    val linked = uses.count(u => uids.get(u.file).exists(uid =>
      each(s"${u.file} → ${u.target.targetType} ${u.target.id} (link)")(
        db.linkBasis(uid, u.target.targetType, u.target.id, u.role, Some(u.property).filter(_.nonEmpty),
          Some(u.item).filter(_.nonEmpty), u.rank, u.basis)).getOrElse(false)))
    val found = uses.filter(u => uids.contains(u.file)).groupBy(u => (u.target.targetType, u.target.id))
      .view.mapValues(_.map(_.file).distinct.size).toMap
    targets.foreach(t =>
      each(s"${t.targetType} ${t.id} (check)")(
        db.checked(check, t.targetType, t.id, found.getOrElse((t.targetType, t.id), 0), Some(t.qid).filter(_.nonEmpty))))
    Written(stored, updated, linked, revoked.sorted)

  /** The rendition and the thumbnail of one file — Commons' own resized copies; the rendition must not
    * pass `max-bytes`. A failed thumbnail leaves the picture without one (the card skips it). */
  private def download(info: Commons.FileInfo, thumb: Option[Commons.FileInfo])
      : Either[String, (Array[Byte], String, Option[(Array[Byte], String, Option[Int], Option[Int])])] =
    if info.renditionUrl.isEmpty && info.size.exists(_ > cfg.maxBytes) then
      Left(s"original of ${info.size.getOrElse(0L)} bytes is larger than media.max-bytes")
    else
      fetch.file(info.renditionUrl.getOrElse(info.originalUrl), info.mime).flatMap { (bytes, mime) =>
        if bytes.length.toLong > cfg.maxBytes then Left(s"rendition of ${bytes.length} bytes is larger than media.max-bytes")
        else
          val t = thumb.flatMap(i => i.renditionUrl.map(u => (u, i))).flatMap { (u, i) =>
            fetch.file(u, i.mime) match
              case Right((tb, tm)) => Some((tb, tm, i.renditionWidth, i.renditionHeight))
              case Left(why)       => scribe.warn(s"media: thumbnail of ${info.title} not fetched: $why"); None
          }
          Right((bytes, mime, t))
      }

/** One write of an import that may fail on its own: logged and listed, never the end of the run. */
private[media] object MediaStep:
  def each[A](failed: scala.collection.mutable.Builder[String, List[String]])(what: => String)(write: => A): Option[A] =
    try Some(write)
    catch
      case e: InterruptedException => throw e
      case scala.util.control.NonFatal(e) =>
        scribe.warn(s"media import: $what: ${e.getMessage}")
        failed += s"$what: ${e.getClass.getSimpleName}: ${e.getMessage}"
        None

/** The Wikidata → Wikimedia Commons provider (#1446).
  *
  * For each place or event: its Wikidata item's picture statements (a place: image P18, locator map
  * P242, plan P3311, aerial view P8592, route map P15; an event: P18 — `media.wikidata.*-properties`),
  * the best-ranked `per-property` of each, and a place's Commons category (P373). For each file named:
  * its licence, author and credit from Commons' `imageinfo` + `extmetadata` — read, never assumed —
  * and the provider's allowlist ([[MediaLicence]]). A dry run stops there and reports. Otherwise each
  * allowed file not stored yet is downloaded as Commons' own renditions (`display-px` wide, and the
  * `thumb-px` card thumbnail), stored and linked; a stored one is linked and, with `refresh`, has its
  * licence re-read. Documented APIs only, through [[Polite]]. */
final class WikidataCommons(fetch: Polite, cfg: MediaConfig):

  val provider: String = MediaProvider.Commons
  private val files = CommonsFiles(fetch, cfg)

  def run(targets: List[Target], dryRun: Boolean, refresh: Boolean, store: MediaStore): MediaImportReport =
    val failed = List.newBuilder[String]
    val allow  = cfg.commons.allowed

    // 1. the items' statements
    val props = (cfg.wikidata.placeRoles ++ cfg.wikidata.eventRoles).map(_._1).toSet
    val qids  = targets.map(_.qid).distinct
    // the items whose batch Wikidata did not answer: their targets are NOT recorded as asked, so the
    // next run (or the job's next batch) asks again
    val unanswered = scala.collection.mutable.Set.empty[String]
    val items = qids.grouped(cfg.wikidata.batch.max(1)).toList.flatMap { ids =>
      fetch.json(Wikidata.entitiesUrl(cfg.wikidata.apiUrl, ids, cfg.wikidata.maxlag))
        .flatMap(Wikidata.items(_, props)) match
          case Right(m) => m.toList
          case Left(e)  => failed += s"Wikidata ${ids.head}…: $e"; unanswered ++= ids; Nil
    }.toMap

    val uses = targets.flatMap { t =>
      val roles = if t.targetType == MediaRepo.Place then cfg.wikidata.placeRoles else cfg.wikidata.eventRoles
      items.get(t.qid).toList.flatMap { item =>
        roles.flatMap { (prop, role) =>
          Wikidata.best(item.statements.filter(_.property == prop), cfg.perProperty).zipWithIndex.flatMap { (st, i) =>
            Option(Commons.fileTitle(st.file)).filter(_.nonEmpty).map(CommonsUse(t, role, prop, _, i, t.qid))
          }
        }
      }
    }
    val categories = targets.filter(_.targetType == MediaRepo.Place).flatMap(t =>
      items.get(t.qid).flatMap(_.commonsCategory).map(c => (t, c)))

    // 2. what Commons says about each file
    val verdicts = files.judge(uses.map(_.file).distinct, allow, failed)
    val base = files.counts(MediaImportReport(provider = provider, dryRun = dryRun, targets = targets.size,
      items = items.size), uses, verdicts, allow)

    if dryRun then base.copy(failed = failed.result())
    else
      val w = files.persist(uses, verdicts, targets.filterNot(t => unanswered(t.qid)), refresh, store, failed, provider)
      val cats = categories.count((t, c) =>
        MediaStep.each(failed)(s"category $c → ${t.id}")(store.commonsCategory(t.id, c, t.qid)).getOrElse(false))
      base.copy(stored = w.stored, updated = w.updated, linked = w.linked, categories = cats, revoked = w.revoked,
        failed = failed.result())
