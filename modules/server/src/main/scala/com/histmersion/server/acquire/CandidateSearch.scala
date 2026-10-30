package com.histmersion.server.acquire

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.GoalPlans.LinkKind
import com.histmersion.db.{AcquireRepo, ArticleRepo, CandidateRepo, GoalLinkRepo, GoalRepo, QueueRepo, SourceRepo}

import java.time.{LocalDate, ZoneOffset}
import javax.sql.DataSource

/** The deterministic new-source search of one goal (#3761, docs/research-goal-advancement.md): its WATCHES — every
  * saved query (its linked `query` links, else its title) on every index of `acquire.candidates.indexes`, and for up to
  * `sources` of its sources (linked, and cited by its linked articles) the works citing it and the newer works of its
  * first and last authors (OpenAlex) — each read from its watermark; the works found deduplicated by DOI across watches
  * and against the corpus, the acquisition list and the goal's earlier candidates; the new ones stored as `new`. No
  * model runs here: the sweep only calls the indexes, so a schedule can run it as often as it likes for the price of
  * the requests, and starts a model only when it found something new.
  */
final class CandidateSearch(
    ds: DataSource,
    cfg: => CandidatesConfig,
    indexes: => List[ScholarlyIndex],
    alerts: => CitationAlerts,
    today: () => LocalDate = () => LocalDate.now(ZoneOffset.UTC)
):

  /** The goal's watches as of now: what is read, from where (the watermark is the stored one). */
  private def plan(goal: Goal): List[CandidateWatch] =
    val c = cfg
    val queries = Some(GoalLinkRepo.refs(ds, goal.slug, LinkKind.Query)).filter(_.nonEmpty).getOrElse(List(goal.title))
    val q = for query <- queries; i <- indexes
    yield CandidateWatch(goal.slug, Candidates.WatchKind.Query.wire, query, i.name)
    val cited = (GoalLinkRepo.refs(ds, goal.slug, LinkKind.Source) ++
      GoalLinkRepo
        .refs(ds, goal.slug, LinkKind.Article)
        .flatMap(a => ArticleRepo.sourcesCited(ds, a).filter(_.title.isDefined).map(_.citekey))).distinct
      .take(math.max(0, c.sources))
    val withDoi = cited.flatMap(ck => SourceRepo.load(ds, ck).flatMap(_.doi).flatMap(Acquire.normalizeDoi).map(ck -> _))
    val citing = withDoi.map((ck, doi) => CandidateWatch(goal.slug, Candidates.WatchKind.Citing.wire, doi, "openalex"))
    q ++ citing

  /** Sweep `goal` now; a dry run reads, but stores neither candidates nor watermarks. */
  def sweep(goal: String, dryRun: Boolean): Either[ApiError, CandidateSweep] =
    GoalRepo.get(ds, goal).toRight(ApiError(s"no goal '$goal'", Some(Write.CodeNotFound))).map { g =>
      val c = cfg
      val now = today()
      val stored = CandidateRepo.watches(ds, g.slug).map(w => (w.kind, w.subject, w.index) -> w).toMap
      def known(w: CandidateWatch) =
        stored.get((w.kind, w.subject, w.index)).fold(w)(s => w.copy(watermark = s.watermark))
      val base = plan(g).map(known)
      val byIndex = indexes.map(i => i.name -> i).toMap
      // the authors of the cited sources: their own watches (found through OpenAlex, then kept)
      val authorWatches = base
        .filter(_.kind == Candidates.WatchKind.Citing.wire)
        .flatMap { w =>
          alerts
            .authors(w.subject)
            .toOption
            .toList
            .flatMap(as => (as.headOption ++ as.lastOption).toList.distinct.take(c.authorsPerSource))
            .map(a => known(CandidateWatch(g.slug, Candidates.WatchKind.Author.wire, a, "openalex")))
        }
        .distinctBy(w => w.subject)
      // an index that may not be asked now (CORE's budget spent, CORE asked to wait) is left out of the rest
      // of this sweep: its watches keep their watermarks and are read next time
      val leftOut = scala.collection.mutable.LinkedHashMap.empty[String, (String, Int)] // this sweep's tally
      def query(i: ScholarlyIndex, w: CandidateWatch, since: LocalDate): Either[String, List[WorkRef]] =
        leftOut.get(i.name).orElse(i.unavailable.map(_ -> 0)) match
          case Some((why, n)) =>
            leftOut.update(i.name, (why, n + 1))
            Left(s"not asked this sweep — $why")
          case None =>
            val got = i.search(w.subject, since, c.perWatch)
            got.left.foreach(_ => i.unavailable.foreach(why => leftOut.update(i.name, (why, 0))))
            got
      val read = (base ++ authorWatches).map { w =>
        val since = Candidates.since(w.watermark, now, c.lookbackDays)
        val got: Either[String, List[WorkRef]] = Candidates.WatchKind.parse(w.kind) match
          case Some(Candidates.WatchKind.Query) =>
            byIndex
              .get(w.index)
              .toRight(s"index '${w.index}' is not configured")
              .flatMap(query(_, w, since))
          case Some(Candidates.WatchKind.Citing) => alerts.citing(w.subject, since, c.perWatch)
          case Some(Candidates.WatchKind.Author) => alerts.byAuthor(w.subject, since, c.perWatch)
          case None                              => Left(s"unknown watch kind '${w.kind}'")
        (w, since, got)
      }
      val found = read.flatMap { (w, _, got) =>
        val by = w.kind match
          case "query"  => s"${w.index} query \"${w.subject}\""
          case "citing" => s"${w.index} citing ${w.subject}"
          case _        => s"${w.index} author ${w.subject}"
        got.toOption.toList.flatten.map(by -> _)
      }
      // known already: the corpus, the acquisition list (open or done)
      val corpus = AcquireRepo.doiIndex(ds).keySet
      val queued = QueueRepo.list(ds, Queue.Kind.Acquire, all = true).flatMap(i => Scheduling.dois(i.text)).toSet
      val (fresh, skipped) = Candidates.dedupe(found, corpus ++ queued)
      val earlier = CandidateRepo.dois(ds, g.slug)
      val again = fresh.count((k, _, _) => earlier.contains(k))
      val added =
        if dryRun then fresh.count((k, _, _) => !earlier.contains(k))
        else
          fresh.count { (k, w, by) =>
            CandidateRepo.upsert(
              ds,
              SourceCandidate(
                0,
                g.slug,
                k,
                w.title.getOrElse(k),
                w.year,
                w.authors.take(8),
                w.venue,
                w.abstractText.map(_.take(2000)),
                w.isOa,
                w.arxivId,
                by
              )
            )
          }
      // a watch read moves its watermark to today; a failed one keeps its own (nothing is skipped)
      val watches = read.map { (w, _, got) =>
        val mark = got.toOption.map(_ => now.toString)
        if !dryRun then
          CandidateRepo.recordWatch(ds, w, mark, got.left.toOption.map(_.take(500)), got.map(_.size).getOrElse(0))
        w.copy(
          watermark = if dryRun then w.watermark else mark.orElse(w.watermark),
          lastError = got.left.toOption,
          lastFound = got.map(_.size).getOrElse(0)
        )
      }
      val unseen = CandidateRepo.counts(ds, Some(g.slug)).find(_.state == Candidates.State.New.wire).fold(0)(_.count)
      val notes = leftOut.toList.map { case (index, (why, n)) =>
        s"$index not asked this sweep: $why — ${Plural.count(n, "watch", "watches")} left unread, which keep their " +
          "watermarks and are read next sweep"
      }
      CandidateSweep(g.slug, watches, found.size, added, again, skipped, unseen, dryRun, notes)
    }
