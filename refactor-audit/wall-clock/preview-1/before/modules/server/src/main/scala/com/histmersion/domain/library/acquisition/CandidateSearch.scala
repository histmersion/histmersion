package com.histmersion.domain.library.acquisition

import com.histmersion.domain.automation.goals.GoalService
import com.histmersion.domain.automation.worklists.{QueueScope, QueueService}
import com.histmersion.domain.library.sources.SourceService

import com.histmersion.api.{Plural, Write}
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.automation.Goal.Plans.LinkKind

import hearth.kindlings.optics.syntax.*

import java.time.{LocalDate, ZoneOffset}
import com.histmersion.api.automation.{Goal, Queue}
import com.histmersion.api.library.{Acquire, Candidates, CandidateSweep, CandidateWatch, WorkRef}
import com.histmersion.api.Api
import com.histmersion.api.library.Source

/** The deterministic new-source search of one goal (#3761, docs/research-goal-advancement.md): its WATCHES — every
  * saved query (its linked `query` links, else its title) on every index of `acquire.candidates.indexes`, and for up to
  * `sources` of its sources (linked, and cited by its linked articles) the works citing it and the newer works of its
  * first and last authors (OpenAlex) — each read from its watermark; the works found deduplicated by DOI across watches
  * and against the corpus, the acquisition list and the goal's earlier candidates; the new ones stored as `new`. No
  * model runs here: the sweep only calls the indexes, so a schedule can run it as often as it likes for the price of
  * the requests, and starts a model only when it found something new.
  */
final class CandidateSearch(
    candidates: CandidateService,
    sources: SourceService,
    goals: GoalService,
    articles: ArticleCitations,
    queues: QueueService,
    cfg: => CandidatesConfig,
    indexes: => List[ScholarlyIndex],
    alerts: => CitationAlerts,
    today: () => LocalDate = () => LocalDate.now(ZoneOffset.UTC)
):

  /** The goal's watches as of now: what is read, from where (the watermark is the stored one). */
  private def plan(goal: Goal): List[CandidateWatch] =
    val c = cfg
    val links = goals.links(goal.slug)
    def refs(kind: LinkKind) = links.filter(_.kind == kind).map(_.ref)
    val queries = Some(refs(LinkKind.Query)).filter(_.nonEmpty).getOrElse(List(goal.title))
    val q = for query <- queries; i <- indexes
    yield CandidateWatch(goal.slug, Candidates.WatchKind.Query.wire, query, i.name)
    val cited = (refs(LinkKind.Source) ++ refs(LinkKind.Article).flatMap(articles.cited)).distinct
      .take(math.max(0, c.sources.value))
    val withDoi = cited.flatMap(ck => sources.load(ck).flatMap(_.doi).flatMap(Ident.doi).map(ck -> _))
    val citing = withDoi.map((ck, doi) => CandidateWatch(goal.slug, Candidates.WatchKind.Citing.wire, doi, "openalex"))
    q ++ citing

  /** Sweep `goal` now; a dry run reads, but stores neither candidates nor watermarks. */
  def sweep(goal: String, dryRun: Boolean): Either[Api.Error, CandidateSweep] =
    goals.get(goal).toRight(Api.Error(s"no goal '$goal'", Some(Write.CodeNotFound))).map { g =>
      val c = cfg
      val now = today()
      val stored = candidates.watches(g.slug).map(w => (w.kind, w.subject, w.index) -> w).toMap
      def known(w: CandidateWatch) =
        stored.get((w.kind, w.subject, w.index)).fold(w)(s => w.modify(_.watermark).setTo(s.watermark))
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
            .flatMap(as => (as.headOption ++ as.lastOption).toList.distinct.take(c.authorsPerSource.value))
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
            val got = i.search(w.subject, since, c.perWatch.value)
            got.left.foreach(_ => i.unavailable.foreach(why => leftOut.update(i.name, (why, 0))))
            got
      val read = (base ++ authorWatches).map { w =>
        val since = Candidates.since(w.watermark, now, c.lookbackDays.value)
        val got: Either[String, List[WorkRef]] = Candidates.WatchKind.parse(w.kind) match
          case Some(Candidates.WatchKind.Query) =>
            byIndex
              .get(w.index)
              .toRight(s"index '${w.index}' is not configured")
              .flatMap(query(_, w, since))
          case Some(Candidates.WatchKind.Citing) => alerts.citing(w.subject, since, c.perWatch.value)
          case Some(Candidates.WatchKind.Author) => alerts.byAuthor(w.subject, since, c.perWatch.value)
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
      val corpus = sources.doiIndex.keySet
      val queued = queues.list(Queue.Kind.Acquire, QueueScope.All).flatMap(i => Scheduling.dois(i.text)).toSet
      val (fresh, skipped) = CandidateDedupe.dedupe(found, corpus ++ queued)
      val earlier = candidates.dois(g.slug)
      val again = fresh.count((k, _, _) => earlier.contains(k))
      val added =
        if dryRun then fresh.count((k, _, _) => !earlier.contains(k))
        else
          fresh.count { (k, w, by) =>
            candidates.upsert(
              Source.Candidate(
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
          candidates.recordWatch(w, mark, got.left.toOption.map(_.take(500)), got.map(_.size).getOrElse(0))
        w.modify(_.watermark)
          .setTo(if dryRun then w.watermark else mark.orElse(w.watermark))
          .modify(_.lastError)
          .setTo(got.left.toOption)
          .modify(_.lastFound)
          .setTo(got.map(_.size).getOrElse(0))
      }
      val unseen = candidates.counts(Some(g.slug)).find(_.state == Candidates.State.New.wire).fold(0)(_.count)
      val notes = leftOut.toList.map { case (index, (why, n)) =>
        s"$index not asked this sweep: $why — ${Plural.count(n, "watch", "watches")} left unread, which keep their " +
          "watermarks and are read next sweep"
      }
      CandidateSweep(g.slug, watches, found.size, added, again, skipped, unseen, dryRun, notes)
    }
