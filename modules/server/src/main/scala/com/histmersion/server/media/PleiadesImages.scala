package com.histmersion.server.media

import com.histmersion.pluginhost.{allowed, Polite}

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.db.MediaRepo
import com.histmersion.db.MediaRepo.Target
import com.histmersion.values.MediaRole

/** Pictures a Pleiades place record links (#1446). For each place on the map that carries a Pleiades id, its place
  * record (`/places/{id}/json`, no key) is read once; the Wikimedia Commons files its references name
  * ([[PleiadesLinks]]) — at most `per-place`, in the record's order — are judged by their licence ON COMMONS under
  * `media.pleiades.licences` ([[CommonsFiles]]: read, never assumed; Pleiades' own CC BY covers its data, not what it
  * links) and linked in role `image`, the link's basis naming the record. No name is matched: the place carries the id
  * the record is under. A link to Flickr (its API needs a key), to a Commons category or to a picture on another site
  * is counted with that reason. The pictures are ordinary `wikimedia-commons` assets (one stored already is linked once
  * more); the checks are recorded as `pleiades`.
  */
final class PleiadesImages(fetch: Polite, cfg: MediaConfig):

  private val files = CommonsFiles(fetch, cfg)
  private val conf = cfg.pleiades

  def run(targets: List[Target], dryRun: Boolean, refresh: Boolean, store: MediaStore): MediaImportReport =
    val failed = List.newBuilder[String]
    val allow = conf.allowed
    val places = targets.filter(_.targetType == MediaRepo.Place)
    val ids = store.pleiadesIds(places.map(_.id))
    // places whose record did not answer are NOT recorded as asked: the next run asks again
    val unanswered = scala.collection.mutable.Set.empty[String]

    // 1. each place's record(s): the picture links, in order
    // `var`: the place records that answered
    var records = 0
    val read = places.map { t =>
      val pids = ids.getOrElse(t.id, Nil)
      if pids.isEmpty then failed += s"place ${t.id} (${t.label}): no Pleiades id"
      t -> pids.flatMap { pid =>
        fetch.json(PleiadesLinks.placeUrl(conf.apiUrl, pid)).flatMap(PleiadesLinks.links) match
          case Right(ls) => records += 1; ls.map(pid -> _)
          case Left(e)   => failed += s"Pleiades place $pid (${t.id}): $e"; unanswered += t.id; Nil
      }
    }

    // 2. the Commons files, at most per-place; the rest counted by reason
    val notTaken = read.flatMap(_._2.flatMap(_._2.file.left.toOption))
    val uses = read.flatMap { (t, links) =>
      links
        .flatMap((pid, l) => l.file.toOption.map(f => (pid, l, f)))
        .distinctBy(_._3)
        .take(conf.perPlace.max(0))
        .zipWithIndex
        .map { case ((pid, l, f), i) =>
          CommonsUse(t, MediaRole.Image, "", f, i, "", Some(s"Pleiades place $pid links it (${l.citation})"))
        }
    }
    val verdicts = files.judge(uses.map(_.file).distinct, allow, failed)
    val samples = uses
      .flatMap { u =>
        verdicts.get(u.file).flatMap(_.decision.toOption).map { (info, r) =>
          MediaSample(
            u.target.targetType,
            u.target.id,
            u.target.label,
            u.file,
            u.file.stripPrefix("File:"),
            r.licence.id,
            u.basis.getOrElse(""),
            info.pageUrl
          )
        }
      }
      .take(cfg.samples.max(0))
    val counted = files.counts(
      MediaImportReport(provider = MediaProvider.Pleiades, dryRun = dryRun, targets = places.size, items = records),
      uses,
      verdicts,
      allow
    )
    val base = counted.copy(
      samples = samples,
      rejected = MediaCount.of(counted.rejected.flatMap(c => List.fill(c.n)(c.key)) ++ notTaken)
    )

    if dryRun then base.copy(failed = failed.result())
    else
      val w = files.persist(
        uses,
        verdicts,
        places.filterNot(t => unanswered(t.id)),
        refresh,
        store,
        failed,
        MediaProvider.Pleiades
      )
      base.copy(
        stored = w.stored,
        updated = w.updated,
        linked = w.linked,
        revoked = w.revoked,
        failed = failed.result()
      )
