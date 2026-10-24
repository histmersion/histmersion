package com.histmersion.server.media

import com.histmersion.pluginhost.Polite

import com.histmersion.core.*
import com.histmersion.db.MediaRepo
import com.histmersion.db.MediaRepo.Target

/** Pictures that DEPICT an event (#1446), from Wikimedia Commons: for each event with a Wikidata item,
  *  1. the works its item lists as `P1299 depicted by` (a painting, a relief) — each work's own image
  *     (P18) — when `media.depicts.depicted-by` is on; then
  *  2. the Commons files whose structured data says `P180 depicts` the item, found by CirrusSearch's
  *     documented `haswbstatement` keyword through the action API (no login, no query service);
  * at most `media.depicts.per-event`, linked in role `depiction`. Licence, author and credit come
  * from Commons as for every Commons picture ([[CommonsFiles]]), under `media.depicts.licences`. A
  * picture already stored for another reason (the event's P18) is the same asset, linked once more. */
final class CommonsDepictions(fetch: Polite, cfg: MediaConfig):

  private val files = CommonsFiles(fetch, cfg)

  def run(targets: List[Target], dryRun: Boolean, refresh: Boolean, store: MediaStore): MediaImportReport =
    val failed = List.newBuilder[String]
    val allow  = cfg.depicts.allowed
    val events = targets.filter(t => t.targetType == MediaRepo.Event && Wikidata.isQid(t.qid))
    val unanswered = scala.collection.mutable.Set.empty[String]

    // 1. P1299 on the events' items, then P18 of the works named
    val (eventItems, depictedBy) =
      if !cfg.depicts.depictedBy || events.isEmpty then (0, Map.empty[String, List[String]])
      else
        val answers = events.map(_.qid).distinct.grouped(cfg.wikidata.batch.max(1)).toList.map { ids =>
          fetch.json(Wikidata.entitiesUrl(cfg.wikidata.apiUrl, ids, cfg.wikidata.maxlag)).flatMap { j =>
            j.hcursor.downField("error").focus.fold(Right(j))(_ => Left("Wikidata error"))
          } match
            case Right(j) => CommonsDepicts.depictedBy(j)
            case Left(e)  => failed += s"Wikidata ${ids.head}…: $e"; unanswered ++= ids; Map.empty
        }
        val m = answers.flatten.toMap
        (m.size, m)
    val works = depictedBy.values.flatten.toList.distinct
    val workImage: Map[String, String] = works.grouped(cfg.wikidata.batch.max(1)).toList.flatMap { ids =>
      fetch.json(Wikidata.entitiesUrl(cfg.wikidata.apiUrl, ids, cfg.wikidata.maxlag))
        .flatMap(Wikidata.items(_, Set("P18"))) match
          case Right(items) =>
            items.toList.flatMap((q, item) => Wikidata.best(item.statements, 1).headOption.map(st => q -> Commons.fileTitle(st.file)))
          case Left(e) => failed += s"Wikidata works ${ids.head}…: $e"; Nil
    }.toMap

    // 2. Commons' P180 depicts, one search per event
    val depicts: Map[String, List[String]] = events.flatMap { t =>
      fetch.json(CommonsDepicts.searchUrl(cfg.commons.apiUrl, t.qid, cfg.depicts.searchLimit, cfg.commons.maxlag))
        .flatMap(CommonsDepicts.titles) match
          case Right(ts) => Some(t.id -> ts)
          case Left(e)   => failed += s"Commons search for ${t.id} (${t.qid}): $e"; unanswered += t.qid; None
    }.toMap

    val uses = events.flatMap { t =>
      val fromWorks = depictedBy.getOrElse(t.qid, Nil).flatMap(workImage.get).filter(_.nonEmpty)
        .map(f => (CommonsDepicts.DepictedBy, f))
      val fromFiles = depicts.getOrElse(t.id, Nil).map(f => (CommonsDepicts.Depicts, Commons.fileTitle(f)))
      (fromWorks ++ fromFiles).distinctBy(_._2).take(cfg.depicts.perEvent.max(0)).zipWithIndex.map { case ((p, f), i) =>
        CommonsUse(t, MediaRole.Depiction, p, f, i, t.qid)
      }
    }

    val verdicts = files.judge(uses.map(_.file).distinct, allow, failed)
    val samples = uses.flatMap { u =>
      verdicts.get(u.file).flatMap(_.decision.toOption).map { (info, r) =>
        MediaSample(u.target.targetType, u.target.id, u.target.label, u.file, u.file.stripPrefix("File:"), r.licence.id,
          s"${u.property} ${if u.property == CommonsDepicts.Depicts then "depicts" else "depicted by"} ${u.target.qid}",
          info.pageUrl)
      }
    }.take(cfg.samples.max(0))
    val base = files.counts(MediaImportReport(provider = CommonsDepictions.Check, dryRun = dryRun,
      targets = events.size, items = eventItems + workImage.size), uses, verdicts, allow).copy(samples = samples)

    if dryRun then base.copy(failed = failed.result())
    else
      val w = files.persist(uses, verdicts, events.filterNot(t => unanswered(t.qid)), refresh, store, failed,
        CommonsDepictions.Check)
      base.copy(stored = w.stored, updated = w.updated, linked = w.linked, revoked = w.revoked, failed = failed.result())

object CommonsDepictions:
  /** What `media_checks` records the depictions import under — the pictures themselves are
    * `wikimedia-commons` assets, shared with the Wikidata import. */
  val Check: String = MediaProvider.CommonsDepicts
