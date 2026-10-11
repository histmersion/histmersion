package com.histmersion.server.media

import com.histmersion.core.*
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

  /** One picture statement of one target, the file named by its canonical Commons title. */
  private final case class Use(target: Target, role: String, property: String, file: String, rank: Int)

  /** What Commons said about one file: its licence key (for the counts), and the info and rights when
    * it may be stored, else why not. */
  private final case class Verdict(licence: Option[String], decision: Either[String, (Commons.FileInfo, Commons.Rights)])

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
            Option(Commons.fileTitle(st.file)).filter(_.nonEmpty).map(Use(t, role, prop, _, i))
          }
        }
      }
    }
    val categories = targets.filter(_.targetType == MediaRepo.Place).flatMap(t =>
      items.get(t.qid).flatMap(_.commonsCategory).map(c => (t, c)))

    // 2. what Commons says about each file
    val titles = uses.map(_.file).distinct
    val infos  = imageInfo(titles, cfg.displayPx, meta = true, failed)
    val verdicts: Map[String, Verdict] = titles.map { title =>
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

    val licenceKeys = verdicts.values.flatMap(_.licence).toList
    val base = MediaImportReport(
      provider = provider, dryRun = dryRun, targets = targets.size, items = items.size, statements = uses.size,
      files = titles.size,
      byLicence = MediaCount.of(licenceKeys, k => MediaLicence.allowed(MediaLicence.classify(k), allow)),
      byRole = MediaCount.of(uses.map(_.role)),
      rejected = MediaCount.of(verdicts.values.flatMap(_.decision.left.toOption).toList))

    if dryRun then base.copy(failed = failed.result())
    else persist(base, uses, verdicts, categories, targets.filterNot(t => unanswered(t.qid)), refresh, store, failed)

  /** `imageinfo` for the titles, in batches; a failed batch is reported and its titles get no answer. */
  private def imageInfo(titles: List[String], width: Int, meta: Boolean,
      failed: scala.collection.mutable.Builder[String, List[String]]): Map[String, Either[String, Commons.FileInfo]] =
    titles.grouped(cfg.commons.batch.max(1)).toList.flatMap { batch =>
      fetch.json(Commons.imageInfoUrl(cfg.commons.apiUrl, batch, width, cfg.commons.maxlag, meta))
        .flatMap(Commons.imageInfo) match
          case Right(m) => m.toList
          case Left(e)  => failed += s"Commons ${batch.head}…: $e"; Nil
    }.toMap

  private def persist(base: MediaImportReport, uses: List[Use], verdicts: Map[String, Verdict],
      categories: List[(Target, String)], targets: List[Target], refresh: Boolean, db: MediaStore,
      failed: scala.collection.mutable.Builder[String, List[String]]): MediaImportReport =
    val known = db.known(provider, verdicts.keys.toList)

    // a stored picture whose licence a re-read no longer allows is KEPT, and listed for review
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
    accepted.toList.sortBy(_._1).foreach { case (title, (info, rights)) =>
      val r = Rights(rights.licence.id, rights.licenceUrl, rights.author, rights.credit)
      known.get(title) match
        case Some(k) =>
          if refresh then db.refreshRights(k.uid, r)
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
              val (uid, inserted) = db.save(a, (use.target.targetType, use.target.id))
              if inserted then stored += 1 else updated += 1
              uids(title) = uid
    }
    if refresh then updated += accepted.keys.count(known.contains)

    val linked = uses.count(u => uids.get(u.file).exists(uid =>
      db.link(uid, u.target.targetType, u.target.id, u.role, Some(u.property), Some(u.target.qid), u.rank)))
    val cats = categories.count((t, c) => db.commonsCategory(t.id, c, t.qid))
    val found = uses.filter(u => uids.contains(u.file)).groupBy(u => (u.target.targetType, u.target.id))
      .view.mapValues(_.map(_.file).distinct.size).toMap
    targets.foreach(t => db.checked(provider, t.targetType, t.id, found.getOrElse((t.targetType, t.id), 0), Some(t.qid)))

    base.copy(stored = stored, updated = updated, linked = linked, categories = cats, revoked = revoked.sorted,
      failed = failed.result())

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
